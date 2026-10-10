package com.stoxsim.app.data

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

    private fun session(token: String, access: String = "access") = MockResponse()
        .setBody(sessionBody(access))
        .addHeader("Set-Cookie", "stoxsim_refresh=$token; Path=/api/v1/auth; Secure; HttpOnly; SameSite=Strict; Max-Age=3600")
    private fun sessionBody(access: String) = """{"accessToken":"$access","tokenType":"Bearer","expiresInSeconds":900,"user":$user}"""
    private val user = """{"displayName":"Learner","email":"learner@example.com","emailVerified":true,"accounts":[]}"""
    private class MemoryStore : RefreshTokenStore {
        var value: String? = null
        override fun read() = value
        override fun write(token: String) { value = token }
        override fun clear() { value = null }
    }
}
