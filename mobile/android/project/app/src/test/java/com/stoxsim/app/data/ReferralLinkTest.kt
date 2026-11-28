package com.stoxsim.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReferralLinkTest {
    @Test fun acceptsTheVerifiedHostAndCanonicalizesTheCode() {
        assertEquals("ABCDEFGHIJKLMNOP", ReferralLink.parse("https://stoxsim.com/invite?ref=abcdefghijklmnop"))
    }

    @Test fun rejectsUnknownHostsPathsAndAmbiguousQueries() {
        listOf(
            "https://stoxsim.com.evil.test/invite?ref=ABCDEFGHIJKLMNOP",
            "http://stoxsim.com/invite?ref=ABCDEFGHIJKLMNOP",
            "https://stoxsim.com:443/invite?ref=ABCDEFGHIJKLMNOP",
            "https://user@stoxsim.com/invite?ref=ABCDEFGHIJKLMNOP",
            "https://stoxsim.com/verify-email?ref=ABCDEFGHIJKLMNOP",
            "https://stoxsim.com/invite?ref=ABCDEFGHIJKLMNOP&ref=QRSTUVWXYZ234567",
            "https://stoxsim.com/invite?ref=ABCDEFGHIJKLMNOP#fragment",
            "https://stoxsim.com/invite?ref=ABCDEFGHIJKLMNO8"
        ).forEach { assertNull(it, ReferralLink.parse(it)) }
    }
}
