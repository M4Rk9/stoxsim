package com.stoxsim.app.data

import org.junit.Assert.*
import org.junit.Test

class EmailActionLinkTest {
    private val token = "a".repeat(43)
    @Test fun acceptsOnlyCanonicalStoxSimEmailRoutes() {
        assertEquals(EmailAction.VERIFY_EMAIL, EmailActionLink.parse("https://stoxsim.com/verify-email?token=$token")?.action)
        val reset = EmailActionLink.parse("https://stoxsim.com/reset-password?token=$token")!!
        assertEquals(EmailAction.RESET_PASSWORD, reset.action)
        assertEquals("https://stoxsim.com/reset-password?token=$token", reset.browserUrl())
        assertFalse(reset.toString().contains(token))
    }
    @Test fun rejectsForeignHostsRedirectsDuplicatesEncodingTricksAndMalformedTokens() {
        for (url in listOf("http://stoxsim.com/verify-email?token=$token", "https://evil.test/verify-email?token=$token",
            "https://stoxsim.com.evil.test/verify-email?token=$token", "https://evil@stoxsim.com/verify-email?token=$token",
            "https://stoxsim.com:444/verify-email?token=$token", "https://stoxsim.com/%76erify-email?token=$token",
            "https://stoxsim.com/verify-email/extra?token=$token", "https://stoxsim.com/verify-email?token=$token&token=$token",
            "https://stoxsim.com/verify-email?token=$token&redirect=https://evil.test", "https://stoxsim.com/verify-email?token=$token#fragment",
            "https://stoxsim.com/verify-email?token=%ZZ", "https://stoxsim.com/verify-email?token=short",
            "https://stoxsim.com/verify-email?token=" + "x".repeat(257), "https://stoxsim.com/verify-email", "stoxsim://verify-email?token=$token")) {
            assertNull(url.substringBefore('?'), EmailActionLink.parse(url))
        }
    }
}
