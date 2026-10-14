package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.R
import com.stoxsim.app.data.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

@Composable internal fun ScenarioWorkspace(state: AppState, changeRegion: (String) -> Unit, selectAccount: (String) -> Unit,
    run: (JSONObject, String, List<BigDecimal>) -> Unit, clearResult: () -> Unit) {
    var selected by rememberSaveable(state.accountId) { mutableStateOf("recovery") }
    var title by rememberSaveable(state.accountId) { mutableStateOf("My market path") }
    var input by rememberSaveable(state.accountId) { mutableStateOf("10, 25, 50") }
    var step by rememberSaveable(state.accountId) { mutableIntStateOf(0) }
    val catalog = state.lists["scenarios"].orEmpty()
    val definition = catalog.find { it.text("id") == selected }
    val shocks = input.split(',').map { it.trim().toBigDecimalOrNull() }
    val pathValid = shocks.size in 1..12 && shocks.all { it != null && it >= BigDecimal("-100") && it <= BigDecimal("100") && it.stripTrailingZeros().scale() <= 2 }
    val valid = selected != "custom" || (title.trim().isNotEmpty() && title.length <= 80 && pathValid)
    val remaining = state.data["credits"]?.decimal("remaining")
    val purple = if (state.dark) Color(0xFFB5A5FF) else Color(0xFF6E56BB)
    val preview = if (selected == "custom") if (pathValid) shocks.filterNotNull() else emptyList()
        else definition?.optJSONArray("shocks")?.let { a -> (0 until a.length()).mapNotNull { a.opt(it)?.toString()?.toBigDecimalOrNull() } }.orEmpty()

    ScenarioHero(state)
    AccountSelector(state, changeRegion, selectAccount)
    SectionError(state, "scenarios"); SectionError(state, "credits")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Choose your experiment", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Text("${catalog.size} paths", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (catalog.isEmpty()) Panel { Muted(if (state.working) "Loading scenario paths…" else "Scenario paths are unavailable. Refresh to try again.") }
    catalog.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { item ->
                val custom = item.text("id") == "custom"
                val accent = if (custom || item.text("id") == "whipsaw") purple else MaterialTheme.colorScheme.primary
                val active = item.text("id") == selected
                OutlinedCard(onClick = { selected = item.text("id"); step = 0; clearResult() }, enabled = !state.working,
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "Select scenario ${item.text("title")}" },
                    shape = MaterialTheme.shapes.medium, border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) accent else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(containerColor = if (active) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.12f)) {
                            Icon(painterResource(if (custom) R.drawable.ic_nav_scenario else R.drawable.ic_nav_stock), null,
                                Modifier.padding(10.dp).size(22.dp), tint = accent)
                        }
                        Text(item.text("title"), style = MaterialTheme.typography.titleMedium)
                        Text(if (custom) "Build your own price path" else "${item.optJSONArray("shocks")?.length() ?: 0} hypothetical moves", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (active) "Selected ✓" else "Explore path →", style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (selected == "custom") Panel("YOUR RULES", "Build a market path") {
        OutlinedTextField(title, { title = it.take(80); clearResult() }, label = { Text("Scenario name") }, modifier = Modifier.fillMaxWidth(), enabled = !state.working)
        OutlinedTextField(input, { input = it.take(180); step = 0; clearResult() }, label = { Text("Price changes (%) separated by commas") },
            modifier = Modifier.fillMaxWidth(), enabled = !state.working, isError = !pathValid)
        if (pathValid) {
            val values = shocks.filterNotNull()
            val index = step.coerceIn(0, values.lastIndex)
            ChoiceRow(values.mapIndexed { i, _ -> i.toString() to "Step ${i + 1}" }, index.toString(), !state.working) { step = it.toInt() }
            fun adjust(value: BigDecimal) {
                input = values.mapIndexed { i, old -> if (i == index) value.coerceIn(BigDecimal("-100"), BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros() else old }.joinToString(", ")
                clearResult()
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton({ adjust(values[index] - BigDecimal("5")) }, enabled = !state.working,
                    modifier = Modifier.semantics { contentDescription = "Decrease selected shock by 5 percent" }) { Text("−5%") }
                Text(percent(values[index]), style = MaterialTheme.typography.headlineSmall, color = purple)
                OutlinedButton({ adjust(values[index] + BigDecimal("5")) }, enabled = !state.working,
                    modifier = Modifier.semantics { contentDescription = "Increase selected shock by 5 percent" }) { Text("+5%") }
            }
            Slider(values[index].toFloat(), { adjust(it.toString().toBigDecimal()) }, valueRange = -100f..100f,
                enabled = !state.working, modifier = Modifier.semantics { contentDescription = "Selected price shock" },
                colors = SliderDefaults.colors(thumbColor = purple, activeTrackColor = purple))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ input += ", 0"; step = values.size; clearResult() }, enabled = !state.working && values.size < 12) { Text("+ Add step") }
                TextButton({ input = values.filterIndexed { i, _ -> i != index }.joinToString(", "); step = 0; clearResult() }, enabled = !state.working && values.size > 1) { Text("Remove step") }
            }
        }
        Muted("Use 1–12 moves from −100% to +100%, with at most two decimals.")
    }
    Panel("PRICE PATH", if (selected == "custom") title.ifBlank { "Custom path" } else definition?.text("title").orEmpty()) {
        if (preview.isNotEmpty()) LineChart(listOf(BigDecimal.ZERO) + preview, listOf("Start") + preview.indices.map { "Step ${it + 1}" },
            "Hypothetical cumulative price shocks", lineColor = purple)
        else Muted("Choose a path or enter valid price moves to see its shape.")
        definition?.let { Text(it.text("lesson"), style = MaterialTheme.typography.bodyMedium) }
        Muted("Each move is relative to the starting price, not the previous step. This preview does not spend credits.")
        Button({ definition?.let { run(it, title, shocks.filterNotNull()) } },
            enabled = !state.working && definition != null && valid && (remaining?.signum() ?: 0) > 0 && state.accountId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp)) {
            Icon(painterResource(R.drawable.ic_nav_scenario), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
            Text(if (state.working) "Running experiment…" else "Run scenario · 1 credit")
        }
        if (remaining?.signum() == 0) Muted("No credits remain in your current allowance.")
    }
    ScenarioResult(state, purple)
}

@Composable private fun ScenarioHero(state: AppState) {
    val credits = state.data["credits"]
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Brush.linearGradient(listOf(Color(0xFF103E35), Color(0xFF25203F)))).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(painterResource(R.drawable.ic_nav_scenario), null, Modifier.size(24.dp), tint = Color(0xFF8CE3C2))
            Text("Scenario Lab", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Text("See the what-if.\nFind your next insight.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text("Test market moves against your portfolio.", color = Color(0xFFD1D9D8), style = MaterialTheme.typography.bodyMedium)
        Surface(color = Color.White.copy(alpha = 0.09f), shape = RoundedCornerShape(50)) {
            Text(credits?.let { "${it.text("remaining")} / ${it.text("allowance")} credits · ${it.text("plan")}" } ?: "Credits unavailable",
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color(0xFFCFC4FF), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable internal fun ScenarioResult(state: AppState, purple: Color) {
    state.data["scenarioResult"]?.let { result -> Panel("YOUR EXPERIMENT", "${result.optJSONObject("scenario")?.text("title").orEmpty()} · Results") {
        val projection = result.getJSONObject("projection")
        val steps = projection.objects("steps")
        Text(money(steps.lastOrNull()?.decimal("equity"), state.currency), style = MaterialTheme.typography.headlineLarge)
        Muted("Hypothetical ending value")
        ValueRow("Portfolio change", percent(projection.decimal("returnPercent")), projection.decimal("returnPercent"))
        LineChart(steps.map { it.decimal("equity") }, steps.map { "Step ${it.text("step")}" }, "Hypothetical portfolio value by scenario step", lineColor = purple)
        ValueRow("Starting value", money(result.decimal("startingEquity"), state.currency))
        ValueRow("Cash held", money(result.decimal("cash"), state.currency))
        ValueRow("Largest drawdown", percent(projection.decimal("maximumDrawdownPercent")))
        var expanded by remember(result) { mutableStateOf(false) }
        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide experiment details" else "View steps & assumptions") }
        if (expanded) {
            steps.forEach { ValueRow("${it.text("step")} · ${percent(it.decimal("shockPercent"))}", money(it.decimal("equity"), state.currency)) }
            result.objects("positions").forEach { ValueRow(it.text("symbol"), money(it.decimal("endingValue"), state.currency)); ValueRow("Change", money(it.decimal("change"), state.currency), it.decimal("change")) }
            result.strings("assumptions").forEach { Muted(it) }
        }
        Muted("Hypothetical model · Your holdings and balances are unchanged")
    } }
}
