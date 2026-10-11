package com.stoxsim.app.data

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class FinancialPoint(val period: String, val revenue: BigDecimal?, val profit: BigDecimal?,
    val revenueChange: String = "", val profitChange: String = "")
internal data class FinancialScale(val low: BigDecimal, val high: BigDecimal) {
    fun fraction(value: BigDecimal): Float = value.subtract(low).divide(high.subtract(low), 8, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}
internal fun financialScale(points: List<FinancialPoint>): FinancialScale {
    val values = points.flatMap { listOfNotNull(it.revenue, it.profit) } + BigDecimal.ZERO
    val low = values.minOrNull()!!; val high = values.maxOrNull()!!
    return FinancialScale(low, if (high.compareTo(low) == 0) low.add(BigDecimal.ONE) else high)
}
internal fun metricKey(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_").trim('_')
internal fun metricTitle(value: String) = when (metricKey(value)) {
    "net_income", "net_profit" -> "Net profit"
    else -> metricKey(value).split('_').joinToString(" ").replaceFirstChar { it.uppercase() }
}
internal fun reportingDate(period: String): LocalDate? {
    try { return LocalDate.parse(period) } catch (_: Exception) { }
    try { return YearMonth.parse(period, DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH)).atEndOfMonth() } catch (_: Exception) { }
    return period.toIntOrNull()?.takeIf { it in 1900..2200 }?.let { LocalDate.of(it, 12, 31) }
}
/** Providers return newest first. Sort known date labels; otherwise preserve that contract. */
internal fun financialPoints(financials: JSONObject): List<FinancialPoint> {
    val metrics = financials.objects("metrics")
    fun history(keys: Set<String>) = metrics.firstOrNull { metricKey(it.text("category")) in keys }
        ?.objects("history").orEmpty().filter { it.text("period").isNotBlank() }.associateBy { it.text("period") }
    val revenue = history(setOf("revenue")); val profit = history(setOf("net_profit", "net_income"))
    val periods = (revenue.keys + profit.keys).distinct()
    val ordered = if (periods.all { reportingDate(it) != null }) periods.sortedBy { reportingDate(it) } else periods.reversed()
    return ordered.takeLast(5).map { period -> FinancialPoint(period, revenue[period]?.decimal("value"), profit[period]?.decimal("value"),
        revenue[period]?.text("change").orEmpty(), profit[period]?.text("change").orEmpty()) }
}
internal fun periodLabel(period: String, yearly: Boolean): String = reportingDate(period)?.let {
    if (yearly) it.year.toString() else it.format(DateTimeFormatter.ofPattern("MMM\nuuuu", Locale.ENGLISH))
} ?: period
internal fun financialPeriodMatches(financials: JSONObject, requested: String): Boolean {
    val reported = financials.text("timePeriod").trim().lowercase(Locale.ROOT)
    return reported.isBlank() || reported == requested
}
internal fun normalizedDescription(value: String) = value.trim().replace(Regex("\\s+"), " ")
internal fun companyExcerpt(value: String): String {
    val text = normalizedDescription(value)
    if (text.length <= 180) return text
    val prefix = text.take(177)
    val end = prefix.lastIndexOf(' ').takeIf { it >= 120 } ?: prefix.length
    return prefix.take(end).trimEnd('.', ',', ';', ':') + "…"
}

internal fun financialNumber(value: BigDecimal, units: String): String =
    NumberFormat.getNumberInstance(if (units.contains("USD", ignoreCase = true)) Locale.US else Locale.forLanguageTag("en-IN"))
        .apply { maximumFractionDigits = 2 }.format(value)
internal fun financialAmount(value: BigDecimal?, units: String): String {
    if (value == null) return "—"
    val amount = financialNumber(value, units)
    return when {
        units.contains("crore", ignoreCase = true) -> "₹$amount Cr"
        units.contains("USD", ignoreCase = true) && units.contains("million", ignoreCase = true) -> "\$$amount M"
        else -> "$amount${if (units.isBlank()) "" else " $units"}"
    }
}
