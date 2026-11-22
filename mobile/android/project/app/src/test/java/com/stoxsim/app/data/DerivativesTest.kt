package com.stoxsim.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.stoxsim.app.ui.newsDestination

class DerivativesTest {
    private fun leg(id: String, lots: Int = 1) = DerivativeLeg(JSONObject().put("id", id), "OPEN_LONG", lots)
    @Test fun reviewExpiresAndCannotAuthorizeEditedLegs() {
        val d = DerivativesState(ticket = leg("one"))
        val r = DerivativeReview(d.signature(false), JSONObject(), 30000, true)
        assertTrue(r.valid(d.signature(false), 29999))
        assertFalse(r.valid(d.signature(false), 30000))
        assertFalse(r.valid(d.copy(ticket = leg("one", 2)).signature(false), 100))
    }
    @Test fun basketSignatureIsCanonicalButQuantitySensitive() {
        val d = DerivativesState(basket = listOf(leg("one"), leg("two")))
        assertEquals(d.signature(true), d.copy(basket = d.basket.reversed()).signature(true))
        assertNotEquals(d.signature(true), d.copy(basket = listOf(leg("one", 2), leg("two"))).signature(true))
        assertEquals(2, d.payload(true).getJSONArray("legs").length())
    }
    @Test fun uncertainSubmissionKeepsExactOriginalPayload() {
        val d = DerivativesState(ticket = leg("one"))
        val original = d.payload(false).put("requestKey", "stable-key").toString()
        val pending = DerivativeSubmission(d.signature(false), "stable-key", original, false)
        val edited = d.copy(ticket = leg("two"), uncertain = pending)
        assertEquals("one", JSONObject(edited.uncertain!!.body).getString("contractId"))
        assertEquals("stable-key", JSONObject(edited.uncertain.body).getString("requestKey"))
        assertFalse(leg("one", 0).valid)
        assertFalse(leg("one", 101).valid)
    }
    @Test fun publisherLinksRejectNonHttpsAndCredentials() {
        assertNull(newsDestination("javascript:alert(1)")); assertNull(newsDestination("http://example.test"))
        assertNull(newsDestination("https://user:pass@example.test/story"))
        assertEquals("https://example.test/story", newsDestination("https://example.test/story"))
    }
}
