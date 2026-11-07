package com.stoxsim.app.data

import org.junit.Assert.*
import org.junit.Test

class LearnerExperienceTest {
    @Test fun optionalProfileCanBeClearedWithoutInventingPreferences() {
        val body = learnerProfilePayload("", "", "")
        assertTrue(body.isNull("experienceLevel")); assertTrue(body.isNull("learningObjective")); assertTrue(body.isNull("preferredMarket"))
        assertEquals("BEGINNER", learnerProfilePayload("BEGINNER", "BASICS", "INDIA").getString("experienceLevel"))
        assertThrows(IllegalArgumentException::class.java) { learnerProfilePayload("ADMIN", "BASICS", "INDIA") }
    }
    @Test fun recommendationRoutesPreserveKnownJourneysAndRejectExternalNavigation() {
        assertEquals("Markets", learnerDestination("/#stock-search")); assertEquals("Orders", learnerDestination("/#orders"))
        assertEquals("Reflections", learnerDestination("/progress#reflections")); assertEquals("Learning path", learnerDestination("/progress"))
        assertEquals("Subscriptions", learnerDestination("/subscriptions"))
        listOf("https://attacker.test/", "//attacker.test/", "javascript:alert(1)", "/unknown", "/progress#unknown", "/\\attacker.test", "/%2f%2fattacker.test").forEach { assertNull(learnerDestination(it)) }
    }
    @Test fun reflectionPreservesOwnershipReferencesAndNeverAddsAIConsent() {
        val account = "00000000-0000-0000-0000-000000000001"; val order = "00000000-0000-0000-0000-000000000002"
        val body = reflectionPayload("Reason", "Assumption", "Risk", "Outcome", "Review", account, order)
        assertEquals(account, body.getString("accountId")); assertEquals(order, body.getString("orderId"))
        assertFalse(body.has("consent")); assertEquals("Review", body.getString("reflection"))
        assertThrows(IllegalArgumentException::class.java) { reflectionPayload(" ", "", "", "", "") }
        assertThrows(IllegalArgumentException::class.java) { reflectionPayload("a".repeat(2001), "", "", "", "") }
    }
}
