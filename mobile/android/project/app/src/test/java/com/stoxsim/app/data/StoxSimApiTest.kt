package com.stoxsim.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.SocketPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StoxSimApiTest {
    private lateinit var server: MockWebServer
    private lateinit var store: MemoryStore
    private lateinit var api: StoxSimApi

    @Before fun setup() {
        server = MockWebServer().apply { start() }
        store = MemoryStore()
        api = StoxSimApi(store, server.url("/"))
    }
    @After fun teardown() { server.shutdown() }

    @Test fun loginReadsRefreshFromSecureCookieNotJson() = runBlocking {
        server.enqueue(session("first"))
        assertEquals("Learner", api.authenticate(" learner@example.com ", "password1").displayName)
        assertEquals("first", store.value)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/login", request.path)
        assertEquals("learner@example.com", JSONObject(request.body.readUtf8()).getString("email"))
        assertNull(request.getHeader("Cookie"))
    }

    @Test fun googleSignupUsesNativeChallengeAndPreservesSecureSessionStorage() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"clientId":"web.apps.googleusercontent.com"}"""))
        server.enqueue(MockResponse().setBody("""{"binding":"challenge-binding","nonce":"server-nonce"}"""))
        val challenge = api.googleChallenge()
        assertEquals("server-nonce", challenge.nonce)
        assertEquals("/api/v1/auth/google/native/config", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/google/native/challenge", request.path)
        assertNull(request.getHeader("Origin"))
        server.enqueue(session("google-refresh"))
        api.googleAuthenticate("google-id-token", challenge.binding, " Learner ", true)
        val signup = server.takeRequest()
        assertEquals("/api/v1/auth/google/native/register", signup.path)
        val json = JSONObject(signup.body.readUtf8())
        assertTrue(json.getBoolean("termsAccepted"))
        assertEquals("Learner", json.getString("displayName"))
        assertEquals("challenge-binding", json.getString("binding"))
        assertFalse(json.has("password"))
        assertEquals("google-refresh", store.value)
    }

    @Test fun rejectedGoogleLoginDoesNotCreateASession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.googleAuthenticate("invalid", "binding", null, false); fail("must reject") } catch (_: ApiFailure) { }
        assertNull(store.value)
        assertEquals("/api/v1/auth/google/native/login", server.takeRequest().path)
    }

    @Test fun registrationSendsLegalConsent() = runBlocking {
        server.enqueue(session("registered"))
        api.authenticate("learner@example.com", "password1", "Learner")
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/register", request.path)
        assertTrue(JSONObject(request.body.readUtf8()).getBoolean("termsAccepted"))
    }

    @Test fun restoreRotatesStoredTokenAndUsesMemoryBearerOnly() = runBlocking {
        store.value = "old"
        server.enqueue(session("rotated", "new-access"))
        server.enqueue(MockResponse().setBody(user))
        assertEquals("Learner", api.restore()?.displayName)
        assertEquals("rotated", store.value)
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertEquals("old", JSONObject(refresh.body.readUtf8()).getString("refreshToken"))
        assertNull(refresh.getHeader("Authorization"))
        val me = server.takeRequest()
        assertEquals("Bearer new-access", me.getHeader("Authorization"))
        assertNull(me.getHeader("Cookie"))
    }

    @Test fun expiredAccessRefreshesOnceWithoutConcurrentRotation() = runBlocking {
        server.enqueue(session("initial", "access-one"))
        api.authenticate("learner@example.com", "password1")
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(session("rotated", "access-two"))
        server.enqueue(MockResponse().setBody(user))
        server.enqueue(MockResponse().setBody(user))
        listOf(async { api.currentUser() }, async { api.currentUser() }).awaitAll()
        assertEquals("Bearer access-one", server.takeRequest().getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        repeat(2) { assertEquals("Bearer access-two", server.takeRequest().getHeader("Authorization")) }
        assertEquals(5, server.requestCount)
    }

    @Test fun invalidRefreshClearsLocalSession() = runBlocking {
        store.value = "revoked"
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.restore(); fail("Expected expired session") } catch (_: SessionExpired) { }
        assertNull(store.value)
    }

    @Test fun temporaryServerFailureRetainsRefreshForRetry() = runBlocking {
        store.value = "still-valid"
        server.enqueue(MockResponse().setResponseCode(503))
        try { api.restore(); fail("Expected failure") } catch (failure: ApiFailure) { assertEquals(503, failure.status) }
        assertEquals("still-valid", store.value)
    }

    @Test fun logoutClearsLocalSecretWhenServerFails() = runBlocking {
        store.value = "logout-token"
        server.enqueue(MockResponse().setResponseCode(503))
        assertFalse(api.logout())
        assertNull(store.value)
        assertEquals("logout-token", JSONObject(server.takeRequest().body.readUtf8()).getString("refreshToken"))
        assertNull(api.restore())
        assertEquals(1, server.requestCount)
    }

    @Test fun insecureRefreshCookieIsRejected() = runBlocking {
        server.enqueue(MockResponse().setBody(sessionBody("access"))
            .addHeader("Set-Cookie", "stoxsim_refresh=unsafe; Path=/api/v1/auth; HttpOnly; Max-Age=3600"))
        try { api.authenticate("learner@example.com", "password1"); fail("Expected unsafe cookie rejection") }
        catch (_: java.io.IOException) { }
        assertNull(store.value)
    }

    @Test fun loginRedirectDoesNotForwardCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", server.url("/other")))
        try { api.authenticate("learner@example.com", "password1"); fail("Expected redirect rejection") }
        catch (failure: ApiFailure) { assertEquals(307, failure.status) }
        assertEquals(1, server.requestCount)
    }

    @Test fun failedRetriedGetEndsSessionInsteadOfLooping() = runBlocking {
        server.enqueue(session("initial"))
        api.authenticate("learner@example.com", "password1")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(session("rotated"))
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.currentUser(); fail("Expected expired session") } catch (_: SessionExpired) { }
        assertEquals(4, server.requestCount)
        assertNull(store.value)
    }

    @Test fun searchEscapesQueryAndSendsSelectedRegion() = runBlocking {
        server.enqueue(session("initial"))
        api.authenticate("learner@example.com", "password1")
        server.takeRequest()
        server.enqueue(MockResponse().setBody("[]"))
        assertTrue(api.search("UNITED_STATES", "A&B").isEmpty())
        val url = server.takeRequest().requestUrl!!
        assertEquals("A&B", url.queryParameter("q"))
        assertEquals("UNITED_STATES", url.queryParameter("marketRegion"))
        assertEquals(2, url.querySize)
    }

    @Test fun orderUsesSelectedAccountAndStableKeyWithoutAutomaticReplay() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"id":"order","status":"OPEN"}"""))
        val draft = OrderDraft("sandbox-id", Instrument("ABC", "ABC Company", "NSE", "INDIA"), "BUY", "LIMIT", 3, java.math.BigDecimal("120.50"))
        api.mutate("accounts/sandbox-id/orders", body = draft.payload(), idempotencyKey = "retained-request-key")
        val request = server.takeRequest()
        assertEquals("/api/v1/accounts/sandbox-id/orders", request.path)
        assertEquals("POST", request.method)
        assertEquals("retained-request-key", request.getHeader("Idempotency-Key"))
        val payload = JSONObject(request.body.readUtf8())
        assertEquals(3L, payload.getLong("quantity")); assertEquals("LIMIT", payload.getString("orderType"))
        assertEquals(0, java.math.BigDecimal("120.50").compareTo(payload.money("limitPrice")))
        assertEquals("Bearer access", request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
    }
    @Test fun write401ReconnectsButNeverResubmitsOrder() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(session("rotated"))
        try { api.mutate("accounts/account/orders", idempotencyKey = "key"); fail("Expected explicit retry") }
        catch (failure: java.io.IOException) { assertEquals("Session reconnected. Review the action and try again.", failure.message) }
        assertEquals("/api/v1/accounts/account/orders", server.takeRequest().path)
        assertEquals("/api/v1/auth/refresh", server.takeRequest().path)
        assertEquals(3, server.requestCount); assertEquals("rotated", store.value)
    }
    @Test fun writeTimeoutDoesNotRetryOrLoseSession() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { api.mutate("accounts/account/orders", idempotencyKey = "key"); fail("Expected network failure") }
        catch (_: java.io.IOException) { }
        assertEquals(2, server.requestCount); assertEquals("initial", store.value)
    }
    @Test fun sessionInventoryAloneSendsRefreshCookieToIdentifyCurrentDevice() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("[]")); api.arrayData("auth/sessions")
        assertEquals("stoxsim_refresh=initial", server.takeRequest().getHeader("Cookie"))
        server.enqueue(MockResponse().setBody("[]")); api.arrayData("accounts/account/orders")
        assertNull(server.takeRequest().getHeader("Cookie"))
    }
    @Test fun quoteEncodesSymbolAsSinglePathSegment() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("""{"lastPrice":null,"previousClose":null,"dataStatus":"UNAVAILABLE","exchangeTimestamp":null}"""))
        val quote = api.quote(Instrument("A/B?x", "Test", "NASDAQ", "UNITED_STATES"))
        assertNull(quote.price)
        val url = server.takeRequest().requestUrl!!
        assertEquals("A/B?x", url.pathSegments[5]); assertEquals(0, url.querySize)
    }
    @Test fun tradingFailureDisplaysServerValidationAndDoesNotRetry() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"message":"Insufficient available cash"}"""))
        try { api.mutate("accounts/account/orders"); fail("Expected validation failure") }
        catch (failure: ApiFailure) { assertEquals(422, failure.status); assertEquals("Insufficient available cash", failure.message) }
        assertEquals(2, server.requestCount)
    }
    @Test fun emptyMembershipResponseIsAnEmptyObject() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("null"))
        assertEquals(0, api.objectData("campus/membership-request").length())
    }

    @Test fun searchedNseStockUsesTheWatchlistServerContract() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("""[{"tradingSymbol":"RELIANCE","name":"Example company","exchange":"NSE","marketRegion":"INDIA","currency":"INR","instrumentType":"EQUITY"}]"""))
        val stock = api.search("INDIA", "reliance").single(); server.takeRequest()
        assertEquals("", stock.id)
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"name":"My Watchlist","items":[{"symbol":"RELIANCE","exchange":"NSE","marketRegion":"INDIA"}]}"""))
        assertEquals("RELIANCE", api.addToWatchlist(stock).objects("items").single().getString("symbol"))
        val request = server.takeRequest(); val body = JSONObject(request.body.readUtf8())
        assertEquals("POST", request.method); assertEquals("/api/v1/watchlists/default/items", request.path)
        assertEquals(setOf("marketRegion", "exchange", "symbol"), body.keys().asSequence().toSet())
        assertEquals("INDIA", body.getString("marketRegion")); assertEquals("NSE", body.getString("exchange")); assertEquals("RELIANCE", body.getString("symbol"))
        assertEquals("Bearer access", request.getHeader("Authorization"))
    }
    @Test fun usEtfWatchlistPreservesItsActualExchange() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"items":[]}"""))
        api.addToWatchlist(Instrument("SPY", "Example ETF", "NYSE_ARCA", "UNITED_STATES", currency = "USD"))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("UNITED_STATES", body.getString("marketRegion")); assertEquals("NYSE_ARCA", body.getString("exchange")); assertEquals("SPY", body.getString("symbol"))
    }
    @Test fun financialPeriodRequestsKeepQuarterlyAndYearlySeparate() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        val stock = Instrument("RELIANCE", "Example", "BSE")
        for (period in listOf("quarterly", "yearly")) {
            server.enqueue(MockResponse().setBody("""{"financials":{"timePeriod":"$period","metrics":[]}}"""))
            assertEquals(period, api.insights(stock, period).getJSONObject("financials").getString("timePeriod"))
            val url = server.takeRequest().requestUrl!!
            assertEquals("/api/v1/instruments/INDIA/BSE/RELIANCE/insights", url.encodedPath)
            assertEquals(period, url.queryParameter("timePeriod"))
        }
    }

    @Test fun analyticsUsesMinimalPayloadAndNeverRefreshesOrSignsOutOnFailure() = runBlocking {
        assertFalse(api.recordActivity("learner-id", ProductActivity.ACTIVE)); assertEquals(0, server.requestCount)
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        assertFalse(api.recordActivity("other-user", ProductActivity.ACTIVE)); assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(401))
        assertFalse(api.recordActivity("learner-id", ProductActivity.ACTIVE))
        val request = server.takeRequest()
        assertEquals("/api/v1/analytics/events", request.path)
        assertEquals("Bearer access", request.getHeader("Authorization")); assertNull(request.getHeader("Cookie"))
        val payload = JSONObject(request.body.readUtf8())
        assertEquals(setOf("version", "event"), payload.keys().asSequence().toSet())
        assertEquals("ACTIVE", payload.getString("event")); assertEquals("initial", store.value)
        server.enqueue(MockResponse().setResponseCode(204)); assertTrue(api.recordActivity("learner-id", ProductActivity.STOCK_OPENED))
        assertEquals("STOCK_OPENED", JSONObject(server.takeRequest().body.readUtf8()).getString("event"))
        assertEquals(3, server.requestCount)
    }
    @Test fun analyticsDoesNotLoseAVisitWhileAReadHoldsTheSessionLock() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody(user).setBodyDelay(500, java.util.concurrent.TimeUnit.MILLISECONDS))
        val reading = async(kotlinx.coroutines.Dispatchers.Default) { api.currentUser() }
        assertNotNull(server.takeRequest(2, java.util.concurrent.TimeUnit.SECONDS))
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(api.recordActivity("learner-id", ProductActivity.ACTIVE))
        assertEquals("/api/v1/analytics/events", server.takeRequest().path)
        reading.await()
        api.endLocalSession()
        assertFalse(api.recordActivity("learner-id", ProductActivity.ACTIVE))
        assertEquals(3, server.requestCount)
    }

    @Test fun emailActionsArePublicAndResetSuccessClearsOnlyAfterServerConfirmation() = runBlocking {
        val token = "t".repeat(43)
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))
        api.completeEmailLink(EmailActionLink(EmailAction.VERIFY_EMAIL, token))
        val verification = server.takeRequest()
        assertEquals("/api/v1/auth/email-verification/confirm", verification.path)
        assertNull(verification.getHeader("Authorization")); assertNull(verification.getHeader("Cookie"))
        assertEquals("initial", store.value)
        val link = EmailActionLink(EmailAction.RESET_PASSWORD, token)
        server.enqueue(MockResponse().setResponseCode(401))
        try { api.completeEmailLink(link, "password2"); fail("Expected invalid link") } catch (_: ApiFailure) { }
        server.takeRequest(); assertEquals("initial", store.value)
        server.enqueue(MockResponse().setResponseCode(204)); api.completeEmailLink(link, "password2")
        val reset = server.takeRequest(); assertEquals("/api/v1/auth/password/reset", reset.path)
        assertNull(reset.getHeader("Authorization")); assertNull(reset.getHeader("Cookie"))
        assertEquals("password2", JSONObject(reset.body.readUtf8()).getString("newPassword"))
        assertNull(store.value); assertNull(api.restore()); assertEquals(4, server.requestCount)
    }

    private fun session(token: String, access: String = "access") = MockResponse()
        .setBody(sessionBody(access))
        .addHeader("Set-Cookie", "stoxsim_refresh=$token; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict; Max-Age=3600")
    private fun sessionBody(access: String) = """{"accessToken":"$access","tokenType":"Bearer","expiresInSeconds":900,"user":$user}"""
    private val user = """{"id":"learner-id","displayName":"Learner","email":"learner@example.com","emailVerified":true,"accounts":[]}"""
    private class MemoryStore : RefreshTokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
    @Test fun searchKeepsNseStocksAndEtfsButRejectsBseIndicesAndWrongMarket() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setBody("""[
            {"tradingSymbol":"BSE","name":"BSE LIMITED","exchange":"NSE","marketRegion":"INDIA","instrumentType":"EQUITY"},
            {"tradingSymbol":"NIFTYBEES","name":"Nifty ETF","exchange":"NSE","marketRegion":"INDIA","instrumentType":"ETF"},
            {"tradingSymbol":"BSEINDEX","name":"Index","exchange":"BSE","marketRegion":"INDIA","instrumentType":"INDEX"},
            {"tradingSymbol":"STOCK","name":"BSE stock","exchange":"BSE","marketRegion":"INDIA","instrumentType":"EQUITY"},
            {"tradingSymbol":"NIFTY","name":"Index","exchange":"NSE","marketRegion":"INDIA","instrumentType":"INDEX"},
            {"tradingSymbol":"SPY","name":"US ETF","exchange":"NYSE_ARCA","marketRegion":"UNITED_STATES","instrumentType":"ETF"}
        ]"""))
        assertEquals(listOf("BSE", "NIFTYBEES"), api.search("INDIA", "bse").map { it.symbol })
    }
    @Test fun cancelledSearchClosesSocketAndReleasesApiLockBeforeTheCallTimeout() = runBlocking {
        server.enqueue(session("initial")); api.authenticate("learner@example.com", "password1"); server.takeRequest()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val searching = launch { api.search("INDIA", "old") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        withTimeout(2_000) { searching.cancelAndJoin() }
        server.enqueue(MockResponse().setBody("[]"))
        // The replacement query must acquire the same mutex immediately, rather
        // than wait for the old search's 30 second blocking call timeout.
        withTimeout(2_000) { assertTrue(api.search("INDIA", "new").isEmpty()) }
        assertTrue(server.takeRequest(2, TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("q") == "new")
    }
}
