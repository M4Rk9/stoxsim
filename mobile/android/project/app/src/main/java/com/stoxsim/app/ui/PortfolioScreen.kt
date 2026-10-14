package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*

@Composable internal fun PortfolioAnalysisScreen(state: AppState, change: (Int) -> Unit) {
    var section by rememberSaveable(state.accountId) { mutableStateOf("Performance") }
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { Muted("Account value"); Text(money(state.portfolio?.total, state.currency), style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.weight(1f)) { Muted("Starting capital"); Text(money(state.portfolio?.capital, state.currency), style = MaterialTheme.typography.titleLarge) }
        }
        ValueRow("All-time return", percent(state.portfolio?.returnPercent), state.portfolio?.returnPercent)
        state.portfolio?.let { SnapshotLabel(it.status, it.valuedAt) }
        SectionError(state, "portfolio")
    }
    ChoiceRow(listOf("Performance", "Allocation", "StoxScore").map { it to it }, section) { section = it }
    if (section == "Performance") HistoryPanel(state, change)
    if (section == "StoxScore") Panel("PORTFOLIO STRUCTURE", "StoxScore") {
        SectionError(state, "analytics")
        state.data["analytics"]?.let { data ->
            Text(data.decimal("stoxScore")?.let { "${number(it)} / 100" } ?: "Not enough data", style = MaterialTheme.typography.headlineMedium)
            Status(data.text("structureBand")); Status(data.text("status"))
            ValueRow("Coverage", percent(data.decimal("dataCoveragePercent")))
            ValueRow("Largest position", percent(data.decimal("largestPositionWeightPercent")))
            ValueRow("Top three positions", percent(data.decimal("topThreeWeightPercent")))
            var expanded by remember { mutableStateOf(false) }
            TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide score breakdown" else "View score breakdown") }
            if (expanded) data.objects("components").forEach { component ->
                Text(component.text("label"), fontWeight = FontWeight.Bold)
                ValueRow("Score / weight", "${number(component.decimal("score"))} / ${percent(component.decimal("weightPercent"))}")
                Text(component.text("explanation"))
            }
            data.strings("observations").forEach { Text(it) }; Muted(data.text("disclaimer"))
        }
    }
    if (section == "Allocation") {
    Panel("ALLOCATION & ATTRIBUTION", "Understand your portfolio") {
        SectionError(state, "insights")
        state.data["insights"]?.let { data ->
            Status(data.text("status")); ValueRow("Cash weight", percent(data.decimal("cashWeightPercent")))
            data.objects("allocations").forEach { allocation ->
                ValueRow(allocation.text("symbol"), percent(allocation.decimal("accountWeightPercent")))
                LinearProgressIndicator(progress = { (allocation.decimal("accountWeightPercent")?.toFloat()?.div(100) ?: 0f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Text("P/L contribution", style = MaterialTheme.typography.titleMedium)
            data.objects("attributions").forEach { ValueRow(it.text("symbol"), money(it.decimal("totalContribution"), state.currency), it.decimal("totalContribution")) }
            data.strings("observations").forEach { Text(it) }; Muted(data.text("disclaimer"))
        }
    }
    Panel("STOX ANALYSIS", "Sector allocation") {
        SectionError(state, "sectors")
        val sectors = state.lists["sectors"].orEmpty()
        if (sectors.isEmpty()) Muted("Make your first trade to see sector allocation.")
        SectorChart(sectors.map { it.decimal("percent") ?: java.math.BigDecimal.ZERO })
        sectors.forEach { ValueRow(it.text("name"), percent(it.decimal("percent"))); Muted(money(it.decimal("value"), state.currency)) }
    }
    }
}
@Composable internal fun HistoryPanel(state: AppState, change: (Int) -> Unit) {
    var days by rememberSaveable(state.accountId) { mutableStateOf("30") }
    Panel("PERFORMANCE OVER TIME", "Portfolio history") {
        ChoiceRow(listOf("30" to "30 days", "90" to "90 days · Plus", "365" to "1 year · Plus"), days, !state.working) { days = it; change(it.toInt()) }
        SectionError(state, "history")
        state.data["history"]?.let { data ->
            Status(data.text("status"))
            val normalized = data.text("tier") != "FREE"
            val points = data.objects("points")
            val values = points.map { it.decimal(if (normalized) "portfolioIndex" else "equity") }
            val benchmark = if (normalized) points.map { it.decimal("benchmarkIndex") } else emptyList()
            val connect = data.optInt("missingSessions") == 0 && data.optInt("cashFlowBreaks") == 0
            if (!connect) Muted("Some performance metrics need more history. Gaps and cash adjustments are not connected.")
            LineChart(values, points.map { it.text("day") }, "Observed portfolio history", benchmark, connect)
            Muted(if (normalized) "Portfolio: green · Benchmark: grey · Starting index 100" else "Observed equity · ${data.text("currency")}")
            if (normalized) {
                ValueRow("Observed return", percent(data.decimal("returnPercent")))
                ValueRow(data.text("benchmark"), percent(data.decimal("benchmarkReturnPercent")))
                ValueRow("Maximum drawdown", percent(data.decimal("maximumDrawdownPercent")))
                data.optJSONObject("risk")?.let { risk ->
                    ValueRow("Volatility · Pro", percent(risk.decimal("volatilityPercent")))
                    ValueRow("Beta · Pro", number(risk.decimal("beta"))); ValueRow("Correlation · Pro", number(risk.decimal("correlation")))
                }
            } else Muted("Plus unlocks longer history, benchmarks and drawdown. Pro adds risk metrics. Existing benefits apply here too.")
            var expanded by remember { mutableStateOf(false) }
            TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide daily observations" else "View daily observations") }
            if (expanded) points.forEach { ValueRow(it.text("day"), money(it.decimal("equity"), state.currency)); Muted(it.text("quality")) }
        }
    }
}
