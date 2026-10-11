package com.stoxsim.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class FundamentalsTest {
    @Test fun financialHistoryJoinsByPeriodAndKeepsMissingSeparateFromZero() {
        val points = financialPoints(JSONObject("""{"metrics":[
          {"category":"Revenue","history":[{"period":"Jun 2026","value":120},{"period":"Mar 2026","value":100}]},
          {"category":"Net profit","history":[{"period":"Jun 2026","value":0},{"period":"Dec 2025","value":-5}]}]}"""))
        assertEquals(listOf("Dec 2025", "Mar 2026", "Jun 2026"), points.map { it.period })
        assertNull(points[0].revenue); assertEquals(BigDecimal("-5"), points[0].profit)
        assertNull(points[1].profit); assertEquals(BigDecimal.ZERO, points[2].profit)
    }
    @Test fun newestFiveIsoPeriodsAndNetIncomeAliasAreSupported() {
        val history = (2026 downTo 2020).joinToString(",") { """{"period":"$it-12-31","value":$it}""" }
        val points = financialPoints(JSONObject("""{"metrics":[{"category":"net_income","history":[$history]}]}"""))
        assertEquals(5, points.size); assertEquals("2022-12-31", points.first().period); assertEquals("2026-12-31", points.last().period)
        assertEquals("2026", periodLabel(points.last().period, true)); assertNull(points.last().revenue)
    }
    @Test fun lossesStayBelowZeroAndPositiveValuesAboveIt() {
        val scale = financialScale(listOf(FinancialPoint("a", BigDecimal("100"), BigDecimal("-25"))))
        assertEquals(0f, scale.fraction(BigDecimal("-25")), 0.0001f)
        assertEquals(0.2f, scale.fraction(BigDecimal.ZERO), 0.0001f)
        assertEquals(1f, scale.fraction(BigDecimal("100")), 0.0001f)
    }
    @Test fun zeroAndMissingHistoriesHaveAFiniteScale() {
        val scale = financialScale(listOf(FinancialPoint("a", BigDecimal.ZERO, null)))
        assertEquals(0f, scale.fraction(BigDecimal.ZERO), 0f)
        assertTrue(scale.high > scale.low)
        assertTrue(financialPoints(JSONObject("""{"metrics":[]}""")).isEmpty())
    }
    @Test fun descriptionsAreShortWithoutInventingFacts() {
        val source = "A company  makes products.\n" + "It serves customers in different regions. ".repeat(20)
        val excerpt = companyExcerpt(source)
        assertTrue(excerpt.length <= 180); assertTrue(excerpt.endsWith("…")); assertFalse(excerpt.contains('\n'))
        assertTrue(normalizedDescription(source).startsWith(excerpt.removeSuffix("…")))
        assertEquals("A small company.", companyExcerpt(" A small  company. "))
    }
    @Test fun quarterlyDataIsNotPresentedAsYearly() {
        val data = JSONObject("""{"timePeriod":"quarterly"}""")
        assertTrue(financialPeriodMatches(data, "quarterly")); assertFalse(financialPeriodMatches(data, "yearly"))
        assertTrue(financialPeriodMatches(JSONObject(), "yearly"))
    }
    @Test fun reportedUnitsUseIndianAndUsFormattingWithoutRescalingValues() {
        assertEquals("₹1,00,000 Cr", financialAmount(BigDecimal("100000"), "crore"))
        assertEquals("\$100,000 M", financialAmount(BigDecimal("100000"), "USD million"))
        assertEquals("₹-5 Cr", financialAmount(BigDecimal("-5"), "crore"))
        assertEquals("—", financialAmount(null, "USD million"))
    }

}
