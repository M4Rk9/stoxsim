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
    ScenarioWorkspace(state, model::changeRegion, model::selectAccount, model::scenario, { model.clearData("scenarioResult") }, model::refresh)
}
@Composable internal fun LearningScreen(state: AppState, model: AppViewModel) {
    SectionError(state, "progression")
    SectionError(state, "dailyStreak")
    val data = state.data["progression"]
    if (data == null) { Muted("Your learning path will appear when the connection is available."); return }
    Panel("YOUR LEARNING PATH", data.text("levelName")) {
        ValueRow("Level / XP", "${data.text("level")} / ${data.text("totalXp")}")
        ValueRow("Current / longest streak", "${data.text("currentStreak")} / ${data.text("longestStreak")} days")
        Muted(if (data.optBoolean("checkedInToday")) "Today’s visit recorded" else "Your daily visit syncs automatically while you’re signed in.")
        Muted("Streak timezone: ${data.text("checkInZoneId")}")
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
