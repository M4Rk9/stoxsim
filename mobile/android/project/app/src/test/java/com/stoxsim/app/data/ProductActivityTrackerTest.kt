package com.stoxsim.app.data

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProductActivityTrackerTest {
    private val now = Instant.parse("2026-10-16T23:59:00Z")
    @Test fun dailyAcknowledgementDeduplicatesAndUtcMidnightStartsAnotherDay() {
        val tracker = ProductActivityTracker()
        val day = tracker.attempt(ProductActivity.ACTIVE, now)!!
        tracker.confirmed(ProductActivity.ACTIVE, day)
        assertNull(tracker.attempt(ProductActivity.ACTIVE, now.plusSeconds(30)))
        assertNotNull(tracker.attempt(ProductActivity.ACTIVE, now.plusSeconds(61)))
        assertNotNull(tracker.attempt(ProductActivity.STOCK_OPENED, now.plusSeconds(61)))
    }
    @Test fun failuresRetryAfterFiveMinutesAndSuccessIsNotAssumed() {
        val tracker = ProductActivityTracker()
        tracker.attempt(ProductActivity.STOCK_OPENED, now.minusSeconds(3600))
        assertNull(tracker.attempt(ProductActivity.STOCK_OPENED, now.minusSeconds(3590)))
        assertNotNull(tracker.attempt(ProductActivity.STOCK_OPENED, now.minusSeconds(3300)))
    }
    @Test fun accountChangesDoNotShareAcknowledgements() {
        val first = ProductActivityTracker()
        first.confirmed(ProductActivity.ACTIVE, first.attempt(ProductActivity.ACTIVE, now)!!)
        assertNotNull(ProductActivityTracker().attempt(ProductActivity.ACTIVE, now))
    }
}
