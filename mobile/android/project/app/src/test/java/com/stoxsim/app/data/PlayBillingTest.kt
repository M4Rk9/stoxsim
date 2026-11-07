package com.stoxsim.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayBillingTest {
    private fun config() = JSONObject("""{"enabled":true,"packageName":"com.stoxsim.app","obfuscatedAccountId":"opaque-account-123","products":[{"plan":"PLUS","productId":"stoxsim_plus","basePlanId":"monthly"},{"plan":"PRO","productId":"stoxsim_pro","basePlanId":"monthly"}]}""")
    @Test fun disabledCatalogCannotCreatePurchaseConfiguration() {
        val value = parsePlayConfiguration(JSONObject("""{"enabled":false}"""), "com.stoxsim.app")
        assertFalse(value.enabled); assertTrue(value.products.isEmpty()); assertEquals("", value.accountId)
    }
    @Test fun rejectsWrongPackageAndInvalidOrDuplicateCatalog() {
        listOf(config().put("packageName", "attacker.app"), config().put("obfuscatedAccountId", ""),
            config().apply { getJSONArray("products").getJSONObject(1).put("productId", "stoxsim_plus") },
            config().apply { getJSONArray("products").getJSONObject(0).put("plan", "ADMIN") }).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { parsePlayConfiguration(value, "com.stoxsim.app") }
        }
    }
    @Test fun pendingUnboundOrDifferentAccountPurchasesAreNeverSubmitted() {
        val value = parsePlayConfiguration(config(), "com.stoxsim.app")
        assertTrue(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("stoxsim_plus"), "opaque-account-123", true))
        assertFalse(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("stoxsim_plus"), "opaque-account-123", false))
        assertFalse(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("stoxsim_plus"), "other-account", true))
        assertFalse(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("stoxsim_plus"), null, true))
        assertFalse(maySubmitPlayPurchase(value, "other.app", listOf("stoxsim_plus"), "opaque-account-123", true))
        assertFalse(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("unknown"), "opaque-account-123", true))
        assertFalse(maySubmitPlayPurchase(value, "com.stoxsim.app", listOf("stoxsim_plus", "stoxsim_pro"), "opaque-account-123", true))
    }
    @Test fun unsupportedRenewalPeriodDoesNotBecomeAMisleadingPrice() {
        assertEquals("month", playRenewalPeriod("P1M")); assertEquals("year", playRenewalPeriod("P1Y"))
        assertNull(playRenewalPeriod("P7D")); assertNull(playRenewalPeriod("unknown"))
    }
}
