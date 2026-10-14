package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.R
import com.stoxsim.app.data.*
import java.math.BigDecimal
import java.math.RoundingMode

@Composable internal fun PortfolioScreen(state: AppState, model: AppViewModel) {
    AccountSelector(state, model::changeRegion, model::selectAccount)
    HoldingsWorkspace(state, model::selectStock) { model.openScreen("Portfolio analysis") }
}

@Composable internal fun HoldingsWorkspace(state: AppState, select: (Instrument) -> Unit, analysis: () -> Unit) {
    val p = state.portfolio
    val value = p?.positions?.fold(BigDecimal.ZERO) { total, position -> total + position.value }
    val returns = p?.invested?.takeIf { it.signum() > 0 }?.let {
        p.unrealized.multiply(BigDecimal("100")).divide(it, 2, RoundingMode.HALF_UP)
    }
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("HOLDINGS${p?.let { " (${it.positions.size})" }.orEmpty()}")
                Text(money(value, state.currency), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
                Muted("Current value")
            }
            OutlinedIconButton(analysis, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ic_nav_stock), "Open portfolio analysis")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ValueRow("Unrealized return", "${money(p?.unrealized, state.currency)}${returns?.let { " (${percent(it)})" }.orEmpty()}", p?.unrealized)
        ValueRow("Invested", money(p?.invested, state.currency))
        p?.let { SnapshotLabel(it.status, it.valuedAt) }
        SectionError(state, "portfolio")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HoldingMetric("Available cash", money(p?.cash ?: state.account?.cash, state.currency), Modifier.weight(1f))
        HoldingMetric("Account P/L", money(p?.profitLoss, state.currency), Modifier.weight(1f), p?.profitLoss)
    }
    HoldingsList(state, select)
    OutlinedButton(analysis, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("View portfolio analysis →") }
}

@Composable private fun HoldingMetric(label: String, value: String, modifier: Modifier, amount: BigDecimal? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Muted(label); Text(value, fontWeight = FontWeight.SemiBold, color = amount?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable internal fun SnapshotLabel(status: String, at: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Status(status); Text("As of ${time(at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun HoldingsList(state: AppState, select: (Instrument) -> Unit) {
    var query by rememberSaveable(state.accountId) { mutableStateOf("") }
    var sort by rememberSaveable(state.accountId) { mutableStateOf("Name") }
    var menu by remember { mutableStateOf(false) }
    val positions = state.portfolio?.positions.orEmpty().filter { it.symbol.contains(query, true) || it.name.contains(query, true) }
    val sorted = when (sort) {
        "Value" -> positions.sortedByDescending { it.value }
        "Return" -> positions.sortedWith(compareByDescending<Position> { it.returnPercent }.thenBy { it.symbol })
        else -> positions.sortedBy { it.name.ifBlank { it.symbol }.lowercase() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your stocks", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Sort holdings") }
                DropdownMenu(menu, { menu = false }) {
                    listOf("Name", "Value", "Return").forEach { choice -> DropdownMenuItem(text = { Text("Sort by ${choice.lowercase()}") },
                        onClick = { sort = choice; menu = false }) }
                }
            }
        }
        if (state.portfolio?.positions?.isNotEmpty() == true) OutlinedTextField(query, { query = it.take(100) }, singleLine = true,
            placeholder = { Text("Search holdings") }, leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
            shape = RoundedCornerShape(28.dp), textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search holdings" },
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        if (sorted.isEmpty()) Panel {
            Text(if (query.isNotBlank()) "No matching holdings" else "Your first holding starts here", style = MaterialTheme.typography.titleMedium)
            Muted(if (query.isNotBlank()) "Try a company name or stock symbol." else if (state.portfolio == null) "Your portfolio will appear when the connection is available." else "Find a stock and place a practice order to build your portfolio.")
        } else Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column {
                sorted.forEachIndexed { index, position ->
                    HoldingRow(position, state, select)
                    if (index < sorted.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable private fun HoldingRow(position: Position, state: AppState, select: (Instrument) -> Unit) {
    Surface(onClick = { select(Instrument(position.symbol, position.name, position.exchange, state.region, currency = state.currency)) },
        enabled = !state.working, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) { Text(position.symbol.take(2), style = MaterialTheme.typography.labelMedium) }
                }
                Column(Modifier.weight(1f)) {
                    Text(position.symbol, style = MaterialTheme.typography.titleMedium)
                    Text(position.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.End) {
                    Text(money(position.value, state.currency), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    Text(percent(position.returnPercent), style = MaterialTheme.typography.bodySmall,
                        color = position.returnPercent?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Muted("${position.quantity} shares · Avg ${money(position.average, state.currency)}")
                    if (position.blockedQuantity > 0) Muted("${position.blockedQuantity} reserved")
                }
                Column(Modifier.weight(0.85f), horizontalAlignment = Alignment.End) {
                    Text(money(position.profit, state.currency), style = MaterialTheme.typography.bodySmall,
                        color = position.profit?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    Muted("LTP ${money(position.price, state.currency)}")
                }
            }
            if (position.status !in listOf("LIVE", "CLOSED")) Status(position.status)
        }
    }
}
