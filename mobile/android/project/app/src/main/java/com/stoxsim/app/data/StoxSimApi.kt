package com.stoxsim.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId

internal class ApiFailure(val status: Int, detail: String? = null) : IOException(detail ?: when (status) {
    400 -> "Check the information you entered and try again."
    401 -> "Your session has expired. Please sign in again."
    403 -> "This action is currently unavailable for your account."
    404 -> "This item could not be found."
    409 -> "This account or action already exists."
    422 -> "The order could not be placed. Check its quantity, price and available balance."
    429 -> "Too many requests. Wait a minute before trying again."
    else -> "StoxSim is unavailable right now. Please try again shortly."
})
internal class SessionExpired : IOException("Your session has expired. Please sign in again.")

/** Serialized requests prevent refresh rotation, restore and logout from racing each other. */
internal class StoxSimApi(
    private val store: RefreshTokenStore,
    private val baseUrl: HttpUrl = "https://api.stoxsim.com/".toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
) {
    private val lock = Mutex()
    private data class ActivitySession(val userId: String, val token: String, val expiresAt: Long)
    @Volatile private var activitySession: ActivitySession? = null
    private var accessToken: String? = null
    private var accessExpiresAt: Long = 0
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun restore(): User? = serialized {
        if (store.read() == null) return@serialized null
        refresh()
        parseUser(JSONObject(authorizedGet("api/v1/auth/me")))
    }

    suspend fun authenticate(email: String, password: String, name: String? = null): User = serialized {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        if (name != null) body.put("displayName", name.trim()).put("termsAccepted", true)
        request("api/v1/auth/" + if (name == null) "login" else "register", body).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) throw IOException("Email or password is incorrect.")
                throw failure(response)
            }
            acceptSession(response)
        }
    }

    suspend fun forgotPassword(email: String) = serialized {
        request("api/v1/auth/password/forgot", JSONObject().put("email", email.trim())).use {
            if (!it.isSuccessful) throw failure(it)
        }
    }

    /** Optional analytics never rotates tokens, clears a session or queues behind user operations. */
    suspend fun recordActivity(userId: String, event: ProductActivity): Boolean = withContext(Dispatchers.IO) {
        // An immutable snapshot avoids competing with session refresh, streaming or user writes.
        // In-flight logout/revocation is still checked by the server; identity cannot change here.
        val session = activitySession?.takeIf {
            userId.isNotBlank() && userId == it.userId && System.currentTimeMillis() < it.expiresAt
        } ?: return@withContext false
        try {
            val url = baseUrl.newBuilder().addPathSegments("api/v1/analytics/events").build()
            val body = JSONObject().put("version", 1).put("event", event.name)
            val request = Request.Builder().url(url).header("Authorization", "Bearer ${session.token}")
                .post(body.toString().toRequestBody(jsonType)).build()
            val call = client.newCall(request)
            call.timeout().timeout(5, TimeUnit.SECONDS)
            call.execute().use { it.isSuccessful }
        } catch (_: IOException) { false }
    }

    /** Email action tokens use public endpoints, without any account's bearer/cookie or write replay. */
    suspend fun completeEmailLink(link: EmailActionLink, password: String? = null) = serialized {
        val body = JSONObject().put("token", link.token)
        if (link.action == EmailAction.RESET_PASSWORD) body.put("newPassword", requireNotNull(password))
        val path = if (link.action == EmailAction.VERIFY_EMAIL) "email-verification/confirm" else "password/reset"
        request("api/v1/auth/$path", body).use { if (!it.isSuccessful) throw failure(it) }
        if (link.action == EmailAction.RESET_PASSWORD) clearSession()
    }

    suspend fun currentUser(): User = serialized { parseUser(JSONObject(authorizedGet("api/v1/auth/me"))) }

    suspend fun portfolio(region: String): Portfolio = serialized {
        parsePortfolio(JSONObject(authorizedGet("api/v1/portfolio", mapOf("marketRegion" to region))))
    }

    suspend fun search(region: String, query: String): List<Instrument> = serialized {
        if (accessToken == null) refresh()
        var response = searchPage(region, query)
        if (response.first == 401) {
            currentCoroutineContext().ensureActive()
            // Finish refresh rotation atomically under the session lock, then
            // check cancellation before issuing another safe GET.
            refresh()
            currentCoroutineContext().ensureActive()
            response = searchPage(region, query)
            if (response.first == 401) { clearSession(); throw SessionExpired() }
        }
        JSONArray(response.second)
            .mapObjects { parseInstrument(it, region) }.filter { it.isSearchEligible(region) }.take(8)
    }

    suspend fun quotes(): kotlinx.coroutines.flow.Flow<JSONObject> {
        val token = serialized {
            if (accessToken == null || System.currentTimeMillis() >= accessExpiresAt - 30_000) refresh()
            accessToken!!
        }
        return marketStream(client, baseUrl, token)
    }
    suspend fun accounts(): List<Account> = serialized {
        JSONArray(authorizedGet("api/v1/accounts")).mapObjects(::parseAccount)
    }
    suspend fun accountPortfolio(id: String): Portfolio = serialized {
        parsePortfolio(JSONObject(authorizedGet("api/v1/accounts/$id/portfolio")))
    }
    suspend fun objectData(path: String, params: Map<String, String> = emptyMap()): JSONObject = serialized {
        authorizedGet("api/v1/$path", params).let { JSONObject(if (it == "null") "{}" else it) }
    }
    suspend fun arrayData(path: String, params: Map<String, String> = emptyMap()): List<JSONObject> = serialized {
        JSONArray(authorizedGet("api/v1/$path", params)).mapObjects { it }
    }
    suspend fun quote(instrument: Instrument): Quote = serialized {
        parseQuote(JSONObject(authorizedGet(stockPath(instrument, "quote"))))
    }
    suspend fun candles(instrument: Instrument, months: Long): List<Candle> = serialized {
        val to = LocalDate.now(ZoneId.of(if (instrument.region == "INDIA") "Asia/Kolkata" else "America/New_York"))
        JSONObject(authorizedGet(stockPath(instrument, "candles"), mapOf("interval" to "ONE_DAY",
            "from" to to.minusMonths(months).toString(), "to" to to.toString())))
            .getJSONArray("candles").mapObjects { Candle(it.getString("timestamp"), it.money("close")) }
    }
    suspend fun insights(instrument: Instrument, period: String): JSONObject = serialized {
        JSONObject(authorizedGet(stockPath(instrument, "insights"), mapOf("timePeriod" to period)))
    }
    suspend fun addToWatchlist(instrument: Instrument): JSONObject =
        mutate("watchlists/default/items", body = instrument.watchlistPayload())
    suspend fun mutate(path: String, method: String = "POST", body: JSONObject = JSONObject(),
        idempotencyKey: String? = null): JSONObject = serialized {
        // Rotate before writes when the advertised token lifetime is almost over. Never replay a write.
        if (accessToken == null || System.currentTimeMillis() >= accessExpiresAt - 30_000) refresh()
        request("api/v1/$path", body, accessToken, method = method, idempotencyKey = idempotencyKey).use {
            if (it.code == 401) {
                // Reconnect for the user's next explicit attempt without resubmitting this action.
                refresh()
                throw IOException("Session reconnected. Review the action and try again.")
            }
            if (!it.isSuccessful) throw failure(it)
            val data = it.body?.string().orEmpty()
            if (data.isBlank()) JSONObject() else JSONObject(data)
        }
    }
    suspend fun endLocalSession() = serialized { clearSession() }
    private fun stockPath(instrument: Instrument, resource: String): String {
        // Encode one path segment; a symbol must never become extra path/query components.
        val segment = baseUrl.newBuilder().addPathSegment(instrument.symbol).build().encodedPath.removePrefix("/")
        return "api/v1/instruments/${instrument.region}/${instrument.exchange}/$segment/$resource"
    }

    /** Always remove local secrets; a failed remote revocation is reported to the UI. */
    suspend fun logout(): Boolean = serialized {
        val refresh = store.read()
        try {
            if (refresh == null) true else request("api/v1/auth/logout", JSONObject().put("refreshToken", refresh)).use {
                it.isSuccessful
            }
        } catch (_: IOException) {
            false
        } finally {
            clearSession()
        }
    }

    private suspend fun <T> serialized(action: suspend () -> T): T = withContext(Dispatchers.IO) { lock.withLock { action() } }

    private fun request(path: String, body: JSONObject? = null, bearer: String? = null, params: Map<String, String> = emptyMap(),
        method: String? = null, idempotencyKey: String? = null): Response =
        client.newCall(buildRequest(path, body, bearer, params, method, idempotencyKey)).execute()

    /** Cancelling optional search closes its socket and releases the session mutex promptly. */
    private suspend fun searchPage(region: String, query: String): Pair<Int, String> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(buildRequest("api/v1/instruments/search", bearer = accessToken,
            params = mapOf("marketRegion" to region, "q" to query.trim())))
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use { it.code to if (it.code == 401) "" else readSuccessful(it) }
                    continuation.resume(result)
                } catch (error: Exception) { continuation.resumeWithException(error) }
            }
        })
    }

    private fun buildRequest(path: String, body: JSONObject? = null, bearer: String? = null, params: Map<String, String> = emptyMap(),
        method: String? = null, idempotencyKey: String? = null): Request {
        val url = baseUrl.newBuilder().addEncodedPathSegments(path).apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "StoxSim-Android/0.5.4")
        bearer?.let { request.header("Authorization", "Bearer $it") }
        if (path == "api/v1/auth/sessions") store.read()?.let { request.header("Cookie", "stoxsim_refresh=$it") }
        idempotencyKey?.let { request.header("Idempotency-Key", it) }
        if (method != null) request.method(method, if (method == "DELETE") body?.toString()?.toRequestBody(jsonType)
            else (body ?: JSONObject()).toString().toRequestBody(jsonType))
        else body?.let { request.post(it.toString().toRequestBody(jsonType)) }
        return request.build()
    }

    private fun authorizedGet(path: String, params: Map<String, String> = emptyMap()): String {
        if (accessToken == null) refresh()
        request(path, bearer = accessToken, params = params).use {
            if (it.code != 401) return readSuccessful(it)
        }
        // Only safe GET requests are retried, once, after rotation. No purchase/order retry is implied.
        refresh()
        request(path, bearer = accessToken, params = params).use {
            if (it.code == 401) { clearSession(); throw SessionExpired() }
            return readSuccessful(it)
        }
    }

    private fun refresh() {
        val token = store.read() ?: throw SessionExpired()
        request("api/v1/auth/refresh", JSONObject().put("refreshToken", token)).use {
            if (it.code == 401 || it.code == 403) { clearSession(); throw SessionExpired() }
            if (!it.isSuccessful) throw failure(it)
            acceptSession(it)
        }
    }

    private fun acceptSession(response: Response): User {
        val json = JSONObject(response.body?.string() ?: throw IOException("The server returned an empty response."))
        val user = parseUser(json.getJSONObject("user"))
        val access = json.getString("accessToken").also { require(it.isNotBlank()) }
        // The Spring DTO deliberately omits refreshToken from JSON. Retain only this secure, host-only cookie.
        val cookie = Cookie.parseAll(response.request.url, response.headers).singleOrNull {
            it.name == "stoxsim_refresh" && it.hostOnly && it.domain == baseUrl.host && it.httpOnly && it.secure &&
                it.path == "/api/v1/auth" && it.expiresAt > System.currentTimeMillis() && it.value.isNotBlank()
        } ?: run { clearSession(); throw SessionExpired() }
        store.write(cookie.value)
        accessToken = access
        accessExpiresAt = System.currentTimeMillis() + json.optLong("expiresInSeconds", 900).coerceIn(1, 86400) * 1000
        activitySession = ActivitySession(user.id, access, accessExpiresAt)
        return user
    }

    private fun failure(response: Response): ApiFailure {
        val detail = if (response.code in setOf(400, 403, 404, 409, 422)) try {
            JSONObject(response.body?.string().orEmpty()).text("message").takeIf { it.isNotBlank() }?.take(500)
        } catch (_: Exception) { null } else null
        return ApiFailure(response.code, detail)
    }

    private fun readSuccessful(response: Response): String {
        if (!response.isSuccessful) throw failure(response)
        return response.body?.string() ?: throw IOException("The server returned an empty response.")
    }

    private fun clearSession() { activitySession = null; accessToken = null; accessExpiresAt = 0; store.clear() }
}
