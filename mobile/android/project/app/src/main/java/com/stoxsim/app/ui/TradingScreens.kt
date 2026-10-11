package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

@Composable internal fun AccountSelector(state: AppState, changeRegion: (String) -> Unit, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
            Text(state.account?.let { "${it.label}${if (!it.active) " · Locked" else ""} ▾" } ?: "Select a practice account")
        }
        DropdownMenu(expanded, { expanded = false }) {
            state.user?.accounts?.filter { it.region == state.region }?.forEach { account ->
                DropdownMenuItem(text = { Text("${account.label} · ${account.kind.lowercase()}${if (!account.active) " · Locked" else ""}") },
                    onClick = { expanded = false; select(account.id) })
            }
        }
    }
    if (state.account?.active == false) Message(null, "This sandbox is locked. History remains visible; new orders require an active entitlement.")
}

@Composable internal fun PortfolioMetrics(state: AppState) {
    val p = state.portfolio
    Panel(kicker = "${state.account?.kind ?: "STANDARD"} · ${regionLabel(state.region)} PORTFOLIO") {
        Text(money(p?.total, state.currency), style = MaterialTheme.typography.headlineLarge)
        Muted("Account value · Started with ${money(p?.capital ?: state.account?.capital, state.currency)}")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ValueRow("Available cash", money(p?.cash ?: state.account?.cash, state.currency))
        ValueRow("Reserved cash", money(p?.blocked ?: state.account?.blocked, state.currency))
        ValueRow("Unrealized P/L", money(p?.unrealized, state.currency), p?.unrealized)
        ValueRow("Total return", percent(p?.returnPercent), p?.returnPercent)
        ValueRow("All-time P/L", money(p?.profitLoss, state.currency), p?.profitLoss)
        p?.let { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Status(it.status); Muted("Valued ${time(it.valuedAt)}") } }
        SectionError(state, "portfolio")
    }
}

@Composable internal fun DashboardScreen(state: AppState, model: AppViewModel) {
    StocksExplore(state, model::openScreen, model::selectStock)
    val onboarding = state.data["onboarding"]
    if (onboarding != null && !onboarding.optBoolean("completed") && !onboarding.optBoolean("dismissed")) Panel("START HERE", "Your first practice portfolio") {
        Text("Choose a stock, explore its chart, and place a simulated order with virtual money.")
        if (!onboarding.optBoolean("introductionCompleted")) Button({ model.onboarding("introduction/complete") }, enabled = !state.working) { Text("Start practising") }
        else Button({ model.openScreen("Markets") }) { Text("Find your first stock") }
        TextButton({ model.onboarding("dismiss") }, enabled = !state.working) { Text("Dismiss introduction") }
    }
}
@Composable internal fun StocksExplore(state: AppState, navigate: (String) -> Unit, select: (Instrument) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Stocks", style = MaterialTheme.typography.headlineMedium)
        TextButton({ navigate("Account") }) { Text("${if (state.region == "INDIA") "IND" else "US"} · ${state.currency} ▾") }
    }
    Indices(state)
    val recent = state.recentStocks.filter { it.region == state.region }
    if (recent.isNotEmpty()) {
        Text("Recently viewed", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            recent.forEach { stock -> OutlinedCard(onClick = { select(stock) }, modifier = Modifier.width(105.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stock.symbol.take(2), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stock.symbol, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                    Muted(stock.exchange)
                }
            } }
        }
    }
    StockMoverGrid(state, select)
    Panel(title = "Your investments") {
        Text(money(state.portfolio?.total, state.currency), style = MaterialTheme.typography.headlineMedium)
        ValueRow("Total return", percent(state.portfolio?.returnPercent), state.portfolio?.returnPercent)
        Muted("${state.account?.label.orEmpty()} · ${money(state.portfolio?.cash ?: state.account?.cash, state.currency)} available")
        SectionError(state, "portfolio")
        TextButton({ navigate("Portfolio") }) { Text("View holdings & analysis →") }
    }
    Text("Products & tools", style = MaterialTheme.typography.titleLarge)
    listOf("Watchlist" to "Follow your favourite stocks", "Competitions" to "Practise with other learners", "Campus" to "Join your institution").chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (route, description) -> OutlinedCard(onClick = { navigate(route) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(route, style = MaterialTheme.typography.titleMedium); Muted(description); Text("Explore →", color = MaterialTheme.colorScheme.primary) }
            } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    TextButton({ navigate("Wallet") }) { Text("View practice wallet →") }
}
@Composable private fun StockMoverGrid(state: AppState, select: (Instrument) -> Unit) {
    var category by rememberSaveable(state.region) { mutableStateOf("gainers") }
    var all by rememberSaveable(state.region, category) { mutableStateOf(false) }
    val data = state.data["movers"]
    Text("Market movers", style = MaterialTheme.typography.titleLarge)
    ChoiceRow(listOf("gainers" to "Gainers", "losers" to "Losers"), category) { category = it }
    SectionError(state, "movers")
    val items = data?.objects(category).orEmpty()
    if (items.isEmpty()) Muted(if (state.working) "Loading market snapshot…" else "No movers available for this snapshot.")
    (if (all) items else items.take(4)).chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { item -> OutlinedCard(onClick = { select(Instrument(item.text("symbol"), item.text("name"), item.text("exchange"), state.region, currency = state.currency, key = item.text("instrumentKey"))) },
                modifier = Modifier.weight(1f).fillMaxHeight(), enabled = !state.working,
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.text("symbol").take(2), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(item.text("name").ifBlank { item.text("symbol") }, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Muted(item.text("symbol"))
                    Text(money(item.decimal("lastPrice"), state.currency), fontWeight = FontWeight.Bold)
                    Text(percent(item.decimal("changePercent")), color = item.decimal("changePercent")?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (items.size > 4) TextButton({ all = !all }) { Text(if (all) "Show less" else "See all movers →") }
    data?.let { Muted("${it.text("dataStatus")} · ${time(it.text("generatedAt"))}") }
}
@Composable private fun MarketBanner(state: AppState) {
    val m = state.data["market"]
    Panel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (state.region == "INDIA") "NSE" else "NASDAQ", fontWeight = FontWeight.Bold)
            Status(m?.text("phase") ?: "UNAVAILABLE")
        }
        Muted(m?.text("timezone") ?: if (state.region == "INDIA") "Asia/Kolkata" else "America/New_York")
        ValueRow("Next transition", time(m?.text("nextTransition").orEmpty()))
        Muted("Stream: ${state.streamStatus.lowercase()} · Quote status and timestamps show price freshness.")
        SectionError(state, "market")
    }
}
@Composable private fun Indices(state: AppState) {
    SectionError(state, "indices")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.lists["indices"].orEmpty().forEach { index -> OutlinedCard(modifier = Modifier.width(170.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(index.text("label"), style = MaterialTheme.typography.labelLarge)
                Text(number(index.decimal("value")), fontWeight = FontWeight.Bold)
                Text(percent(index.decimal("changePercent")), style = MaterialTheme.typography.bodySmall, color = index.decimal("changePercent")?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                Muted("${index.text("dataStatus")} · ${time(index.text("exchangeTimestamp"))}")
            }
        } }
    }
}
@Composable internal fun Movers(state: AppState, select: (Instrument) -> Unit) {
    var category by rememberSaveable { mutableStateOf("gainers") }
    Panel("MARKET OVERVIEW", "Top movers") {
        ChoiceRow(listOf("gainers" to "Gainers", "losers" to "Losers"), category) { category = it }
        val movers = state.data["movers"]
        SectionError(state, "movers")
        if (movers != null) { Status(movers.text("dataStatus")); Muted("${movers.text("universe").replace('_', ' ')} · ${time(movers.text("generatedAt"))}") }
        val items = movers?.objects(category).orEmpty()
        if (items.isEmpty()) Muted(if (state.working) "Loading market snapshot…" else "No movers available for this snapshot.")
        items.forEach { item ->
            TextButton({ select(Instrument(item.text("symbol"), item.text("name"), item.text("exchange"), state.region, currency = state.currency, key = item.text("instrumentKey"))) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.text("symbol"), fontWeight = FontWeight.Bold); Muted(item.text("name")) }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(item.decimal("lastPrice"), state.currency), color = MaterialTheme.colorScheme.onSurface)
                        Text(percent(item.decimal("changePercent")), color = item.decimal("changePercent")?.let { tone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
@Composable internal fun MarketsScreen(state: AppState, model: AppViewModel) {
    StockResults(state, model::selectStock)
    if (!state.searched) { Indices(state); Movers(state, model::selectStock) }
}
@Composable internal fun StockResults(state: AppState, select: (Instrument) -> Unit) {
    Panel(title = if (state.searched) "Results for ${state.searchQuery}" else "Search stocks & ETFs") {
        Muted("${regionLabel(state.region)} · ${state.currency} · Use the search bar above")
        if (!state.searched) Muted("Search by symbol or company name.")
        if (state.searched && state.instruments.isEmpty()) Muted("No instruments match your search.")
        state.instruments.forEach { instrument ->
            TextButton({ select(instrument) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(instrument.symbol, fontWeight = FontWeight.Bold); Muted("${instrument.name} · ${instrument.exchange}") }
                    Text("→")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
@Composable internal fun WatchlistScreen(state: AppState, model: AppViewModel) {
    AccountSelector(state, model::changeRegion, model::selectAccount)
    WatchlistPanel(state, model::selectStock, model::removeWatch)
    OutlinedButton({ model.openScreen("Markets") }) { Text("Find stocks to watch") }
}
@Composable internal fun WatchlistPanel(state: AppState, select: (Instrument) -> Unit, remove: (String) -> Unit) {
    val watchlist = state.data["watchlist"]
    Panel("LIVE LIST", watchlist?.text("name") ?: "My Watchlist") {
        SectionError(state, "watchlist")
        val items = watchlist?.objects("items").orEmpty().filter { it.text("marketRegion") == state.region }
        if (items.isEmpty()) Muted("Your watchlist is empty for this market. Add stocks from their detail screen.")
        items.forEach { item ->
            TextButton({ select(Instrument(item.text("symbol"), item.text("name"), item.text("exchange"), state.region, item.text("instrumentId"), item.text("currency"), item.text("instrumentKey"))) }, enabled = !state.working) {
                Column(Modifier.fillMaxWidth()) { Text(item.text("symbol"), fontWeight = FontWeight.Bold); Muted(item.text("name")) }
            }
            ValueRow(money(item.decimal("lastPrice"), item.text("currency")), percent(item.decimal("changePercent")), item.decimal("changePercent"))
            Row(verticalAlignment = Alignment.CenterVertically) { Status(item.text("dataStatus")); Spacer(Modifier.weight(1f)); TextButton({ remove(item.text("itemId")) }, enabled = !state.working) { Text("Remove") } }
            Muted(time(item.text("exchangeTimestamp")))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
@Composable internal fun StockScreen(state: AppState, model: AppViewModel) {
    val instrument = state.selected ?: return
    val quote = state.quote
    val delta = if (quote?.price != null && quote.previous != null && quote.previous.signum() > 0)
        quote.price.subtract(quote.previous).multiply(BigDecimal(100)).divide(quote.previous, 4, RoundingMode.HALF_UP) else null
    Panel("${instrument.exchange} · ${regionLabel(instrument.region)}", instrument.symbol) {
        Text(instrument.name)
        Text(money(quote?.price, instrument.currency), style = MaterialTheme.typography.headlineLarge)
        if (delta != null) Text(percent(delta), color = tone(delta))
        Status(quote?.status ?: "UNAVAILABLE"); Muted("Quote ${time(quote?.at.orEmpty())}")
        SectionError(state, "quote")
        val watched = state.data["watchlist"]?.objects("items")?.any { it.text("symbol") == instrument.symbol && it.text("exchange") == instrument.exchange } == true
        OutlinedButton({ model.watch(instrument) }, enabled = !state.working && !watched && instrument.id.isNotBlank()) { Text(if (watched) "★ Watching" else "☆ Add to watchlist") }
        if (instrument.id.isBlank() && !watched) Muted("Search this symbol in Markets to add it to your watchlist.")
        ValueRow("Open", money(quote?.open, instrument.currency)); ValueRow("Day high / low", "${money(quote?.high, instrument.currency)} / ${money(quote?.low, instrument.currency)}")
        ValueRow("Previous close", money(quote?.previous, instrument.currency)); ValueRow("Volume", quote?.volume?.toString() ?: "—")
    }
    Panel("PRICE HISTORY", "Historical prices") {
        ChoiceRow(listOf("1" to "1M", "3" to "3M", "6" to "6M", "12" to "1Y", "36" to "3Y", "60" to "5Y"), state.chartMonths.toString(), !state.working) { model.chartRange(it.toLong()) }
        SectionError(state, "chart")
        LineChart(state.candles.map { it.close }, state.candles.map { time(it.at) }, "Daily closing price for ${instrument.symbol}")
        Muted("Daily closing prices · ${instrument.currency}")
    }
    OrderTicket(state, model::estimate, model::placeOrder)
    Panel("STUDY THE BUSINESS", "Company fundamentals") {
        ChoiceRow(listOf("quarterly" to "Quarterly", "yearly" to "Yearly"), state.insightPeriod, !state.working, model::financialPeriod)
        SectionError(state, "stockInsights")
        val insights = state.data["stockInsights"]
        if (insights == null && !state.working) Muted("Company fundamentals are temporarily unavailable.")
        insights?.let { data ->
            Status(data.text("status")); Muted("As of ${time(data.text("asOf"))}")
            if (data.text("message").isNotBlank()) Text(data.text("message"))
            data.optJSONObject("profile")?.let { profile ->
                Text(profile.text("description")); ValueRow("Sector", profile.text("sector").ifBlank { "—" })
            }
            data.objects("ratios").forEach { ValueRow(it.text("name"), it.text("companyValue").ifBlank { "—" }) }
            data.optJSONObject("financials")?.let { financials ->
                Muted("${financials.text("statementType")} · ${financials.text("unitsIn")}")
                financials.objects("metrics").forEach { metric ->
                    Text(metric.text("category"), style = MaterialTheme.typography.titleMedium)
                    metric.objects("history").forEach { ValueRow(it.text("period"), number(it.decimal("value"))) }
                }
            }
        }
    }
}

@Composable internal fun OrderTicket(state: AppState, estimate: (OrderDraft) -> Unit, place: (OrderDraft) -> Unit) {
    val instrument = state.selected ?: return
    var side by rememberSaveable(instrument.symbol, state.accountId) { mutableStateOf("BUY") }
    var type by rememberSaveable(instrument.symbol, state.accountId) { mutableStateOf("MARKET") }
    var quantity by rememberSaveable(instrument.symbol, state.accountId) { mutableStateOf("1") }
    var limit by rememberSaveable(instrument.symbol, state.accountId) { mutableStateOf("") }
    var review by remember { mutableStateOf<OrderDraft?>(null) }
    val qty = quantity.toLongOrNull()
    val price = limit.toBigDecimalOrNull()
    val valid = qty != null && qty > 0 && state.account?.active == true && state.accountId.isNotBlank() &&
        (type != "LIMIT" || price != null && price >= BigDecimal("0.0001"))
    val draft = if (valid) OrderDraft(state.accountId, instrument, side, type, qty!!, if (type == "LIMIT") price else null) else null
    var estimateFor by remember { mutableStateOf<OrderDraft?>(null) }
    Panel("PAPER ORDER", "Place a practice order") {
        Muted("${state.account?.label ?: "No practice account"} · SIMULATED · ${instrument.currency}")
        ChoiceRow(listOf("BUY" to "Buy", "SELL" to "Sell"), side, !state.working) { side = it }
        ChoiceRow(listOf("MARKET" to "Market", "LIMIT" to "Limit"), type, !state.working) { type = it }
        OutlinedTextField(quantity, { quantity = it.take(12) }, label = { Text("Quantity (whole shares)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), enabled = !state.working)
        if (type == "LIMIT") OutlinedTextField(limit, { limit = it.take(20) }, label = { Text("Limit price (${instrument.currency})") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !state.working)
        OutlinedButton({ draft?.let { estimateFor = it; estimate(it) } }, enabled = draft != null && !state.working && (type == "LIMIT" || state.quote?.price != null)) { Text("Estimate simulated charges") }
        if (estimateFor == draft) state.data["charges"]?.let { charges ->
            ValueRow("Turnover", money(charges.decimal("turnover"), instrument.currency))
            listOf("brokerage" to "Brokerage", "stt" to "STT", "exchangeCharges" to "Exchange", "gst" to "GST", "sebiCharges" to "SEBI", "stampDuty" to "Stamp duty", "dpCharges" to "DP").forEach { (key, label) -> ValueRow(label, money(charges.decimal(key), instrument.currency)) }
            ValueRow("Total charges", money(charges.decimal("totalCharges"), instrument.currency)); Muted("Simulated · ${charges.text("scheduleVersion")}")
        }
        Button({ review = draft }, enabled = draft != null && !state.working,
            colors = ButtonDefaults.buttonColors(containerColor = if (side == "BUY") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Review ${side.lowercase()} order") }
        Muted("Market orders may queue outside regular hours. Charges and final execution price are determined by the server.")
        state.data["orderResult"]?.takeIf { it.text("symbol") == instrument.symbol }?.let { result ->
            Status(result.text("status")); Text(result.text("rejectionReason"))
            result.optJSONObject("finwizFeedback")?.let { feedback -> Text(feedback.text("headline")); feedback.strings("observations").forEach { Text(it) }; Muted(feedback.text("disclaimer")) }
        }
    }
    review?.let { order -> AlertDialog(onDismissRequest = { review = null }, title = { Text("Confirm practice order") },
        text = { Text("${order.side} ${order.quantity} × ${order.instrument.symbol}\n${state.account?.label}\n${order.type}${order.limit?.let { " · ${money(it, instrument.currency)}" }.orEmpty()}\n\nVirtual money only. Check Orders after a connection interruption before retrying.") },
        confirmButton = { TextButton({ review = null; place(order) }, enabled = !state.working) { Text("Place practice order") } },
        dismissButton = { TextButton({ review = null }) { Text("Back") } }) }
}
@Composable internal fun OrdersScreen(state: AppState, model: AppViewModel) {
    AccountSelector(state, model::changeRegion, model::selectAccount)
    var cancel by remember { mutableStateOf<String?>(null) }
    Panel("PENDING & HISTORY", "Your orders") {
        SectionError(state, "orders")
        val orders = state.lists["orders"].orEmpty()
        if (orders.isEmpty()) Muted("No practice orders yet. Find a stock in Markets to get started.")
        orders.forEach { order ->
            Text("${order.text("side")} ${order.text("quantity")} × ${order.text("symbol")}", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Status(order.text("status")); Status(order.text("orderType")) }
            ValueRow("Limit / executed", "${money(order.decimal("limitPrice"), state.currency)} / ${money(order.decimal("executionPrice"), state.currency)}")
            Muted(time(order.text("createdAt")))
            if (order.text("rejectionReason").isNotBlank()) Text(order.text("rejectionReason"))
            if (order.text("status") == "OPEN") OutlinedButton({ cancel = order.text("id") }, enabled = !state.working) { Text("Cancel order") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    TradesPanel(state)
    Panel("CASH ACTIVITY", "Ledger") {
        SectionError(state, "ledger")
        if (state.lists["ledger"].isNullOrEmpty()) Muted("No ledger entries available.")
        state.lists["ledger"].orEmpty().forEach { entry ->
            Text(entry.text("description")); ValueRow(entry.text("direction"), money(entry.decimal("amount"), state.currency))
            ValueRow("Balance after", money(entry.decimal("balanceAfter"), state.currency)); Muted(time(entry.text("createdAt")))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    cancel?.let { id -> AlertDialog(onDismissRequest = { cancel = null }, title = { Text("Cancel this practice order?") },
        text = { Text("Reserved virtual cash or shares will be released if cancellation succeeds.") },
        confirmButton = { TextButton({ cancel = null; model.cancelOrder(id) }) { Text("Cancel order") } }, dismissButton = { TextButton({ cancel = null }) { Text("Keep order") } }) }
}
@Composable internal fun TradesPanel(state: AppState) {
    Panel("ACTIVITY", "Recent trades") {
        SectionError(state, "trades")
        if (state.lists["trades"].isNullOrEmpty()) Muted("Your executed trades will appear here.")
        state.lists["trades"].orEmpty().forEach { trade ->
            Text("${trade.text("side")} ${trade.text("quantity")} × ${trade.text("symbol")}", fontWeight = FontWeight.Bold)
            ValueRow("Execution price", money(trade.decimal("price"), state.currency))
            ValueRow("Charges", money(trade.decimal("charges"), state.currency)); ValueRow("Net cash effect", money(trade.decimal("netCashEffect"), state.currency))
            Muted("${time(trade.text("executedAt"))} · Simulated charges · ${trade.text("chargeScheduleVersion")}")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
