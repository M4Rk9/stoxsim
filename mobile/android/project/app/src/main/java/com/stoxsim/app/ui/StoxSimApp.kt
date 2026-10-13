package com.stoxsim.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.R

@Composable internal fun StoxSimApp(model: AppViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.foreground(true)
            if (event == Lifecycle.Event.ON_STOP) model.foreground(false)
        }
        lifecycle.addObserver(observer)
        model.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); model.foreground(false) }
    }
    val context = LocalContext.current
    var exportError by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(state.export.orEmpty().toByteArray(Charsets.UTF_8)) }
                ?: error("No output stream")
            model.exportSaved()
        } catch (_: Exception) { exportError = "Could not save the export. Choose another location and try again." }
    }
    BackHandler(state.user != null && state.screen != "Home") { model.openScreen(parentScreen(state.screen)) }
    StoxSimTheme(state.dark) {
        AppFrame(state, model::openScreen, model::refresh, model::search) {
            when {
                state.restoring -> { CircularProgressIndicator(); Text("Connecting to your account…") }
                state.restoreFailed -> { Message(state.error); Button(model::restore, enabled = !state.working) { Text("Retry connection") } }
                state.user == null -> {
                    Kicker("LEARN STOCK TRADING. RISK NOTHING.")
                    Panel { AuthScreen(state, model::authenticate, model::forgot, model::clearMessage) }
                }
                else -> {
                    Message(state.error, state.notice)
                    when (state.screen) {
                        "Home" -> DashboardScreen(state, model)
                        "Markets" -> MarketsScreen(state, model)
                        "Stock" -> StockScreen(state, model)
                        "Watchlist" -> WatchlistScreen(state, model)
                        "Portfolio" -> PortfolioScreen(state, model)
                        "Orders" -> OrdersScreen(state, model)
                        "Explore" -> ExploreScreen(model::openScreen)
                        "Scenario Lab" -> ScenarioScreen(state, model)
                        "Learning path" -> LearningScreen(state, model)
                        "Competitions" -> CompetitionScreen(state, model)
                        "Campus" -> CampusScreen(state, model)
                        "Finwiz" -> FinwizHub(state, model::openLesson, model::newChat, model::openChat, model::deleteChat, model::openScreen)
                        "Finwiz lesson" -> FinwizLesson(state, model::markLessonRead, { model.newChat(it) })
                        "Finwiz chat" -> FinwizChatScreen(state, model::askChat, { model.newChat() })
                        "Account" -> AccountHub(state, model::openScreen, model::changeRegion, model::selectAccount, model::setDark, model::logout)
                        "Profile & security" -> SettingsScreen(state, model)
                        "Subscriptions" -> SubscriptionScreen(state, model::createSandbox)
                        "Wallet" -> WalletScreen(state, model::selectAccount, model::openScreen)
                        "Weekly reports" -> ReportsScreen(state, model)
                    }
                    if (state.export != null) Panel(title = "Your account export") {
                        Text("Save your account data as a JSON file to a location you choose.")
                        Message(exportError)
                        Button({ export.launch("stoxsim-account-export.json") }) { Text("Save export") }
                    }
                }
            }
        }
    }
}

internal fun parentScreen(screen: String) = when (screen) {
    "Finwiz lesson", "Finwiz chat", "Learning path" -> "Finwiz"
    "Stock" -> "Markets"
    "Profile & security", "Subscriptions", "Wallet", "Weekly reports", "Campus" -> "Account"
    else -> "Home"
}
internal fun productTab(screen: String) = when (screen) {
    "Finwiz", "Finwiz lesson", "Finwiz chat", "Learning path" -> "Finwiz"
    "Scenario Lab" -> "Scenario Lab"
    "Competitions" -> "Competitions"
    else -> "Home"
}

@Composable internal fun AppFrame(state: AppState, navigate: (String) -> Unit, refresh: () -> Unit,
    search: (String) -> Unit = { navigate("Markets") }, content: @Composable ColumnScope.() -> Unit) {
    val signedIn = state.user != null
    val focus = LocalFocusManager.current
    var query by rememberSaveable(state.region, state.user?.email) { mutableStateOf("") }
    val stockPages = listOf("Home", "Markets", "Portfolio", "Orders", "Watchlist")
    val main = stockPages + listOf("Finwiz", "Scenario Lab", "Competitions")
    val chat = state.screen == "Finwiz chat"
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column(Modifier.statusBarsPadding().fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (signedIn) {
                    if (state.screen !in main) IconButton({ navigate(parentScreen(state.screen)) }, Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    OutlinedTextField(query, { query = it.take(100) }, placeholder = { Text("Search stocks & ETFs", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        singleLine = true, enabled = !state.working, shape = RoundedCornerShape(12.dp), textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Stock and ETF search" },
                        leadingIcon = { IconButton({ focus.clearFocus(); if (query.isBlank()) navigate("Markets") else search(query) }, enabled = !state.working) { Icon(Icons.Default.Search, "Search stocks and ETFs") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {
                            if (!state.working && query.isNotBlank()) { focus.clearFocus(); search(query) }
                        }))
                    Surface(onClick = { focus.clearFocus(); navigate("Account") }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp).semantics { contentDescription = "Account settings" }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Box(contentAlignment = Alignment.Center) { Text(state.user?.displayName?.trim()?.take(1)?.uppercase().orEmpty(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Image(painterResource(R.drawable.stoxsim_logo), null, Modifier.size(34.dp))
                    val brandColor = MaterialTheme.colorScheme.primary
                    Text(buildAnnotatedString { append("Stox"); withStyle(SpanStyle(color = brandColor)) { append("Sim") } }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (signedIn && state.screen in stockPages) StocksTabs(state.screen, navigate)
            else HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, bottomBar = {
        if (signedIn) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            val items = listOf(
                Triple("Home", "Stock", R.drawable.ic_nav_stock),
                Triple("Finwiz", "Finwiz AI", R.drawable.ic_nav_finwiz),
                Triple("Scenario Lab", "Scenario Lab", R.drawable.ic_nav_scenario),
                Triple("Competitions", "Competition", R.drawable.ic_nav_competition)
            )
            items.forEach { (route, label, icon) -> NavigationBarItem(selected = productTab(state.screen) == route,
                onClick = { focus.clearFocus(); navigate(route) }, modifier = Modifier.semantics { contentDescription = "Open $label" },
                icon = { Icon(painterResource(icon), null, Modifier.size(24.dp)) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val base = Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding()
            Column((if (chat) base.fillMaxSize() else base.verticalScroll(key(state.screen) { rememberScrollState() }))
                .padding(horizontal = 16.dp, vertical = if (chat) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (chat || state.screen in stockPages || state.screen == "Finwiz") 8.dp else 16.dp)) {
                if (signedIn && state.screen !in stockPages && !chat && state.screen != "Finwiz") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.screen == "Finwiz lesson") "Learn with Finwiz" else state.screen, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                        IconButton(refresh, enabled = !state.working) { Icon(Icons.Default.Refresh, "Refresh ${state.screen}") }
                    }
                }
                content()
                if (signedIn && !chat) Muted("Educational simulator • No real-money trading")
            }
        }
    }
}
@Composable internal fun StocksTabs(screen: String, navigate: (String) -> Unit) {
    val items = listOf("Home" to "Explore", "Portfolio" to "Holdings", "Orders" to "Orders", "Watchlist" to "Watchlist")
    val index = items.indexOfFirst { it.first == screen }.coerceAtLeast(0)
    TabRow(index, containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary) {
        items.forEach { (route, label) -> Tab(selected = route == items[index].first, onClick = { navigate(route) }) {
            Text(label, Modifier.padding(horizontal = 2.dp, vertical = 14.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        } }
    }
}
