package com.stoxsim.app.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.io.IOException

/** Bounded STOMP 1.2 decoder for the server's read-only JSON quote topic. */
internal data class StompFrame(val command: String, val headers: Map<String, String>, val body: String)
internal class StompDecoder {
    private var buffer = byteArrayOf()
    fun decode(bytes: ByteArray): List<StompFrame> {
        require(buffer.size + bytes.size <= 65_536) { "Market frame too large" }
        buffer += bytes
        val frames = mutableListOf<StompFrame>()
        while (buffer.isNotEmpty()) {
            var skipped = 0
            while (skipped < buffer.size && (buffer[skipped] == 10.toByte() || buffer[skipped] == 13.toByte())) skipped++
            if (skipped > 0) buffer = buffer.copyOfRange(skipped, buffer.size)
            if (buffer.isEmpty()) break
            val lf = buffer.indexOf(10)
            if (lf < 0) break
            val command = buffer.copyOfRange(0, lf).toString(Charsets.UTF_8).removeSuffix("\r")
            var offset = lf + 1
            val headers = linkedMapOf<String, String>()
            var complete = false
            while (offset < buffer.size) {
                val end = (offset until buffer.size).firstOrNull { buffer[it] == 10.toByte() } ?: break
                val line = buffer.copyOfRange(offset, end).toString(Charsets.UTF_8).removeSuffix("\r")
                offset = end + 1
                if (line.isEmpty()) { complete = true; break }
                require(headers.size < 32 && line.length <= 4096)
                val colon = line.indexOf(':'); require(colon > 0)
                val rawKey = line.substring(0, colon); val rawValue = line.substring(colon + 1)
                fun header(value: String) = if (command in listOf("CONNECT", "CONNECTED")) value else unescape(value)
                headers.putIfAbsent(header(rawKey), header(rawValue))
            }
            if (!complete) break
            val count = headers["content-length"]?.let { it.toIntOrNull()?.also { n -> require(n in 0..65_536) } ?: error("Invalid body size") }
            val end = if (count != null) offset + count else (offset until buffer.size).firstOrNull { buffer[it] == 0.toByte() }
            if (end == null || end >= buffer.size) break
            require(buffer[end] == 0.toByte())
            frames += StompFrame(command, headers, buffer.copyOfRange(offset, end).toString(Charsets.UTF_8))
            buffer = buffer.copyOfRange(end + 1, buffer.size)
        }
        return frames
    }
    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index++]
            if (character != '\\') append(character) else {
                require(index < value.length)
                append(when (value[index++]) { 'r' -> '\r'; 'n' -> '\n'; 'c' -> ':'; '\\' -> '\\'; else -> error("Invalid STOMP escape") })
            }
        }
    }
}
internal fun marketStream(client: OkHttpClient, base: HttpUrl, token: String) = callbackFlow {
    require(token.none { it == '\n' || it == '\r' || it == '\u0000' })
    val decoder = StompDecoder()
    val request = Request.Builder().url(base.newBuilder().addPathSegments("ws/market").build())
        .header("Sec-WebSocket-Protocol", "v12.stomp").header("User-Agent", "StoxSim-Android/0.3.0").build()
    val socket = client.newWebSocket(request, object : WebSocketListener() {
        private var subscribed = false
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("CONNECT\naccept-version:1.2\nhost:${base.host}\nAuthorization:Bearer $token\nheart-beat:0,0\n\n\u0000")
        }
        override fun onMessage(webSocket: WebSocket, text: String) = receive(webSocket, text.toByteArray(Charsets.UTF_8))
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = receive(webSocket, bytes.toByteArray())
        private fun receive(socket: WebSocket, bytes: ByteArray) {
            try {
                decoder.decode(bytes).forEach { frame -> when (frame.command) {
                    "CONNECTED" -> {
                        require(!subscribed && frame.headers["version"] == "1.2")
                        subscribed = true
                        socket.send("SUBSCRIBE\nid:quotes\ndestination:/topic/market/quotes\nack:auto\n\n\u0000")
                        trySend(JSONObject().put("streamConnected", true))
                    }
                    "MESSAGE" -> {
                        require(subscribed && frame.headers["destination"] == "/topic/market/quotes" && frame.headers["subscription"] == "quotes")
                        trySend(JSONObject(frame.body))
                    }
                    "ERROR" -> throw IOException("Market stream unavailable")
                } }
            } catch (_: Exception) { socket.cancel(); close(IOException("Market stream interrupted")) }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close(IOException("Market stream interrupted")) }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null); close() }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
    })
    awaitClose { socket.cancel() }
}
