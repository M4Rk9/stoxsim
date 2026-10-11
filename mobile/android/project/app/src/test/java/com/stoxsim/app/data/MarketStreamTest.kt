package com.stoxsim.app.data

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MarketStreamTest {
    @Test fun decoderHandlesHeartbeatsMultipleFramesAndFragmentedUtf8Body() {
        val decoder = StompDecoder()
        val body = """{"name":"₹ café","lastPrice":125.5}"""
        val bytes = ("\nCONNECTED\r\nversion:1.2\r\n\r\n\u0000MESSAGE\ndestination:/topic/market/quotes\nsubscription:quotes\ncontent-length:${body.toByteArray().size}\n\n$body\u0000\n").toByteArray()
        val first = decoder.decode(bytes.copyOfRange(0, 50))
        val remaining = decoder.decode(bytes.copyOfRange(50, bytes.size))
        assertEquals("CONNECTED", first.single().command)
        assertEquals(body, remaining.single().body)
    }
    @Test fun decoderPreservesFirstHeaderAndUnescapesColon() {
        val frame = StompDecoder().decode("MESSAGE\nx:a\\cb\nx:ignored\n\n{}\u0000".toByteArray()).single()
        assertEquals("a:b", frame.headers["x"])
    }
    @Test fun decoderRejectsOversizedOrInvalidHeaderEscapes() {
        try { StompDecoder().decode(ByteArray(65_537)); fail("Expected size bound") } catch (_: IllegalArgumentException) { }
        try { StompDecoder().decode("MESSAGE\nx:a\\t\n\n{}\u0000".toByteArray()); fail("Expected invalid escape") } catch (_: IllegalStateException) { }
    }
    @Test fun authenticatedStreamSubscribesOnceAndDeliversQuote() = runBlocking {
        val server = MockWebServer()
        val connect = AtomicReference<String>()
        val subscribe = AtomicReference<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.startsWith("CONNECT\n")) { connect.set(text); webSocket.send("CONNECTED\nversion:1.2\n\n\u0000") }
                else if (text.startsWith("SUBSCRIBE\n")) {
                    subscribe.set(text)
                    val body = """{"instrumentKey":"key","marketRegion":"INDIA","lastPrice":123.45}"""
                    webSocket.send("MESSAGE\ndestination:/topic/market/quotes\nsubscription:quotes\ncontent-length:${body.toByteArray().size}\n\n$body\u0000")
                }
            }
        }))
        server.start()
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
        try {
            val events = withTimeout(5000) { marketStream(client, server.url("/"), "access-in-memory").take(2).toList() }
            assertTrue(events.first().getBoolean("streamConnected"))
            assertEquals("123.45", events.last().get("lastPrice").toString())
            assertTrue(connect.get().contains("Authorization:Bearer access-in-memory\n"))
            assertTrue(subscribe.get().contains("destination:/topic/market/quotes\n"))
            val request = server.takeRequest()
            assertEquals("/ws/market", request.path); assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        } finally { server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
