package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

@Composable internal fun Panel(kicker: String = "", title: String = "", content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kicker.isNotBlank()) Kicker(kicker)
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
@Composable internal fun Kicker(value: String) { Text(value.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable internal fun Muted(value: String) { Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable internal fun Message(error: String?, notice: String? = null) {
    if (error != null || notice != null) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
        Text(error ?: notice.orEmpty(), Modifier.padding(14.dp), color = if (error != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
@Composable internal fun SectionError(state: AppState, key: String) { state.sectionErrors[key]?.let { Message(it) } }
@Composable internal fun Status(value: String) { if (value.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) { Text(value.replace('_', ' '), Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall) } }
@Composable internal fun ChoiceRow(options: List<Pair<String, String>>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) -> FilterChip(selected == key, { onSelect(key) }, label = { Text(label) }, enabled = enabled) }
    }
}
@Composable internal fun ValueRow(label: String, value: String, amount: BigDecimal? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if (amount == null) MaterialTheme.colorScheme.onSurface else tone(amount))
    }
}
@Composable internal fun tone(amount: BigDecimal) = if (amount.signum() >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
internal fun money(value: BigDecimal?, currency: String) = value?.let {
    NumberFormat.getCurrencyInstance(if (currency == "INR") Locale.forLanguageTag("en-IN") else Locale.US).apply { this.currency = Currency.getInstance(currency) }.format(it)
} ?: "—"
internal fun number(value: BigDecimal?) = value?.let { NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply { maximumFractionDigits = 2 }.format(it) } ?: "—"
internal fun percent(value: BigDecimal?) = value?.let { "${if (it.signum() > 0) "+" else ""}${number(it)}%" } ?: "—"
internal fun time(value: String) = try { DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value)) } catch (_: Exception) { value.ifBlank { "—" } }
internal fun regionLabel(region: String) = if (region == "INDIA") "India" else "US"

/** Null marks and explicit history breaks leave gaps instead of implying unobserved returns. */
@Composable internal fun LineChart(values: List<BigDecimal?>, labels: List<String>, title: String,
    benchmark: List<BigDecimal?> = emptyList(), connect: Boolean = true) {
    var selected by remember(values) { mutableIntStateOf(-1) }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val secondary = MaterialTheme.colorScheme.secondary
    val marks = (values + benchmark).filterNotNull()
    if (marks.isEmpty()) { Muted("No observed data available yet."); return }
    val low = marks.minOrNull()!!.toDouble()
    val high = marks.maxOrNull()!!.toDouble()
    val span = (high - low).coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted(number(marks.maxOrNull())); Muted(number(marks.minOrNull())) }
        Canvas(Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = title }
            .pointerInput(values) { detectTapGestures { point -> selected = ((point.x / size.width) * (values.size - 1)).toInt().coerceIn(0, values.lastIndex) } }) {
            val inset = 8.dp.toPx()
            fun point(index: Int, value: BigDecimal) = Offset(inset + index.toFloat() / (values.size - 1).coerceAtLeast(1) * (size.width - inset * 2),
                size.height - inset - ((value.toDouble() - low) / span).toFloat() * (size.height - inset * 2))
            repeat(4) { i -> val y = inset + i * (size.height - inset * 2) / 3; drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1f) }
            fun series(items: List<BigDecimal?>, color: androidx.compose.ui.graphics.Color) {
                val path = Path(); var drawing = false
                items.forEachIndexed { i, value ->
                    if (value == null) drawing = false else {
                        val p = point(i, value)
                        if (drawing && connect) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                        drawing = true
                        if (!connect || items.size == 1) drawCircle(color, 3.dp.toPx(), p)
                    }
                }
                if (connect) drawPath(path, color, style = Stroke(2.dp.toPx()))
            }
            series(benchmark, secondary); series(values, primary)
            if (selected in values.indices) values[selected]?.let { drawCircle(primary, 5.dp.toPx(), point(selected, it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted(labels.firstOrNull().orEmpty()); Muted(labels.lastOrNull().orEmpty()) }
        if (selected in values.indices) Text("${labels.getOrNull(selected).orEmpty()} · ${number(values[selected])}", style = MaterialTheme.typography.bodySmall)
        Muted("Tap the chart to inspect an observation.")
    }
}

@Composable internal fun SectorChart(weights: List<BigDecimal>) {
    if (weights.isEmpty()) return
    val colors = listOf(0xFF7C6EE6, 0xFF00B386, 0xFFF2B85B, 0xFF5F9DE8, 0xFFE889A5, 0xFF8EB68A).map { androidx.compose.ui.graphics.Color(it) }
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Sector allocation as a share of invested value" }) {
        val diameter = size.height - 32.dp.toPx()
        val left = (size.width - diameter) / 2
        var start = -90f
        weights.forEachIndexed { index, value ->
            val sweep = value.toFloat().coerceIn(0f, 100f) * 3.6f
            drawArc(colors[index % colors.size], start, sweep, false, Offset(left, 16.dp.toPx()), androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(24.dp.toPx()))
            start += sweep
        }
    }
}
