package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*
import java.math.BigDecimal

@Composable internal fun ExploreScreen(navigate: (String) -> Unit) {
    Text("Build your investing skills.", style = MaterialTheme.typography.headlineMedium)
    listOf("Scenario Lab" to "Explore what could happen to your portfolio.",
        "Finwiz" to "Ask questions about markets, fundamentals and investing.",
        "Learning path" to "Your challenges, achievements and daily streak.",
        "Competitions" to "Join the season and practise with private leagues.",
        "Campus" to "Join your institution and practise in campus competitions.",
        "Watchlist" to "Follow stocks across India and the US.",
        "Weekly reports" to "Review your progress and email preferences.",
        "Account" to "Profile, appearance, security and plan benefits.").forEach { (name, description) ->
        Panel(title = name) { Text(description); OutlinedButton({ navigate(name) }, modifier = Modifier.fillMaxWidth()) { Text("Open $name →") } }
    }
}
@Composable internal fun ScenarioScreen(state: AppState, model: AppViewModel) {
    AccountSelector(state, model::changeRegion, model::selectAccount)
    Text("What if?", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
    Text("Your ideas. Your market. Explore what could happen.")
    var selected by rememberSaveable(state.accountId) { mutableStateOf("recovery") }
    var title by rememberSaveable(state.accountId) { mutableStateOf("My next big idea") }
    var input by rememberSaveable(state.accountId) { mutableStateOf("10, 25, 50") }
    val catalog = state.lists["scenarios"].orEmpty()
    val definition = catalog.find { it.text("id") == selected }
    val credits = state.data["credits"]
    val shocks = input.split(',').map { it.trim().toBigDecimalOrNull() }
    val valid = selected != "custom" || (title.trim().isNotEmpty() && title.length <= 80 && shocks.size in 1..12 && shocks.all { it != null && it >= BigDecimal("-100") && it <= BigDecimal("100") && it.stripTrailingZeros().scale() <= 2 })
    Panel("START WITH AN IDEA", "Scenario Lab") {
        SectionError(state, "scenarios"); SectionError(state, "credits")
        Text(credits?.let { "${it.optInt("remaining")} / ${it.optInt("allowance")} credits · ${it.text("plan")}" } ?: "Loading credits…", fontWeight = FontWeight.Bold)
        ChoiceRow(catalog.map { it.text("id") to it.text("title") }, selected, !state.working) { selected = it; model.clearData("scenarioResult") }
        if (selected == "custom") {
            OutlinedTextField(title, { title = it.take(80); model.clearData("scenarioResult") }, label = { Text("Scenario name") }, modifier = Modifier.fillMaxWidth(), enabled = !state.working)
            OutlinedTextField(input, { input = it.take(180); model.clearData("scenarioResult") }, label = { Text("Price changes (%) separated by commas") }, modifier = Modifier.fillMaxWidth(), enabled = !state.working)
            Muted("Use 1–12 moves between −100% and +100%, with at most two decimals. Each move is relative to today's price.")
        } else definition?.let { Text(it.text("lesson")); Text(it.optJSONArray("shocks")?.toString().orEmpty() + "% cumulative price moves") }
        Button({ definition?.let { model.scenario(it, title, shocks.filterNotNull()) } }, enabled = !state.working && definition != null && valid && (credits?.optInt("remaining") ?: 0) > 0 && state.accountId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Run scenario · 1 credit") }
        if (credits?.optInt("remaining") == 0) Muted("No credits remain in your current allowance.")
    }
    state.data["scenarioResult"]?.let { result -> Panel("YOUR WHAT-IF RESULT", result.optJSONObject("scenario")?.text("title").orEmpty()) {
        val projection = result.getJSONObject("projection")
        val steps = projection.objects("steps")
        Text(money(steps.lastOrNull()?.decimal("equity"), state.currency), style = MaterialTheme.typography.headlineLarge)
        ValueRow("Portfolio change", percent(projection.decimal("returnPercent")), projection.decimal("returnPercent"))
        LineChart(steps.map { it.decimal("equity") }, steps.map { "Step ${it.text("step")}" }, "Hypothetical portfolio value by scenario step")
        ValueRow("Starting value", money(result.decimal("startingEquity"), state.currency)); ValueRow("Cash held", money(result.decimal("cash"), state.currency))
        ValueRow("Largest drawdown", percent(projection.decimal("maximumDrawdownPercent")))
        Text("Step-by-step", style = MaterialTheme.typography.titleMedium)
        steps.forEach { ValueRow("${it.text("step")} · ${percent(it.decimal("shockPercent"))}", money(it.decimal("equity"), state.currency)) }
        result.objects("positions").forEach { ValueRow(it.text("symbol"), money(it.decimal("endingValue"), state.currency)); ValueRow("Change", money(it.decimal("change"), state.currency), it.decimal("change")) }
        result.strings("assumptions").forEach { Muted(it) }
    } }
}
@Composable internal fun LearningScreen(state: AppState, model: AppViewModel) {
    SectionError(state, "progression")
    val data = state.data["progression"]
    if (data == null) { Muted("Your learning path will appear when the connection is available."); return }
    Panel("YOUR LEARNING PATH", data.text("levelName")) {
        ValueRow("Level / XP", "${data.text("level")} / ${data.text("totalXp")}")
        ValueRow("Current / longest streak", "${data.text("currentStreak")} / ${data.text("longestStreak")} days")
        Button(model::checkIn, enabled = !state.working && !data.optBoolean("checkedInToday"), modifier = Modifier.fillMaxWidth()) { Text(if (data.optBoolean("checkedInToday")) "Checked in today" else "Daily check-in") }
        Muted("Check-in timezone: ${data.text("checkInZoneId")}")
    }
    data.objects("challenges").forEach { challenge -> Panel("${challenge.text("completedMissions")} / ${challenge.text("totalMissions")} MISSIONS", challenge.text("title")) {
        Text(challenge.text("description"))
        challenge.objects("missions").forEach { mission -> Text("${if (mission.optBoolean("completed")) "✓" else "○"} ${mission.text("title")}"); Muted(mission.text("description")) }
    } }
    Panel(title = "Achievements") { data.objects("achievements").forEach { Text("${if (it.optBoolean("unlocked")) "✓" else "○"} ${it.text("title")}", fontWeight = FontWeight.Bold); Muted(it.text("description")) }; Muted(data.text("disclaimer")) }
    if (state.data["onboarding"]?.optBoolean("dismissed") == true) OutlinedButton({ model.onboarding("resume") }, enabled = !state.working) { Text("Resume onboarding") }
}
@Composable internal fun CompetitionScreen(state: AppState, model: AppViewModel) {
    SectionError(state, "board")
    state.data["board"]?.let { data -> Panel("PRACTISE TOGETHER", data.optJSONObject("season")?.text("title")?.ifBlank { "Current season" } ?: "Current season") {
        ValueRow("Participants", data.text("participantCount")); ValueRow("Your rank", data.text("yourRank").ifBlank { "—" })
        Button(model::enroll, enabled = !state.working && !data.optBoolean("enrolled")) { Text(if (data.optBoolean("enrolled")) "Enrolled" else "Join this season") }
        Standings(data.objects("standings")); Text(data.text("comparisonNote")); Muted(data.text("disclaimer"))
    } }
    OutlinedButton({ model.openScreen("Campus") }) { Text("Campus competitions →") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Panel("PRIVATE LEAGUES", "Practise with your group") {
        SectionError(state, "leagues")
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("League name") }, modifier = Modifier.fillMaxWidth())
        Button({ model.createLeague(name) }, enabled = !state.working && name.trim().length in 3..80) { Text("Create league") }
        OutlinedTextField(code, { code = it.take(80) }, label = { Text("Invite code") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton({ model.joinLeague(code) }, enabled = !state.working && code.isNotBlank()) { Text("Join league") }
        state.lists["leagues"].orEmpty().forEach { league -> TextButton({ model.league(league.text("id")) }, enabled = !state.working) { Text("${league.text("name")} · ${league.text("memberCount")} members →") } }
        state.data["invite"]?.let { invite -> Text(invite.text("inviteCode"), fontWeight = FontWeight.Bold); Muted(invite.text("inviteNote")) }
    }
    state.data["league"]?.let { data ->
        var confirm by remember { mutableStateOf("") }
        val league = data.getJSONObject("league")
        Panel(title = league.text("name")) {
            Standings(data.objects("standings")); Muted(data.text("comparisonNote")); Muted(data.text("disclaimer"))
            if (league.optBoolean("owner")) {
                OutlinedButton({ model.leagueAction(league.text("id"), "rotate") }, enabled = !state.working) { Text("Generate new invite code") }
                TextButton({ confirm = "delete" }, enabled = !state.working) { Text("Delete league") }
            } else TextButton({ confirm = "leave" }, enabled = !state.working) { Text("Leave league") }
        }
        if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" }, title = { Text("${confirm.replaceFirstChar { it.uppercase() }} ${league.text("name")}?") },
            text = { Text(if (confirm == "delete") "This removes the private league for its members." else "Your league membership will be removed.") },
            confirmButton = { TextButton({ model.leagueAction(league.text("id"), confirm); confirm = "" }) { Text("Confirm") } }, dismissButton = { TextButton({ confirm = "" }) { Text("Back") } })
    }
}
@Composable private fun Standings(items: List<org.json.JSONObject>) {
    if (items.isEmpty()) Muted("No standings yet.")
    items.forEach { ValueRow("#${it.text("rank")} ${it.text("displayName")}${if (it.optBoolean("currentUser")) " · You" else ""}", percent(it.decimal("returnPercent")), it.decimal("returnPercent")); Muted("${it.text("dataStatus")} · ${time(it.text("valuedAt"))}") }
}
@Composable internal fun FinwizScreen(state: AppState, model: AppViewModel) {
    AccountSelector(state, model::changeRegion, model::selectAccount)
    var question by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("LEARN") }
    var symbol by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf("BEGINNER") }
    Panel("YOUR INVESTING COMPANION", "Ask Finwiz") {
        ChoiceRow(listOf("LEARN" to "Learn", "STOCK_FUNDAMENTALS" to "Stock fundamentals", "TECHNICAL_ANALYSIS" to "Technical analysis", "FUNDAMENTAL_ANALYSIS" to "Fundamental analysis", "VALUATION" to "Valuation", "CASH_FLOW" to "Cash flow", "MARKET_EVALUATION" to "Market", "PORTFOLIO_EDUCATION" to "Portfolio"), topic, !state.working) { topic = it }
        ChoiceRow(listOf("BEGINNER" to "Beginner", "INTERMEDIATE" to "Intermediate"), experience, !state.working) { experience = it }
        OutlinedTextField(symbol, { symbol = it.take(32) }, label = { Text("Stock symbol (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(question, { question = it.take(2000) }, label = { Text("Your question") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button({ model.ask(question, topic, symbol, experience) }, enabled = question.isNotBlank() && !state.working, modifier = Modifier.fillMaxWidth()) { Text("Ask Finwiz") }
        Muted("Educational explanations. Existing account access and service limits apply.")
    }
    state.data["finwiz"]?.let { data -> Panel(title = "Finwiz's answer") {
        Text(data.text("answer")); Muted("${data.text("provider")} · ${time(data.text("generatedAt"))}")
        data.strings("suggestedQuestions").forEach { suggestion -> TextButton({ question = suggestion }) { Text(suggestion) } }
        Muted(data.text("disclaimer"))
    } }
}
