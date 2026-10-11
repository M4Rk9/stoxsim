package com.stoxsim.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
    BackHandler(state.user != null && state.screen != "Home") { model.openScreen(if (state.screen == "Stock") "Markets" else "Home") }
    StoxSimTheme(state.dark) {
        AppFrame(state, model::openScreen, model::refresh) {
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
                        "Finwiz" -> FinwizScreen(state, model)
                        "Account" -> SettingsScreen(state, model)
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

@Composable internal fun AppFrame(state: AppState, navigate: (String) -> Unit, refresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val brandColor = MaterialTheme.colorScheme.primary
    val main = listOf("Home", "Markets", "Portfolio", "Orders", "Explore")
    val activeTab = when (state.screen) { "Stock", "Watchlist" -> "Markets"; in main -> state.screen; else -> "Explore" }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Column(Modifier.statusBarsPadding().fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.user != null && state.screen !in main) IconButton({ navigate(if (state.screen == "Stock") "Markets" else "Explore") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Image(painterResource(R.drawable.stoxsim_logo), null, Modifier.size(34.dp))
                Text(buildAnnotatedString { append("Stox"); withStyle(SpanStyle(color = brandColor)) { append("Sim") } }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (state.user != null) {
                    IconButton(refresh, enabled = !state.working) { Icon(Icons.Default.Refresh, "Refresh ${state.screen}") }
                    IconButton({ navigate("Account") }) { Icon(Icons.Default.Person, "Account settings") }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, bottomBar = {
        if (state.user != null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.AutoMirrored.Filled.List, Icons.Default.DateRange, Icons.Default.MoreVert)
            main.forEachIndexed { i, name -> NavigationBarItem(selected = activeTab == name, onClick = { navigate(name) },
                icon = { Icon(icons[i], null) }, label = { Text(name) }) }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().imePadding().verticalScroll(key(state.screen) { rememberScrollState() })
                .padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.user != null) Text(if (state.screen == "Home") "Dashboard" else state.screen, style = MaterialTheme.typography.headlineSmall)
                content()
                if (state.user != null) Muted("Educational simulator • No real-money trading")
            }
        }
    }
}
