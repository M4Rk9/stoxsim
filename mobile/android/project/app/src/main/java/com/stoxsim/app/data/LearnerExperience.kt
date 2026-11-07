package com.stoxsim.app.data

import java.net.URI
import org.json.JSONObject

internal val learnerLevels = listOf("BEGINNER", "INTERMEDIATE", "ADVANCED")
internal val learnerObjectives = listOf("BASICS", "FUNDAMENTALS", "DIVERSIFICATION", "DISCIPLINE")
internal val learnerMarkets = listOf("INDIA", "UNITED_STATES")
internal val notificationPreferenceKeys = listOf("learning", "competitions", "portfolioEducation", "productAnnouncements", "promotionalMessages", "pushEnabled", "emailDigest")
internal fun learnerProfilePayload(level: String, objective: String, market: String): JSONObject {
    require(level.isBlank() || level in learnerLevels); require(objective.isBlank() || objective in learnerObjectives)
    require(market.isBlank() || market in learnerMarkets)
    return JSONObject().put("experienceLevel", level.ifBlank { null } ?: JSONObject.NULL)
        .put("learningObjective", objective.ifBlank { null } ?: JSONObject.NULL)
        .put("preferredMarket", market.ifBlank { null } ?: JSONObject.NULL)
}
internal fun reflectionPayload(reason: String, assumptions: String, risks: String, expected: String, reflection: String,
    accountId: String? = null, orderId: String? = null): JSONObject {
    require(reason.trim().isNotEmpty()); require(listOf(reason, assumptions, risks, expected, reflection).all { it.length <= 2000 })
    accountId?.let { java.util.UUID.fromString(it) }; orderId?.let { java.util.UUID.fromString(it) }
    return JSONObject().put("reason", reason.trim()).put("assumptions", assumptions.trim()).put("risks", risks.trim())
        .put("expectedOutcome", expected.trim()).put("reflection", reflection.trim()).put("accountId", accountId ?: JSONObject.NULL).put("orderId", orderId ?: JSONObject.NULL)
}
/** Recommendations and notifications only open known native destinations, never arbitrary URLs. */
internal fun learnerDestination(href: String): String? = try {
    val uri = URI(href)
    if (!href.startsWith('/') || href.startsWith("//") || uri.isAbsolute || uri.rawAuthority != null) null
    else if (uri.rawFragment != null) when (href) {
        "/#orders" -> "Orders"; "/#stock-search" -> "Markets"; "/progress#reflections" -> "Reflections"; else -> null
    }
    else when (uri.path) {
        "/" -> "Home"; "/markets" -> "Markets"; "/portfolio" -> "Portfolio"; "/finwiz" -> "Finwiz"
        "/learn", "/progress" -> "Learning path"; "/scenario-lab" -> "Scenario Lab"; "/reflections" -> "Reflections"
        "/competitions" -> "Competitions"; "/campus" -> "Campus"; "/settings" -> "Profile & security"
        "/account" -> "Account"; "/notifications" -> "Notifications"; "/subscription", "/subscriptions" -> "Subscriptions"
        else -> null
    }
} catch (_: Exception) { null }
