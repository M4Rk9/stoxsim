package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun CampusScreen(state: AppState, model: AppViewModel) {
    val profile = state.data["campus"]
    var decision by remember { mutableStateOf<CampusDecision?>(null) }
    decision?.let { action -> CampusDecisionDialog(action.title, action.details, action.email, !state.working, { decision = null }) { note, email -> decision = null; action.submit(note, email) } }
    SectionError(state, "campus")
    Text("Learn with your campus.", style = MaterialTheme.typography.headlineMedium)
    Muted("Practise with your standard India portfolio and compare progress with fellow learners.")
    if (profile?.optBoolean("emailVerified") == false) Message(null, "Verify your email before requesting membership or enrolling.")
    profile?.optJSONObject("membership")?.let { membership -> Panel(title = "Your membership") { Text("${membership.text("institutionName")} · ${membership.text("role")}");
        TextButton({ decision = CampusDecision("Leave institution?", "This withdraws your campus competition entries. The last organizer must appoint a replacement first.") { note, _ -> model.campusLeave(note) } }, enabled = !state.working) { Text("Leave institution") }
        OutlinedButton({ model.openCampus(membership.text("institutionId")) }, enabled = !state.working) { Text("Open my campus") } } }
    state.data["membershipRequest"]?.takeIf { it.has("id") }?.let { request -> Panel(title = "Membership request") {
        Text("${request.text("institutionName")} · ${request.text("status")}"); Text(request.text("reviewNote"))
        if (request.text("status") == "PENDING") OutlinedButton({ model.cancelCampusRequest(request.text("id")) }, enabled = !state.working) { Text("Cancel request") }
    } }
    var query by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    Panel(title = "Find an institution") {
        OutlinedTextField(query, { query = it.take(160) }, label = { Text("Institution name") }, modifier = Modifier.fillMaxWidth())
        Button({ model.campusSearch(query) }, enabled = !state.working) { Text("Search institutions") }
        SectionError(state, "institutions")
        state.lists["institutions"].orEmpty().forEach { institution -> TextButton({ model.openCampus(institution.text("id")) }, enabled = !state.working) { Text("${institution.text("name")} · ${institution.text("emailDomain")}${if (institution.optBoolean("suspended")) " · Suspended" else ""}") } }
    }
    if (state.data["selectedInstitution"] != null && state.data["campusWorkspace"] == null && profile?.optJSONObject("membership") == null) Panel(title = "Request membership") {
        Muted("An organizer checks your affiliation. Include your course, department or club; avoid sensitive identity documents.")
        OutlinedTextField(note, { note = it.take(300) }, label = { Text("Affiliation note") }, modifier = Modifier.fillMaxWidth())
        Button({ model.campusApply(note) }, enabled = !state.working && note.isNotBlank() && profile?.optBoolean("emailVerified") == true && state.data["membershipRequest"]?.text("status") != "PENDING") { Text("Request membership") }
    }
    var school by rememberSaveable { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Panel(title = "Institution verification") {
        profile?.optJSONObject("latestVerificationRequest")?.let { Text("${it.text("institutionName")} · ${it.text("status")}"); Text(it.text("reviewNote")) }
        TextButton({ expanded = !expanded }) { Text("Request verification for a new institution") }
        if (expanded) {
            OutlinedTextField(school, { school = it.take(160) }, label = { Text("Institution name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(domain, { domain = it.take(190) }, label = { Text("Institution email domain") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(website, { website = it.take(300) }, label = { Text("Institution website (optional)") }, modifier = Modifier.fillMaxWidth())
            Button({ model.campusVerification(school, domain, website) }, enabled = !state.working && school.trim().length >= 3 && domain.contains('.')) { Text("Submit verification request") }
        }
    }
    state.data["campusWorkspace"]?.let { workspace ->
        val institution = workspace.getJSONObject("institution")
        Panel(workspace.text("viewerRole"), institution.text("name")) {
            if (institution.optBoolean("suspended")) Message(null, "This institution is suspended. New requests and competitions are paused.")
            workspace.objects("competitions").forEach { competition -> TextButton({ model.campusBoard(competition.text("id")) }, enabled = !state.working) { Column { Text(competition.text("title")); Muted("${competition.text("status")} · ${competition.text("participants")}/${competition.text("capacity")} learners"); Muted("${time(competition.text("startsAt"))} – ${time(competition.text("endsAt"))}") } } }
            if (workspace.objects("competitions").isEmpty()) Muted("No competitions yet.")
        }
        if (workspace.text("viewerRole") in listOf("ORGANIZER", "ADMIN")) CampusManagement(state, model) { decision = it }
    }
    if (profile?.optBoolean("platformAdmin") == true) {
        Panel("PLATFORM ADMIN", "Institution verification queue") {
            SectionError(state, "campusVerificationQueue")
            state.lists["campusVerificationQueue"].orEmpty().forEach { request ->
                Text(request.text("institutionName")); Muted("${request.text("emailDomain")} · ${request.text("websiteUrl")}")
                Muted("${request.text("requesterDisplayName")} · ${request.text("requesterEmail")}")
                Row {
                    TextButton({ decision = CampusDecision("Approve institution?", "Verify ${request.text("institutionName")} independently before approval.") { note, _ -> model.campusVerificationReview(request.text("id"), true, note) } }, enabled = !state.working) { Text("Approve institution") }
                    TextButton({ decision = CampusDecision("Reject institution?", request.text("institutionName")) { note, _ -> model.campusVerificationReview(request.text("id"), false, note) } }, enabled = !state.working) { Text("Reject institution") }
                }
            }
            if (state.lists["campusVerificationQueue"].isNullOrEmpty()) Muted("No pending institution requests.")
        }
        state.data["campusWorkspace"]?.let { workspace -> Panel("PLATFORM ADMIN", "Institution controls") {
            val suspended = workspace.getJSONObject("institution").optBoolean("suspended")
            OutlinedButton({ decision = CampusDecision(if (suspended) "Restore institution?" else "Suspend institution?", "Suspension pauses new requests, competitions and enrollment.") { note, _ -> model.campusSuspension(!suspended, note) } }, enabled = !state.working) { Text(if (suspended) "Restore institution" else "Suspend institution") }
            OutlinedButton({ decision = CampusDecision("Recover organizer", "Assign a verified existing learner after checking affiliation independently.", true) { note, email -> model.campusRecovery(email, note) } }, enabled = !state.working) { Text("Recover organizer") }
        } }
    }
    SectionError(state, "campusBoard")
    state.data["campusBoard"]?.let { board ->
        val competition = board.getJSONObject("competition")
        var consent by rememberSaveable(competition.text("id")) { mutableStateOf(false) }
        var withdraw by remember { mutableStateOf(false) }
        Panel("CAMPUS STANDINGS", competition.text("title")) {
            if (state.data["campusWorkspace"]?.text("viewerRole") in listOf("ORGANIZER", "ADMIN") && competition.text("status") in listOf("UPCOMING", "OPEN"))
                TextButton({ decision = CampusDecision("Cancel competition?", "${competition.text("title")} will stop accepting participation. The reason is visible to participants.") { note, _ -> model.campusBoardAction("cancel", note) } }, enabled = !state.working) { Text("Cancel competition") }
            Status(competition.text("status")); Muted("${time(competition.text("startsAt"))} – ${time(competition.text("endsAt"))}")
            if (board.optBoolean("refreshUnavailable")) Message(null, "Prices are unavailable. Your last usable score is shown.")
            if (competition.optBoolean("enrolled")) {
                ValueRow("Entry baseline", money(board.decimal("yourBaselineValue"), "INR")); ValueRow("Latest value", money(board.decimal("yourLatestValue"), "INR"))
                OutlinedButton({ model.campusBoardAction("refresh") }, enabled = !state.working) { Text("Refresh my score") }
                TextButton({ withdraw = true }, enabled = !state.working) { Text("Withdraw permanently") }
            } else if (!competition.optBoolean("withdrawn") && competition.text("status") == "OPEN" && profile?.optJSONObject("membership")?.text("institutionId") == state.data["selectedInstitution"]?.text("id")) {
                Row { Checkbox(consent, { consent = it }); Text("I agree to share my display name and simulated return with campus participants.", Modifier.weight(1f)) }
                Button({ model.campusBoardAction("enroll") }, enabled = consent && !state.working && profile?.optBoolean("emailVerified") == true && state.data["campusWorkspace"]?.getJSONObject("institution")?.optBoolean("suspended") != true && competition.optInt("participants") < competition.optInt("capacity")) { Text("Enroll in competition") }
            }
            if (competition.optBoolean("withdrawn")) Muted("Withdrawn. Re-enrollment is disabled for this competition.")
            board.objects("standings").forEach { ValueRow("#${it.text("rank")} ${it.text("displayName")}", percent(it.decimal("returnPercent")), it.decimal("returnPercent")); Muted("${it.text("dataStatus")} · ${time(it.text("valuedAt"))}") }
            Muted(board.text("comparisonNote")); Text(competition.text("cancellationNote"))
        }
        if (withdraw) AlertDialog(onDismissRequest = { withdraw = false }, title = { Text("Withdraw permanently?") }, text = { Text("You cannot re-enroll or reset your entry baseline.") }, confirmButton = { TextButton({ withdraw = false; model.campusBoardAction("withdraw") }) { Text("Withdraw") } }, dismissButton = { TextButton({ withdraw = false }) { Text("Back") } })
    }
}
@Composable private fun CampusManagement(state: AppState, model: AppViewModel, review: (CampusDecision) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var capacity by rememberSaveable { mutableStateOf("50") }
    val startAt = campusDate(start)
    val endAt = campusDate(end)
    Panel("ORGANIZER", "Create a competition") {
        OutlinedTextField(title, { title = it.take(100) }, label = { Text("Competition title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(start, { start = it.take(16) }, label = { Text("Start: yyyy-MM-dd HH:mm (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(end, { end = it.take(16) }, label = { Text("End: yyyy-MM-dd HH:mm") }, modifier = Modifier.fillMaxWidth())
        Muted("Times use your device timezone: ${ZoneId.systemDefault()}")
        OutlinedTextField(capacity, { capacity = it.take(3) }, label = { Text("Capacity (2–200 learners)") }, modifier = Modifier.fillMaxWidth())
        Button({ model.createCampusCompetition(title, startAt.orEmpty(), endAt!!, capacity.toInt()) }, enabled = !state.working && title.trim().length >= 3 && endAt != null && (start.isBlank() || startAt != null) && capacity.toIntOrNull() in 2..200) { Text("Create competition") }
    }
    SectionError(state, "campusManagement")
    state.data["campusManagement"]?.let { management ->
        Panel(title = "Members") {
            management.objects("members").forEach { member ->
                val id = member.text("userId")
                val nextRole = if (member.text("role") == "ORGANIZER") "MEMBER" else "ORGANIZER"
                Text("${member.text("displayName")} · ${member.text("role")}")
                TextButton({ review(CampusDecision("Change member role?", "${member.text("displayName")} → $nextRole. The last organizer is protected by the server.") { _, _ -> model.campusMember(id, nextRole) }) }, enabled = !state.working) { Text(if (nextRole == "ORGANIZER") "Make organizer" else "Make member") }
                TextButton({ review(CampusDecision("Remove member?", "Remove ${member.text("displayName")} and withdraw their campus entries.") { note, _ -> model.campusMember(id, null, note) }) }, enabled = !state.working) { Text("Remove member") }
            }
        }
        Panel(title = "Moderation history") {
            management.objects("audit").forEach { entry -> Text(entry.text("action")); Muted("${entry.text("actor")} · ${entry.text("target")} · ${time(entry.text("createdAt"))}") }
        }
        Panel(title = "Membership requests") {
        management.objects("requests").forEach { request ->
            var note by rememberSaveable(request.text("id")) { mutableStateOf("") }
            Text("${request.text("displayName")} · ${request.text("status")}"); Muted(request.text("note"))
            if (request.text("status") == "PENDING") {
                OutlinedTextField(note, { note = it.take(500) }, label = { Text("Review note") }, modifier = Modifier.fillMaxWidth())
                Row { TextButton({ model.campusReview(request.text("id"), true, note) }, enabled = !state.working && note.isNotBlank()) { Text("Approve") }; TextButton({ model.campusReview(request.text("id"), false, note) }, enabled = !state.working && note.isNotBlank()) { Text("Reject") } }
            }
        }
    } }
}
private data class CampusDecision(val title: String, val details: String, val email: Boolean = false, val submit: (String, String) -> Unit)
@Composable internal fun CampusDecisionDialog(title: String, details: String, needsEmail: Boolean, enabled: Boolean,
    dismiss: () -> Unit, submit: (String, String) -> Unit) {
    var note by rememberSaveable(title, details) { mutableStateOf("") }
    var email by rememberSaveable(title, details) { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(details)
            if (needsEmail) OutlinedTextField(email, { email = it.take(320) }, label = { Text("Verified learner email") })
            OutlinedTextField(note, { note = it.take(500) }, label = { Text("Review reason") })
        }
    }, confirmButton = { TextButton({ submit(note.trim(), email.trim()) }, enabled = enabled && note.isNotBlank() && (!needsEmail || email.contains('@'))) { Text("Confirm") } },
        dismissButton = { TextButton(dismiss) { Text("Back") } })
}
internal fun campusDate(value: String): String? = try { LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).atZone(ZoneId.systemDefault()).toInstant().toString() } catch (_: Exception) { null }
