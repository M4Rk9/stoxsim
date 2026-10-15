package com.stoxsim.app.data

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class DailyStreakTrackerTest {
    private val now = Instant.parse("2026-10-15T18:29:00Z")
    @Test fun loginIsDueAndRepeatedOpeningDoesNotRecordAnotherVisitToday() {
        val tracker = DailyStreakTracker()
        assertTrue(tracker.shouldRecord(now))
        tracker.attempted(now)
        tracker.confirmed("2026-10-15", "Asia/Kolkata")
        assertFalse(tracker.shouldRecord(now.plusSeconds(59)))
        assertTrue(tracker.shouldRecord(now.plusSeconds(60))) // Midnight in the server's zone.
    }
    @Test fun sameDayResumeUsesServerZoneRatherThanUtcDate() {
        val tracker = DailyStreakTracker()
        tracker.attempted(now)
        tracker.confirmed("2026-10-15", "America/New_York")
        assertFalse(tracker.shouldRecord(now.plusSeconds(3600)))
        assertTrue(tracker.shouldRecord(Instant.parse("2026-10-16T04:00:00Z")))
    }
    @Test fun failureRetryIsBoundedAndDoesNotClaimLocalCredit() {
        val tracker = DailyStreakTracker()
        tracker.attempted(now)
        assertFalse(tracker.shouldRecord(now.plusSeconds(30)))
        assertTrue(tracker.shouldRecord(now.plusSeconds(60)))
        tracker.confirmed("invalid", "unknown")
        assertTrue(tracker.shouldRecord(now.plusSeconds(60)))
    }
    @Test fun newAccountAndNewProcessCannotInheritAnotherAccountsCredit() {
        val first = DailyStreakTracker()
        first.attempted(now); first.confirmed("2026-10-15", "UTC")
        assertFalse(first.shouldRecord(now.plusSeconds(120)))
        assertTrue(DailyStreakTracker().shouldRecord(now.plusSeconds(120)))
    }
}
