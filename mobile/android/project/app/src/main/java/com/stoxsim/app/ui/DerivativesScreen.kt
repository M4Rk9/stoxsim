package com.stoxsim.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stoxsim.app.data.*
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable internal fun DerivativesScreen(d: DerivativesState, working: Boolean,
    market: (String, String, Int) -> Unit, ticket: (DerivativeLeg?) -> Unit,
    basket: (List<DerivativeLeg>) -> Unit, review: (Boolean) -> Unit, acknowledge: (Boolean) -> Unit,
    submit: (Boolean) -> Unit, discard: () -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("ALL") }
    var confirmDiscard by remember { mutableStateOf(false) }
    val editable = !working && d.uncertain == null
    Text("F&O practice", style = MaterialTheme.typography.headlineMedium)
    Text("NSE index contracts · Separate INR virtual wallet")
    if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (d.enabled != true) {
        Panel { Text(if (d.enabled == false) "F&O practice is unavailable on this server." else "Loading F&O availability…") }
        return
    }
    Panel(title = "Discover contracts") {
        ChoiceRow(listOf("beginner" to "Beginner", "advanced" to "Advanced"), if (advanced) "advanced" else "beginner") { advanced = it == "advanced" }
        if (!advanced) Text("Calls gain expiry value above their strike; puts below it. Premium is per unit. A lot contains the contract’s stated units. Futures expose you to the full contract value.")
        Text("Contract prices are not the index spot price. Spot, Greeks and open interest are unavailable here.")
        ChoiceRow(listOf("NSE_INDEX|Nifty 50" to "Nifty 50", "NSE_INDEX|Nifty Bank" to "Nifty Bank"), d.underlying, editable) { market(it, "", 0) }
        ChoiceRow(d.expiries.map { it to it }, d.expiry, editable) { market(d.underlying, it, 0) }
        if (d.expiries.isEmpty()) Text("No supported expiries are available.")
        OutlinedTextField(query, { query = it }, label = { Text("Find symbol or strike on this page") }, modifier = Modifier.fillMaxWidth())
        ChoiceRow(listOf("ALL" to "All", "FUT" to "Futures", "CE" to "Calls", "PE" to "Puts"), kind) { kind = it }
        val matches = d.chain.filter { val c = it.getJSONObject("contract"); (kind == "ALL" || c.text("kind") == kind) && (query.isBlank() || c.text("symbol").contains(query, true) || c.text("strike").contains(query)) }
        if (matches.isEmpty()) Text("No matching contracts on this page.")
        matches.forEach { quote ->
            val c = quote.getJSONObject("contract")
            HorizontalDivider()
            ContractDetails(c, advanced)
            ValueRow("Contract price / premium", money(quote.decimal("last"), "INR"))
            Text("Quote ${time(quote.text("lastTradeAt"))} · ${if (quote.optBoolean("fresh")) "Fresh" else "Stale or unavailable"}")
            if (advanced) { ValueRow("Bid / ask", "${money(quote.decimal("bid"), "INR")} / ${money(quote.decimal("ask"), "INR")}"); ValueRow("Volume", number(quote.decimal("volume"))) }
            OutlinedButton({ ticket(DerivativeLeg(c, "OPEN_LONG", 1)) }, enabled = editable && quote.optBoolean("fresh")) { Text("Open long ${c.text("symbol")}") }
            if (c.text("kind") == "FUT") OutlinedButton({ ticket(DerivativeLeg(c, "OPEN_SHORT", 1)) }, enabled = editable && quote.optBoolean("fresh")) { Text("Open short ${c.text("symbol")}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ market(d.underlying, d.expiry, (d.offset - 25).coerceAtLeast(0)) }, enabled = editable && d.offset > 0) { Text("Previous strikes") }
            TextButton({ market(d.underlying, d.expiry, d.offset + 25) }, enabled = editable && d.chain.isNotEmpty() && d.offset < 1000) { Text("More strikes") }
        }
        Text("Source: Upstox. Filters apply to loaded contracts only; quotes refresh when requested.")
        TextButton({ market(d.underlying, d.expiry, d.offset) }, enabled = !working) { Text("Refresh quotes and fills") }
    }
    d.uncertain?.let {
        Panel(title = "Confirm your submission") {
            Text("The result is not confirmed. Check recent fills. Retry sends the exact same reviewed request and cannot duplicate a completed fill. It may execute if the first request did not reach the server, using current quotes.")
            Button({ submit(true) }, enabled = !working) { Text("Retry unchanged submission") }
            TextButton({ confirmDiscard = true }, enabled = !working) { Text("I checked fills — discard pending draft") }
        }
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("Discard retry information?") },
        text = { Text("This does not cancel an order. Check fills first. Starting a new order uses a new key and could add another position.") },
        confirmButton = { TextButton({ confirmDiscard = false; discard() }) { Text("Discard draft") } },
        dismissButton = { TextButton({ confirmDiscard = false }) { Text("Keep draft") } })
    d.ticket?.let { leg -> Panel(title = "Virtual order ticket") {
        ContractDetails(leg.contract, true)
        Text(actionLabel(leg.action))
        LotInput("Order lots", leg.lots, editable) { ticket(leg.copy(lots = it)) }
        Text("Quantity: ${leg.lots.toLong() * leg.contract.optInt("lotSize")} units")
        Button({ review(false) }, enabled = editable && leg.valid) { Text("Review order risk") }
        if (leg.action != "CLOSE") OutlinedButton({ basket(d.basket + leg) }, enabled = editable && leg.valid && d.basket.size < 4) { Text("Add to strategy basket") }
        TextButton({ ticket(null) }, enabled = editable) { Text("Clear ticket") }
    } }
    Panel(title = "Strategy basket") {
        Text("2–4 distinct opening legs, same index and expiry. Options are buy-only. Drafts survive rotation, but are discarded when the app process ends or you sign out.")
        if (d.basket.isEmpty()) Text("Add contracts from an order ticket to examine their combined expiry outcome.")
        d.basket.forEachIndexed { i, leg ->
            ContractDetails(leg.contract, advanced)
            Text(actionLabel(leg.action))
            LotInput("Leg ${i + 1} lots", leg.lots, editable) { lots -> basket(d.basket.mapIndexed { j, l -> if (i == j) l.copy(lots = lots) else l }) }
            TextButton({ basket(d.basket.filterIndexed { j, _ -> i != j }) }, enabled = editable) { Text("Remove leg ${i + 1}") }
        }
        Button({ review(true) }, enabled = editable && d.basket.size in 2..4 && d.basket.all { it.valid }) { Text("Review basket risk") }
    }
    d.review?.let { r -> DerivativeRiskReview(r, d.signature(d.basketReview), d.basketReview, !editable, acknowledge) { submit(false) } }
    Panel(title = "Virtual positions and wallet") {
        val p = d.portfolio
        ValueRow("Available cash", money(p?.decimal("cash"), "INR")); ValueRow("Reserved margin", money(p?.decimal("reservedMargin"), "INR"))
        ValueRow("Realized P&L", money(p?.decimal("realizedPnl"), "INR")); ValueRow("Marked equity", money(p?.decimal("totalEquity"), "INR"))
        if (p?.objects("positions").isNullOrEmpty()) Text("No open positions in the loaded snapshot.")
        p?.objects("positions")?.forEach { position ->
            val q = position.getJSONObject("market"); val c = q.getJSONObject("contract")
            HorizontalDivider(); ContractDetails(c, advanced)
            Text("${position.text("direction").lowercase()} · ${position.optInt("lots")} lots")
            ValueRow("Average entry", money(position.decimal("averageEntry"), "INR")); ValueRow("Unrealized P&L", money(position.decimal("unrealizedPnl"), "INR"))
            if (position.optBoolean("awaitingSettlement")) Text("Expiry settlement pending verified index-close data.")
            else OutlinedButton({ ticket(DerivativeLeg(c, "CLOSE", position.optInt("lots").coerceAtMost(100))) }, enabled = editable && q.optBoolean("fresh")) { Text("Review close ${c.text("symbol")}") }
        }
        Text("Recent fills")
        p?.objects("fills")?.forEach { fill ->
            HorizontalDivider(); Text("${fill.text("symbol")} · ${actionLabel(fill.text("action"))} · ${fill.optInt("lots")} lots")
            Text("${money(fill.decimal("price"), "INR")} · ${time(fill.text("filledAt"))}")
            if (fill.text("basketId").isNotBlank()) Text("Basket ${fill.text("basketId").take(8)}")
        }
    }
}

private fun actionLabel(action: String) = when (action) { "OPEN_LONG" -> "Open long"; "OPEN_SHORT" -> "Open short"; "CLOSE" -> "Close position"; "EXPIRY" -> "Expiry settlement"; else -> "Virtual fill" }
@Composable private fun ContractDetails(c: JSONObject, exact: Boolean) {
    Text(c.text("symbol"), style = MaterialTheme.typography.titleSmall)
    Text("NSE · ${c.text("kind")} · Expiry ${c.text("expiry")} · Lot ${c.optInt("lotSize")}")
    if (c.decimal("strike") != null) Text("Strike ${number(c.decimal("strike"))}")
    if (exact) Text("Exact contract: ${c.text("providerKey")}")
}
@Composable private fun LotInput(label: String, lots: Int, enabled: Boolean, change: (Int) -> Unit) {
    OutlinedTextField(if (lots == 0) "" else lots.toString(), { change(it.toIntOrNull() ?: 0) }, enabled = enabled,
        label = { Text(label) }, supportingText = { Text("1–100 whole lots") }, isError = lots !in 1..100,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}
@Composable internal fun DerivativeRiskReview(r: DerivativeReview, signature: String, basket: Boolean, working: Boolean,
    acknowledge: (Boolean) -> Unit, submit: () -> Unit) {
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(r) { while (true) { now = android.os.SystemClock.elapsedRealtime(); delay(500) } }
    val valid = r.valid(signature, now)
    val v = r.result
    Panel(title = if (basket) "Combined expiry outcome" else "Review virtual order") {
        val legs = if (basket) v.objects("legs") else listOf(v)
        legs.forEach { leg ->
            ContractDetails(leg.getJSONObject("contract"), true)
            Text("${actionLabel(leg.text("action"))} · ${leg.optInt("lots")} lots · ${leg.optLong("quantity")} units")
            ValueRow("Estimated price / premium", money(leg.decimal("price"), "INR"))
            Text("Quote ${time(leg.text("quoteAt"))}")
        }
        ValueRow("Required virtual funds", money(v.decimal("requiredFunds"), "INR"))
        ValueRow("Cash after fill", money(v.decimal("cashAfter"), "INR"))
        val payoff = if (basket) v.getJSONObject("payoff") else v
        ValueRow("Maximum expiry loss", if (payoff.optBoolean("unlimitedLoss")) "Unbounded" else money(payoff.decimal("maximumLoss"), "INR"))
        if (basket) {
            ValueRow("Maximum expiry profit", if (payoff.optBoolean("unlimitedProfit")) "Unbounded" else money(payoff.decimal("maximumProfit"), "INR"))
            val roots = payoff.optJSONArray("breakevens")
            Text("Approximate breakeven index levels: " + (if (roots == null || roots.length() == 0) "None" else (0 until roots.length()).joinToString { roots.get(it).toString() }))
            payoff.objects("zeroRanges").forEach { Text("Zero P&L from ${it.text("from")} to ${it.text("to").ifBlank { "infinity" }}") }
            ExpiryPayoff(payoff.objects("points"))
        } else {
            ValueRow("Expiry breakeven", number(v.decimal("breakeven")))
            ValueRow("Cash change", money(v.decimal("cashChange"), "INR"))
            if (v.text("action") == "CLOSE") ValueRow("Realized P&L on close", money(v.decimal("realizedPnl"), "INR"))
        }
        Text("Calculated expiry outcomes, not forecasts. New opening legs only, held to the same expiry with a nonnegative index. Closing orders do not have opening-leg payoff bounds.")
        Text("Simulated fees ₹0; taxes and additional slippage excluded. Futures reserve 20% margin per leg, with no hedge discount, daily cash mark-to-market or forced liquidation. Loss can exceed margin. Options are buy-only.")
        Text("Quotes can change after review. Execution rechecks data, session, positions and funds. Baskets commit all legs or none; quotes may have different observation times. Positions merge by contract and close individually.")
        Text("Expiry uses verified index-close data; missing data delays per-position cash settlement. No physical delivery or early exercise. This is simulated practice, not real trading.")
        Row { Checkbox(r.accepted, acknowledge, enabled = !working && valid); Text("I understand the risk and simulation assumptions.", Modifier.weight(1f)) }
        if (!valid) Text("Review expired or changed. Request a fresh risk review.")
        Button(submit, enabled = !working && valid && r.accepted) { Text(if (basket) "Place virtual basket" else "Place virtual order") }
    }
}
@Composable private fun ExpiryPayoff(points: List<JSONObject>) {
    val values = points.mapNotNull { p -> val x = p.decimal("underlying")?.toFloat(); val y = p.decimal("pnl")?.toFloat(); if (x == null || y == null) null else x to y }
    val color = MaterialTheme.colorScheme.primary
    val zeroColor = MaterialTheme.colorScheme.outline
    if (values.size >= 2) {
        val maxX = values.maxOf { it.first }.coerceAtLeast(1f)
        val minY = minOf(0f, values.minOf { it.second }); val maxY = maxOf(1f, values.maxOf { it.second })
        Text("Expiry P&L (INR) versus underlying index")
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "Calculated expiry payoff. Exact index and profit or loss values follow." }) {
            fun xy(p: Pair<Float, Float>) = Offset(p.first / maxX * size.width, size.height - (p.second - minY) / (maxY - minY) * size.height)
            drawLine(zeroColor, xy(0f to 0f), xy(maxX to 0f))
            val path = Path(); values.forEachIndexed { i, p -> val q = xy(p); if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y) }; drawPath(path, color, style = Stroke(3.dp.toPx()))
        }
        Text("Chart index range: 0–$maxX. Bounds also account for outcomes beyond this range.")
    }
    Text("Payoff values · Calculated scenarios")
    points.forEach { ValueRow("Index ${number(it.decimal("underlying"))}", money(it.decimal("pnl"), "INR")) }
}
