package com.stoxsim.app.data

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

internal class ApiFailure(val status: Int) : IOException(when (status) {
    400 -> "Check the information you entered and try again."
    401 -> "Your session has expired. Please sign in again."
    403 -> "This action is currently unavailable for your account."
    409 -> "This account or action already exists."
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
    private var accessToken: String? = null
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
                throw ApiFailure(response.code)
            }
            acceptSession(response)
        }
    }

    suspend fun forgotPassword(email: String) = serialized {
        request("api/v1/auth/password/forgot", JSONObject().put("email", email.trim())).use {
            if (!it.isSuccessful) throw ApiFailure(it.code)
        }
    }

    suspend fun currentUser(): User = serialized { parseUser(JSONObject(authorizedGet("api/v1/auth/me"))) }

    suspend fun portfolio(region: String): Portfolio = serialized {
        parsePortfolio(JSONObject(authorizedGet("api/v1/portfolio", mapOf("marketRegion" to region))))
    }

    suspend fun search(region: String, query: String): List<Instrument> = serialized {
        JSONArray(authorizedGet("api/v1/instruments/search", mapOf("marketRegion" to region, "q" to query.trim())))
            .mapObjects { Instrument(it.getString("tradingSymbol"), it.getString("name"), it.getString("exchange")) }
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

    private suspend fun <T> serialized(action: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { action() } }

    private fun request(path: String, body: JSONObject? = null, bearer: String? = null, params: Map<String, String> = emptyMap()): Response {
        val url = baseUrl.newBuilder().addPathSegments(path).apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("User-Agent", "StoxSim-Android/0.2.0")
        bearer?.let { request.header("Authorization", "Bearer $it") }
        body?.let { request.post(it.toString().toRequestBody(jsonType)) }
        return client.newCall(request.build()).execute()
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
            if (!it.isSuccessful) throw ApiFailure(it.code)
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
        return user
    }

    private fun readSuccessful(response: Response): String {
        if (!response.isSuccessful) throw ApiFailure(response.code)
        return response.body?.string() ?: throw IOException("The server returned an empty response.")
    }

    private fun clearSession() { accessToken = null; store.clear() }
}
