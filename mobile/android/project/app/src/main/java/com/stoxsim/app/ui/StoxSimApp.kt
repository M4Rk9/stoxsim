package com.stoxsim.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.User
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable internal fun StoxSimApp(model: AppViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Home") }
    LaunchedEffect(state.user?.email) { tab = "Home" }
    BackHandler(state.user != null && tab != "Home") { tab = "Home" }
    StoxSimTheme {
        Scaffold(bottomBar = {
            if (state.user != null) NavigationBar {
                listOf("Home" to Icons.Default.Home, "Markets" to Icons.Default.Search,
                    "Portfolio" to Icons.Default.List, "Account" to Icons.Default.Person).forEach { (name, icon) ->
                    NavigationBarItem(selected = tab == name, onClick = { tab = name },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(name) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("StoxSim", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                when {
                    state.restoring -> {
                        CircularProgressIndicator()
                        Text("Connecting to your account…")
                    }
                    state.restoreFailed -> {
                        Message(state.error)
                        Button(onClick = model::restore, enabled = !state.working) { Text("Retry connection") }
                        Text("Your session is kept securely while you reconnect.")
                    }
                    state.user == null -> AuthScreen(state, model::authenticate, model::forgot, model::clearMessage)
                    else -> {
                        Message(state.error, state.notice)
                        if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                        when (tab) {
                            "Home" -> HomeScreen(state.user!!, state.working, model::refreshUser)
                            "Markets" -> MarketScreen(state, model)
                            "Portfolio" -> PortfolioScreen(state, model)
                            "Account" -> AccountScreen(state.user!!, state.working, model::refreshUser, model::logout)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Message(error: String?, notice: String? = null) {
    if (error != null || notice != null) Surface(
        color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
    ) { Text(error ?: notice.orEmpty(), Modifier.padding(16.dp)) }
}

@Composable internal fun AuthScreen(
    state: AppState, authenticate: (String, String, String?) -> Unit,
    forgot: (String) -> Unit, clearMessage: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Never put a password in saved instance state / SavedStateHandle.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val register = mode == "register"
    val recovering = mode == "forgot"
    BackHandler(mode != "login" && !state.working) { mode = "login"; password = ""; clearMessage() }
    Text(when (mode) { "register" -> "Start practising."; "forgot" -> "Reset your password."; else -> "Welcome back." },
        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("Build your investing skills with virtual money.", style = MaterialTheme.typography.bodyLarge)
    Message(state.error, state.notice)
    if (register) OutlinedTextField(name, { name = it }, label = { Text("Display name") },
        singleLine = true, enabled = !state.working, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(email, { email = it }, label = { Text("Email address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true,
        enabled = !state.working, modifier = Modifier.fillMaxWidth())
    if (!recovering) OutlinedTextField(password, { password = it }, label = { Text("Password") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
        supportingText = if (register) { { Text("8–72 characters, at most 72 UTF-8 bytes.") } } else null,
        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } },
        enabled = !state.working, modifier = Modifier.fillMaxWidth())
    if (register) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, { accepted = it }, enabled = !state.working)
            Text("I accept the Terms and Privacy Notice.", Modifier.weight(1f))
        }
        Row {
            TextButton(onClick = { uri.openUri("https://stoxsim.com/terms") }) { Text("Read Terms ↗") }
            TextButton(onClick = { uri.openUri("https://stoxsim.com/privacy") }) { Text("Read Privacy ↗") }
        }
    }
    val validEmail = email.trim().contains('@') && email.trim().length <= 320
    val validPassword = password.isNotBlank() && password.length <= 72 && password.toByteArray(Charsets.UTF_8).size <= 72
    val enabled = !state.working && validEmail && (recovering || (validPassword && (!register || (password.length >= 8 && name.trim().length in 2..100 && accepted))))
    Button(onClick = {
        if (recovering) forgot(email) else { authenticate(email, password, if (register) name else null); password = "" }
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (state.working) "Please wait…" else when (mode) { "register" -> "Create account"; "forgot" -> "Send reset link"; else -> "Sign in" })
    }
    if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = { mode = if (mode == "login") "register" else "login"; password = ""; accepted = false; clearMessage() }, enabled = !state.working) {
        Text(if (mode == "login") "Create a StoxSim account" else "Back to sign in")
    }
    if (mode == "login") TextButton(onClick = { mode = "forgot"; password = ""; clearMessage() }, enabled = !state.working) { Text("Forgot password?") }
    Text("Educational simulator • No real-money trading", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun HomeScreen(user: User, working: Boolean, refresh: () -> Unit) {
    Text("Hello, ${user.displayName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Your practice accounts", style = MaterialTheme.typography.titleMedium)
    user.accounts.filter { it.active }.forEach { account ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(account.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${regionLabel(account.region)} • ${if (account.kind == "SANDBOX") "Sandbox" else "Standard"}")
                Text(money(account.cash, account.currency), style = MaterialTheme.typography.headlineMedium)
                Text("Available virtual cash", style = MaterialTheme.typography.bodySmall)
                if (account.blocked.signum() != 0) Text("Reserved: ${money(account.blocked, account.currency)}")
            }
        }
    }
    if (user.accounts.none { it.active }) Text("Your account has no active practice accounts yet.")
    OutlinedButton(onClick = refresh, enabled = !working) { Text("Refresh balances") }
    Text("Explore stocks in Markets and check your holdings in Portfolio. Prices and balances are fetched online.")
}

@Composable private fun RegionSelector(state: AppState, change: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("INDIA", "UNITED_STATES").forEach { region ->
            FilterChip(selected = state.region == region, onClick = { change(region) },
                label = { Text(regionLabel(region)) }, enabled = !state.working)
        }
    }
}

@Composable private fun MarketScreen(state: AppState, model: AppViewModel) {
    var query by rememberSaveable(state.region) { mutableStateOf("") }
    Text("Markets", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    RegionSelector(state, model::changeRegion)
    OutlinedTextField(query, { query = it }, label = { Text("Search symbol or company") }, singleLine = true,
        enabled = !state.working, modifier = Modifier.fillMaxWidth())
    Button(onClick = { model.search(query) }, enabled = !state.working && query.trim().length in 2..160) { Text("Search stocks") }
    if (state.searched && state.instruments.isEmpty()) Text("No stocks match this search.")
    state.instruments.forEach { instrument ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(instrument.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(instrument.name)
                Text(instrument.exchange, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Text("Stock charts, watchlists and trading will be added in the next native update.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun PortfolioScreen(state: AppState, model: AppViewModel) {
    LaunchedEffect(state.region) { if (state.portfolio == null && !state.working) model.loadPortfolio() }
    Text("Portfolio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    RegionSelector(state, model::changeRegion)
    Text("Standard practice account • Virtual money", style = MaterialTheme.typography.bodySmall)
    state.portfolio?.let { portfolio ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Total account value")
                Text(money(portfolio.total, portfolio.currency), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Total P&L: ${money(portfolio.profitLoss, portfolio.currency)}")
                Text("Available cash: ${money(portfolio.cash, portfolio.currency)}")
                Text("Invested: ${money(portfolio.invested, portfolio.currency)}")
            }
        }
        Text("Pricing: ${portfolio.status.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall)
        Text("Valued at: ${portfolio.valuedAt}", style = MaterialTheme.typography.bodySmall)
        Text("Holdings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (portfolio.positions.isEmpty()) Text("You have no holdings in this market yet.")
        portfolio.positions.forEach { position ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(position.symbol, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${position.name} • ${position.quantity} shares")
                    Text(money(position.value, portfolio.currency))
                    Text("Pricing: ${position.status.lowercase()}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    OutlinedButton(onClick = model::loadPortfolio, enabled = !state.working) { Text("Refresh portfolio") }
}

@Composable private fun AccountScreen(user: User, working: Boolean, refresh: () -> Unit, logout: () -> Unit) {
    val uri = LocalUriHandler.current
    Text("Your account", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text(user.displayName, style = MaterialTheme.typography.titleLarge)
    Text(user.email)
    Text(if (user.verified) "Email verified" else "Email not verified. Open your verification email, then refresh.")
    OutlinedButton(onClick = refresh, enabled = !working) { Text("Refresh account") }
    HorizontalDivider()
    TextButton(onClick = { uri.openUri("https://stoxsim.com/privacy") }) { Text("Privacy Notice ↗") }
    TextButton(onClick = { uri.openUri("https://stoxsim.com/delete-account") }) { Text("Account deletion help ↗") }
    Text("These help pages open in your browser. Native account settings and subscriptions are coming in a later update.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = logout, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
}

private fun regionLabel(region: String) = if (region == "INDIA") "India" else "United States"
private fun money(value: BigDecimal, currency: String): String = NumberFormat.getCurrencyInstance(
    if (currency == "INR") Locale.forLanguageTag("en-IN") else Locale.US
).apply { this.currency = Currency.getInstance(currency) }.format(value)
