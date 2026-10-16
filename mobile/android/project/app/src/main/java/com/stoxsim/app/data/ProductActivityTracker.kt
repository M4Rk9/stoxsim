package com.stoxsim.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal enum class ProductActivity { ACTIVE, STOCK_OPENED }

/** Per-sign-in, in-memory throttling. The server owns identity, UTC dates and deduplication. */
internal class ProductActivityTracker {
    private data class Attempt(val day: LocalDate, val at: Instant, val confirmed: Boolean = false)
    private val attempts = mutableMapOf<ProductActivity, Attempt>()

    fun attempt(event: ProductActivity, now: Instant): LocalDate? {
        val day = now.atZone(ZoneOffset.UTC).toLocalDate()
        val previous = attempts[event]
        if (previous?.day == day && (previous.confirmed || now.isBefore(previous.at.plusSeconds(300)))) return null
        attempts[event] = Attempt(day, now)
        return day
    }

    fun confirmed(event: ProductActivity, day: LocalDate) {
        attempts[event]?.takeIf { it.day == day }?.let { attempts[event] = it.copy(confirmed = true) }
    }
}
