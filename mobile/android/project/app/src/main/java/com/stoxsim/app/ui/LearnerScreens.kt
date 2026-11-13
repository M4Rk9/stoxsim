package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*
import org.json.JSONObject

@Composable internal fun LearnerHome(state: AppState, model: AppViewModel) {
    Panel("RESEARCH · PRACTISE · REVIEW", "Test your thinking.") {
        Text("Research a company, form a view and practise with virtual money. Review the evidence, not just the return.")
        Button({ model.openScreen("Markets") }) { Text("Explore a company") }
        OutlinedButton({ model.openScreen("Reflections") }) { Text("Open strategy journal") }
        val due = state.lists["reflections"].orEmpty().count { note ->
            val date = note.optJSONObject("plan")?.text("reviewOn").orEmpty()
            note.text("reviewedAt").isBlank() && date.isNotBlank() && date <= java.time.LocalDate.now().toString()
        }
        if (due > 0) Muted("$due review${if (due == 1) "" else "s"} due in your journal")
    }
    PortfolioMetrics(state)
    val recommendation = state.data["learnerHome"]?.optJSONObject("recommendation")
    if (recommendation != null) Panel("YOUR NEXT LEARNING STEP", recommendation.text("title")) {
        Text(recommendation.text("description"))
        if (learnerDestination(recommendation.text("href")) != null)
            Button(model::useRecommendation, enabled = !state.working) { Text(recommendation.text("actionLabel")) }
    } else Panel(title = "Continue learning") {
        Muted("Explore a lesson or review your learning path at your own pace.")
        Button({ model.openScreen("Learn") }) { Text("Open Learn") }
    }
    SectionError(state, "learnerHome")
    state.data["journey"]?.let { progress ->
        Muted("${progress.optInt("completedSteps")} of ${progress.optInt("totalSteps", 6)} learning steps explored")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton({ model.openScreen("Finwiz") }) { Text("Ask Finwiz") }
        TextButton({ model.openScreen("Notifications") }) { Text("Notifications (${state.data["notifications"]?.optInt("unreadCount") ?: 0})") }
    }
    TextButton({ model.openScreen("Learner profile") }) { Text("Personalize learning") }
}

@Composable internal fun LearnHub(state: AppState, navigate: (String) -> Unit) {
    Muted("Build understanding at your own pace. All trades use virtual money.")
    listOf("Finwiz" to "Ask Finwiz", "Learning path" to "Continue learning missions", "Scenario Lab" to "Explore a portfolio scenario", "Reflections" to "Strategy journal and reviews").forEach { (route, label) ->
        OutlinedButton({ navigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
    SectionError(state, "progression")
    TextButton({ navigate("Learner profile") }) { Text("Learning preferences") }
}

@Composable internal fun LearnerProfileScreen(state: AppState, save: (String, String, String) -> Unit) {
    val profile = state.data["learnerProfile"]
    var level by remember(state.user?.id, profile) { mutableStateOf(profile?.text("experienceLevel").orEmpty()) }
    var objective by remember(state.user?.id, profile) { mutableStateOf(profile?.text("learningObjective").orEmpty()) }
    var market by remember(state.user?.id, profile) { mutableStateOf(profile?.text("preferredMarket").orEmpty()) }
    SectionError(state, "learnerProfile")
    Panel(title = "Your learning preferences") {
        Muted("Optional. These choices personalize educational suggestions across your devices; they do not change your portfolios.")
        Text("Experience")
        ChoiceRow(listOf("" to "No preference") + learnerLevels.map { it to it.lowercase().replaceFirstChar(Char::titlecase) }, level, !state.working) { level = it }
        Text("Learning objective")
        ChoiceRow(listOf("" to "No preference") + learnerObjectives.map { it to it.lowercase().replaceFirstChar(Char::titlecase) }, objective, !state.working) { objective = it }
        Text("Preferred market")
        ChoiceRow(listOf("" to "No preference", "INDIA" to "India", "UNITED_STATES" to "United States"), market, !state.working) { market = it }
        Button({ save(level, objective, market) }, enabled = !state.working && profile != null) { Text("Save learning preferences") }
    }
}

@Composable internal fun NotificationsScreen(state: AppState, read: (String, String?) -> Unit,
    readAll: () -> Unit, preference: (String, Boolean) -> Unit) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if(granted)preference("pushEnabled",true) }
    SectionError(state, "notifications")
    val items = state.data["notifications"]?.objects("items").orEmpty()
    if (items.isEmpty()) Muted("No notifications yet.")
    else {
        TextButton(readAll, enabled = !state.working) { Text("Mark all as read") }
        items.forEach { item -> Panel(item.text("category"), item.text("title")) {
            Text(item.text("body")); Muted(time(item.text("createdAt")))
            if (item.text("readAt").isBlank()) TextButton({ read(item.text("id"), null) }, enabled = !state.working) { Text("Mark as read") }
            if (learnerDestination(item.text("href")) != null) OutlinedButton({ read(item.text("id"), item.text("href")) }, enabled = !state.working) { Text("Open notification") }
        } }
    }
    SectionError(state, "notificationPreferences")
    val preferences = state.data["notificationPreferences"]
    if (preferences != null) Panel(title = "Notification preferences") {
        listOf("learning" to "Learning progress", "competitions" to "Competitions", "portfolioEducation" to "Portfolio education",
            "productAnnouncements" to "Product announcements", "promotionalMessages" to "Promotional messages", "pushEnabled" to "Push notifications", "emailDigest" to "Optional email digest").forEach { (key, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f)); Switch(preferences.optBoolean(key), { enabled ->
                    if(key=="pushEnabled" && enabled && Build.VERSION.SDK_INT>=33 && !PushNotifications.permitted(context))
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else preference(key,enabled)
                }, enabled = !state.working && (key!="pushEnabled" || PushNotifications.configured(context)))
            }
        }
        Muted("Security notices are separate from optional updates. Promotional messages require your choice above.")
        Muted(if(PushNotifications.configured(context)) "Android permission and your preference are both required. Device updates contain only a generic reminder." else "Push notifications are not configured in this build. In-app notifications remain available.")
    }
}

@Composable internal fun ReflectionsScreen(state: AppState, save: (String?, JSONObject) -> Unit,
    delete: (String) -> Unit, link: (String, String) -> Unit, assist: (String, Boolean) -> Unit, clearAnswer: () -> Unit) {
    // Private text deliberately stays out of saved instance state and disk caches.
    var editing by remember(state.user?.id) { mutableStateOf<JSONObject?>(null) }
    var creating by remember(state.user?.id) { mutableStateOf(false) }
    var saving by remember(state.user?.id) { mutableStateOf(false) }
    LaunchedEffect(state.working, state.error, state.notice) {
        if (saving && !state.working) {
            if (state.error == null && state.notice == "Reflection saved privately to your StoxSim account.") creating = false
            saving = false
        }
    }
    var assisting by remember(state.user?.id) { mutableStateOf<JSONObject?>(null) }
    var deleting by remember(state.user?.id) { mutableStateOf<JSONObject?>(null) }
    Text("Strategy journal", style = MaterialTheme.typography.headlineMedium)
    Text("Record your thesis, then revisit the evidence. Targets and stops are notes, not executable orders. Review dates appear in the app; no reminder is sent.")
    OutlinedButton({ editing = null; creating = true }, enabled = !state.working) { Text("New strategy") }
    SectionError(state, "reflections")
    state.lists["reflections"].orEmpty().forEach { item -> Panel(title = item.text("reason")) {
        item.optJSONObject("plan")?.let { plan ->
            listOf("symbol" to "Symbol", "exchange" to "Exchange", "horizon" to "Horizon", "target" to "Target", "invalidation" to "Invalidation", "reviewOn" to "Review date").forEach { (key, label) ->
                plan.text(key).takeIf { it.isNotBlank() }?.let { Text("$label: $it") }
            }
        }
        if (item.text("reviewedAt").isNotBlank()) Muted("Reviewed ${time(item.text("reviewedAt"))}")
        item.optJSONObject("linkedOrder")?.let { order ->
            Text("Practice outcome", style = MaterialTheme.typography.titleMedium)
            Text("${order.text("side").lowercase()} ${order.text("quantity")} · ${order.text("symbol")} · ${order.text("status").lowercase()}")
            if (order.text("executionPrice").isNotBlank()) Text("Executed at ${order.text("currency")} ${order.text("executionPrice")}")
            Muted("Execution is not a closed-trade return. Review charges and holdings in Portfolio.")
        }
        if (item.text("orderId").isBlank()) {
            var showOrders by remember(item.text("id")) { mutableStateOf(false) }
            TextButton({ showOrders = !showOrders }) { Text("Link an existing practice order") }
            if (showOrders) {
                val plan = item.optJSONObject("plan")
                val matching = state.lists["journalOrders"].orEmpty().filter { order ->
                    plan?.text("symbol").isNullOrBlank() || (order.text("symbol") == plan?.text("symbol") && order.text("exchange") == plan?.text("exchange"))
                }
                Muted("Orders from your currently selected practice account. Change accounts in Markets to use another.")
                if (matching.isEmpty()) Muted("No matching orders available in this account.")
                matching.forEach { order -> OutlinedButton({ link(item.text("id"), order.text("id")) }, enabled = !state.working) {
                    Text("${order.text("symbol")} · ${order.text("side").lowercase()} ${order.text("quantity")} · ${order.text("status").lowercase()} · ${time(order.text("createdAt"))}")
                } }
            }
        }
        listOf("assumptions" to "Assumptions", "risks" to "Risks", "expectedOutcome" to "Expected outcome", "reflection" to "What I learned").forEach { (key, label) ->
            item.text(key).takeIf { it.isNotBlank() }?.let { Text(label, style = MaterialTheme.typography.labelLarge); Text(it) }
        }
        Muted(time(item.text("updatedAt")))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton({ editing = item; creating = true }, enabled = !state.working) { Text("Edit") }
            TextButton({ deleting = item }, enabled = !state.working) { Text("Delete") }
        }
        OutlinedButton({ clearAnswer(); assisting = item }, enabled = !state.working) { Text("Ask Finwiz about this reflection") }
    } }
    state.data["reflectionAssistance"]?.let { answer -> Panel(title = "Finwiz reflection assistance") {
        FinwizAnswer(answer.text("answer")); Muted(answer.text("disclaimer"))
        Muted("${answer.text("provider")} · ${time(answer.text("generatedAt"))}")
        TextButton(clearAnswer) { Text("Dismiss answer") }
    } }
    if (creating) ReflectionEditor(state.user?.id.orEmpty(), editing, state.working, state.error,
        { body -> saving = true; save(editing?.text("id"), body) }, { creating = false })
    assisting?.let { item -> AlertDialog(onDismissRequest = { assisting = null }, title = { Text("Share this reflection with Finwiz?") },
        text = { Text("Only continue if you want this reflection sent to the configured AI provider for educational assistance. Private reflections are never sent automatically. Your plan's usage limits apply.") },
        confirmButton = { TextButton({ assist(item.text("id"), true); assisting = null }, enabled = !state.working) { Text("Share and ask Finwiz") } },
        dismissButton = { TextButton({ assisting = null }) { Text("Keep private") } }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this reflection?") },
        text = { Text("This removes the reflection from your StoxSim account.") },
        confirmButton = { TextButton({ delete(item.text("id")); deleting = null }, enabled = !state.working) { Text("Delete") } },
        dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }) }
}

@Composable private fun ReflectionEditor(owner: String, item: JSONObject?, working: Boolean, error: String?, save: (JSONObject) -> Unit, dismiss: () -> Unit) {
    val fields = listOf("reason" to "Why I made this decision", "assumptions" to "Assumptions", "risks" to "Risks considered", "expectedOutcome" to "Expected outcome", "reflection" to "What I learned")
    val planFields = listOf("exchange" to "Exchange (e.g. NSE)", "symbol" to "Symbol", "horizon" to "Time horizon", "target" to "Target / evidence", "invalidation" to "Stop / invalidation", "reviewOn" to "Review date (YYYY-MM-DD)")
    val planValues = remember(owner, item) { planFields.associate { it.first to mutableStateOf(item?.optJSONObject("plan")?.text(it.first).orEmpty()) } }
    var completeReview by remember(owner, item) { mutableStateOf(false) }
    val reviewDate = planValues.getValue("reviewOn").value
    val validDate = reviewDate.isBlank() || runCatching { java.time.LocalDate.parse(reviewDate).year in 1970..2100 }.getOrDefault(false)
    val values = remember(owner, item) { fields.associate { it.first to mutableStateOf(item?.text(it.first).orEmpty()) } }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (item == null) "New strategy" else "Review strategy") },
        text = { Column(Modifier.heightIn(max = 400.dp).then(Modifier.verticalScrollForReflection()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Message(error)
            planFields.forEach { (key, label) -> OutlinedTextField(planValues.getValue(key).value,
                { planValues.getValue(key).value = (if (key == "exchange" || key == "symbol") it.uppercase(java.util.Locale.ROOT) else it).take(when(key) { "exchange" -> 20; "symbol" -> 100; "horizon" -> 200; "reviewOn" -> 10; else -> 500 }) },
                label = { Text(label) }, modifier = Modifier.fillMaxWidth(), enabled = !working,
                isError = key == "reviewOn" && !validDate) }
            fields.forEach { (key, label) -> OutlinedTextField(values.getValue(key).value, { values.getValue(key).value = it.take(2000) },
                label = { Text(label) }, supportingText = { Text("${values.getValue(key).value.length}/2000") }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !working) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(completeReview, { completeReview = it }, enabled = !working); Text("Complete review after recording what I learned") }
        } },
        confirmButton = { TextButton({ save(reflectionPayload(values.getValue("reason").value, values.getValue("assumptions").value,
            values.getValue("risks").value, values.getValue("expectedOutcome").value, values.getValue("reflection").value,
            item?.text("accountId")?.takeIf { it.isNotBlank() }, item?.text("orderId")?.takeIf { it.isNotBlank() }).apply {
                put("plan", JSONObject().apply { planFields.forEach { (key, _) -> put(key, planValues.getValue(key).value.takeIf { it.isNotBlank() } ?: if (key == "reviewOn") JSONObject.NULL else "") } })
                put("completeReview", completeReview)
            }) }, enabled = !working && validDate && values.getValue("reason").value.isNotBlank() && (!completeReview || values.getValue("reflection").value.isNotBlank())) { Text("Save reflection") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
@Composable private fun Modifier.verticalScrollForReflection(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))
