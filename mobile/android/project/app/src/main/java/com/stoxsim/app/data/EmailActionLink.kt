package com.stoxsim.app.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class EmailAction { VERIFY_EMAIL, RESET_PASSWORD }

/** Memory only: never save this token or URL in preferences, saved state, analytics or logs. */
internal class EmailActionLink(val action: EmailAction, val token: String) {
    fun browserUrl(): String = "https://stoxsim.com/" +
        (if (action == EmailAction.VERIFY_EMAIL) "verify-email" else "reset-password") +
        "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8.name())

    companion object {
        fun parse(raw: String?): EmailActionLink? = runCatching {
            if (raw == null || raw.length > 2048) return null
            val uri = URI(raw)
            if (uri.scheme != "https" || uri.host != "stoxsim.com" || uri.port != -1 || uri.userInfo != null || uri.rawFragment != null) return null
            val action = when (uri.rawPath) {
                "/verify-email" -> EmailAction.VERIFY_EMAIL
                "/reset-password" -> EmailAction.RESET_PASSWORD
                else -> return null
            }
            // Exactly one parameter: reject duplicates, arbitrary redirects and nested URLs.
            val query = uri.rawQuery ?: return null
            if (!query.startsWith("token=") || query.contains('&')) return null
            val token = URLDecoder.decode(query.removePrefix("token="), StandardCharsets.UTF_8.name())
            if (!Regex("[A-Za-z0-9_-]{20,256}").matches(token)) return null
            EmailActionLink(action, token)
        }.getOrNull()
    }
}
