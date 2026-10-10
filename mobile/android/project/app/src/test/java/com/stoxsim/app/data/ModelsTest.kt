package com.stoxsim.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ModelsTest {
    @Test fun numericBackendBalancesParseWithoutStringAssumptions() {
        val user = parseUser(JSONObject("""{
            "displayName":"Learner","email":"learner@example.com","emailVerified":false,
            "accounts":[{"marketRegion":"INDIA","accountLabel":"India Standard","accountKind":"STANDARD",
                "currency":"INR","availableCash":99999.25,"blockedCash":0.00,"active":true}]
        }"""))
        assertEquals(BigDecimal("99999.25"), user.accounts.single().cash)
        assertEquals(0, user.accounts.single().blocked.signum())
        assertFalse(user.verified)
    }

    @Test fun unavailablePricingStatusIsPreservedRatherThanLabelledLive() {
        val portfolio = parsePortfolio(JSONObject("""{
            "currency":"INR","totalAccountValue":100001.50,"availableCash":90000.00,
            "investedValue":10000.00,"totalProfitLoss":1.50,"dataStatus":"UNAVAILABLE",
            "valuedAt":"2026-10-10T10:00:00Z","holdings":[{"symbol":"TEST","name":"Test company",
                "quantity":10,"marketValue":10001.50,"pricingStatus":"UNAVAILABLE"}]
        }"""))
        assertEquals("UNAVAILABLE", portfolio.status)
        assertEquals("UNAVAILABLE", portfolio.positions.single().status)
        assertEquals(BigDecimal("100001.50"), portfolio.total)
        assertEquals(10L, portfolio.positions.single().quantity)
    }
}
