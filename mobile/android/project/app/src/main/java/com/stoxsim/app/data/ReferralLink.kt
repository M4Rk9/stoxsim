package com.stoxsim.app.data

import java.net.URI
import java.util.Locale

internal object ReferralLink {
    private val code = Regex("[A-Z2-7]{16}")

    fun parse(raw: String?): String? {
        if (raw == null || raw.length > 256) return null
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host != "stoxsim.com" || uri.port != -1 ||
            uri.userInfo != null || uri.rawPath != "/invite" || uri.rawFragment != null) return null
        val query = uri.rawQuery ?: return null
        if (!query.startsWith("ref=")) return null
        return query.removePrefix("ref=").uppercase(Locale.ROOT).takeIf(code::matches)
    }
}
