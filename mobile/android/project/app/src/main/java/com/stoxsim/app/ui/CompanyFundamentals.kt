package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.json.JSONObject
import java.math.BigDecimal

@Composable internal fun CompanyFundamentals(state: AppState, period: (String) -> Unit) {
    val insights = state.data["stockInsights"]
    Panel("STUDY THE BUSINESS", "Company fundamentals") {
        SectionError(state, "stockInsights")
        if (insights == null) Muted(if (state.working) "Loading company fundamentals…" else "Company fundamentals are temporarily unavailable.")
        insights?.let { data ->
            Status(data.text("status"))
            if (data.text("message").isNotBlank()) Muted(data.text("message"))
            val profile = data.optJSONObject("profile")
            val cards = mutableListOf<Triple<String, String, String>>()
            profile?.text("sector")?.takeIf { it.isNotBlank() }?.let { cards += Triple("Sector", it, "") }
            val sectorCap = profile?.text("sectorMarketCapInrFormatted")?.takeIf { it.isNotBlank() }
                ?: profile?.decimal("sectorMarketCapInrCrore")?.let { "₹${number(it)} Cr" }
            if (sectorCap != null) cards += Triple("Sector market cap", sectorCap, "")
            cards += data.objects("ratios").map { Triple(it.text("name"), it.text("companyValue").ifBlank { "—" }, it.text("sectorValue").takeIf { value -> value.isNotBlank() }?.let { "Sector $it" }.orEmpty()) }
            if (cards.isEmpty()) Muted("Valuation ratios are unavailable for this stock.") else MetricCards(cards)
            Muted("Source: ${data.text("provider").replace('_', ' ')} · Refreshed ${time(data.text("asOf"))}")
        }
    }
    Panel(title = "Financial performance") {
        ChoiceRow(listOf("quarterly" to "Quarterly", "yearly" to "Yearly"), state.insightPeriod, !state.working, period)
        val financials = insights?.optJSONObject("financials")
        if (financials == null) Muted(if (state.working) "Updating financial history…" else "Financial history is unavailable for this stock.")
        else if (!financialPeriodMatches(financials, state.insightPeriod)) {
            Muted("The provider returned ${financials.text("timePeriod")} data for this selection. ${if (state.insightPeriod == "yearly") "Yearly" else "Quarterly"} history is temporarily unavailable.")
        } else {
            Muted("${financials.text("statementType").ifBlank { "Reported financial statements" }} · ${financials.text("unitsIn").ifBlank { "reported units" }}")
            FinancialBars(financials, state.insightPeriod)
            FinancialDetails(financials)
        }
    }
    insights?.optJSONObject("profile")?.text("description")?.takeIf { it.isNotBlank() }?.let { description ->
        CompanyDescription(description)
    }
}

@Composable private fun MetricCards(cards: List<Triple<String, String, String>>) {
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    cards.chunked(columns).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (label, value, detail) -> MetricCard(label, value, detail, Modifier.weight(1f).fillMaxHeight()) }
            if (pair.size < columns) Spacer(Modifier.weight(1f))
        }
    }
}
@Composable private fun MetricCard(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Muted(label)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) Muted(detail)
        }
    }
}
@Composable internal fun CompanyDescription(description: String) {
    val full = normalizedDescription(description)
    val excerpt = companyExcerpt(full)
    var expanded by remember(full) { mutableStateOf(false) }
    Panel(title = "About the company") {
        Text(if (expanded) full else excerpt, style = MaterialTheme.typography.bodyMedium)
        if (excerpt != full) TextButton({ expanded = !expanded }) { Text(if (expanded) "Show less" else "Read more") }
    }
}

@Composable internal fun FinancialBars(financials: JSONObject, period: String) {
    val points = remember(financials) { financialPoints(financials) }
    if (points.none { it.revenue != null || it.profit != null }) { Muted("Revenue and net profit history are unavailable for this stock."); return }
    var selected by remember(points) { mutableIntStateOf(points.lastIndex) }
    val point = points[selected.coerceIn(points.indices)]
    val units = financials.text("unitsIn")
    val revenueColor = MaterialTheme.colorScheme.primary
    val profitColor = Color(0xFF7986CB)
    val lossColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val scale = remember(points) { financialScale(points) }
    Text(point.period, style = MaterialTheme.typography.titleMedium)
    MetricCards(listOf(Triple("Revenue", financialAmount(point.revenue, units), point.revenueChange),
        Triple("Net profit", financialAmount(point.profit, units), point.profitChange)))
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ChartLegend("Revenue", revenueColor); ChartLegend("Net profit", profitColor)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Muted(financialNumber(scale.high, units)); Muted(financialNumber(scale.low, units))
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp).semantics { contentDescription = "Revenue and net profit bar chart, ${if (period == "yearly") "Yearly" else "Quarterly"}" }
        .pointerInput(points) { detectTapGestures { selected = ((it.x / size.width) * points.size).toInt().coerceIn(points.indices) } }) {
        val inset = 8.dp.toPx()
        val height = size.height - 2 * inset
        fun y(value: BigDecimal) = inset + (1f - scale.fraction(value)) * height
        val zero = y(BigDecimal.ZERO)
        repeat(4) { index -> val line = inset + index * height / 3; drawLine(gridColor, Offset(0f, line), Offset(size.width, line), 1f) }
        drawLine(gridColor, Offset(0f, zero), Offset(size.width, zero), 2.dp.toPx())
        val group = size.width / points.size
        val width = (group * 0.28f).coerceAtMost(28.dp.toPx())
        points.forEachIndexed { index, item ->
            val center = group * (index + 0.5f)
            if (index == selected) drawRect(revenueColor.copy(alpha = 0.07f), Offset(group * index, 0f), Size(group, size.height))
            fun bar(value: BigDecimal?, x: Float, color: Color) {
                if (value == null) return
                val mark = y(value)
                if (value.signum() == 0) drawLine(color, Offset(x, zero), Offset(x + width, zero), 2.dp.toPx())
                else drawRect(if (value.signum() < 0) lossColor else color, Offset(x, minOf(mark, zero)), Size(width, kotlin.math.abs(mark - zero)))
            }
            bar(item.revenue, center - width - 2.dp.toPx(), revenueColor)
            bar(item.profit, center + 2.dp.toPx(), profitColor)
        }
    }
    Row(Modifier.fillMaxWidth()) {
        points.forEachIndexed { index, item ->
            Surface(onClick = { selected = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                color = if (selected == index) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = MaterialTheme.shapes.small) {
                Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                    Text(periodLabel(item.period, period == "yearly"), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Muted("Tap a period or bar group for exact values. Missing values have no bar.")
    if (scale.low.signum() < 0) Muted("Losses are red and extend below zero.")
}
@Composable private fun ChartLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(Modifier.size(10.dp), color = color, shape = MaterialTheme.shapes.small) { }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun FinancialDetails(financials: JSONObject) {
    var expanded by remember(financials) { mutableStateOf(false) }
    TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide financial details" else "Show financial details") }
    if (expanded) financials.objects("metrics").forEach { metric ->
        Text(metricTitle(metric.text("category")), style = MaterialTheme.typography.titleMedium)
        metric.objects("history").forEach { ValueRow(it.text("period"), financialAmount(it.decimal("value"), financials.text("unitsIn"))) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
