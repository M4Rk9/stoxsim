package com.stoxsim.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A per-sign-in optimization only. The server owns dates, streaks and duplicate prevention. */
internal class DailyStreakTracker {
    private var attemptedAt: Instant? = null
    private var creditedDate: LocalDate? = null
    private var zone: ZoneId? = null

    fun shouldRecord(now: Instant): Boolean {
        val last = attemptedAt
        if (last != null && now.isBefore(last.plusSeconds(60))) return false
        return zone?.let { now.atZone(it).toLocalDate() != creditedDate } ?: true
    }

    fun attempted(now: Instant) { attemptedAt = now }

    fun confirmed(date: String, zoneId: String) {
        // Missing/malformed acknowledgements remain retryable; never assume success locally.
        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return
        val parsedZone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return
        creditedDate = parsedDate
        zone = parsedZone
    }
}
