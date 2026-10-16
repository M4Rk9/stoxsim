package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.*
import org.json.JSONObject

@Composable internal fun SettingsScreen(state: AppState, model: AppViewModel) {
    val user = state.user ?: return
    val uri = LocalUriHandler.current
    var name by rememberSaveable(user.displayName) { mutableStateOf(user.displayName) }
    var email by rememberSaveable(user.email) { mutableStateOf(user.email) }
    // Passwords never enter saved instance state or persistent storage.
    var profilePassword by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var deletingPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Panel("YOUR ACCOUNT", user.displayName) {
        Text(user.email)
        Status(if (user.verified) "Email verified" else "Email not verified")
        if (!user.verified) {
            Muted(com.stoxsim.app.data.EMAIL_GUIDANCE)
            OutlinedButton(model::resendVerification, enabled = !state.working) { Text("Resend verification email") }
        }
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.take(320) }, label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        if (email.trim() != user.email) PasswordField(profilePassword, { profilePassword = it }, "Current password to change email", !state.working)
        Button({ model.profile(name, email, profilePassword); profilePassword = "" }, enabled = !state.working && name.trim().length in 2..100 && email.trim().contains('@') && (email.trim() == user.email || profilePassword.isNotEmpty())) { Text("Save profile") }
    }
    Panel("ACCOUNT SECURITY", "Change password") {
        PasswordField(current, { current = it }, "Current password", !state.working)
        PasswordField(replacement, { replacement = it }, "New password", !state.working)
        PasswordField(confirmation, { confirmation = it }, "Confirm new password", !state.working)
        Muted("8–72 characters, at most 72 UTF-8 bytes. Updating your password signs out all sessions.")
        Button({ confirm = "password" }, enabled = !state.working && current.isNotBlank() && validNewPassword(replacement) && replacement == confirmation) { Text("Update password") }
    }
    Panel(title = "Active sessions") {
        SectionError(state, "sessions")
        state.lists["sessions"].orEmpty().forEach { session ->
            Text(session.text("device").ifBlank { "Unknown device" }, fontWeight = FontWeight.Bold)
            Muted("Last used ${time(session.text("lastUsedAt"))} · Expires ${time(session.text("expiresAt"))}")
            if (session.optBoolean("current")) Status("This device")
            else TextButton({ model.revokeSession(session.text("id")) }, enabled = !state.working) { Text("Revoke session") }
        }
        OutlinedButton({ confirm = "logoutAll" }, enabled = !state.working) { Text("Sign out all devices") }
    }
    Panel(title = "Security activity") {
        SectionError(state, "events")
        state.lists["events"].orEmpty().forEach { event -> Text(event.text("type").replace('_', ' ')); if (event.text("detail").isNotBlank()) Muted(event.text("detail")); Muted(time(event.text("createdAt"))) }
    }
    Panel(title = "Your data & reports") {
        OutlinedButton({ model.openScreen("Weekly reports") }) { Text("Weekly reports & email preferences") }
        OutlinedButton(model::exportAccount, enabled = !state.working) { Text("Prepare account export") }
        TextButton({ uri.openUri("https://stoxsim.com/privacy") }) { Text("Privacy Notice ↗") }
        TextButton({ uri.openUri("https://stoxsim.com/terms") }) { Text("Terms ↗") }
        TextButton({ uri.openUri("https://stoxsim.com/disclaimer") }) { Text("Simulator disclaimer ↗") }
    }
    Panel(title = "Delete account") {
        Text("Permanently remove your account and simulation data. This cannot be undone.")
        PasswordField(deletingPassword, { deletingPassword = it }, "Password to confirm deletion", !state.working)
        OutlinedButton({ confirm = "delete" }, enabled = !state.working && deletingPassword.isNotBlank()) { Text("Delete my account") }
    }
    Button(model::logout, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    if (confirm.isNotEmpty()) AlertDialog(onDismissRequest = { confirm = "" }, title = { Text(when (confirm) { "delete" -> "Permanently delete your account?"; "password" -> "Update password?"; else -> "Sign out all devices?" }) },
        text = { Text(if (confirm == "delete") "Your account and simulation data will be removed permanently." else "You will need to sign in again on every device.") },
        confirmButton = { TextButton({
            when (confirm) { "delete" -> { model.deleteAccount(deletingPassword); deletingPassword = "" }; "password" -> { model.password(current, replacement); current = ""; replacement = ""; confirmation = "" }; else -> model.logoutAll() }; confirm = ""
        }) { Text("Confirm") } }, dismissButton = { TextButton({ confirm = "" }) { Text("Back") } })
}
internal fun validNewPassword(value: String) = value.length in 8..72 && value.toByteArray(Charsets.UTF_8).size <= 72 && value.isNotBlank()
@Composable internal fun PasswordField(value: String, change: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), enabled = enabled)
}
@Composable internal fun ReportsScreen(state: AppState, model: AppViewModel) {
    val preference = state.data["reportPreference"]
    var zone by rememberSaveable(preference?.text("zoneId")) { mutableStateOf(preference?.text("zoneId") ?: "Asia/Kolkata") }
    var enabled by rememberSaveable(preference?.optBoolean("enabled")) { mutableStateOf(preference?.optBoolean("enabled") ?: false) }
    Panel("YOUR WEEKLY RECAP", "Email preferences") {
        SectionError(state, "reportPreference")
        Row { Checkbox(enabled, { enabled = it }, enabled = !state.working); Text("Receive weekly portfolio reports") }
        OutlinedTextField(zone, { zone = it.take(100) }, label = { Text("Report timezone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Muted(preference?.text("schedule").orEmpty()); Muted("Reports require a verified email. " + com.stoxsim.app.data.EMAIL_GUIDANCE)
        Button({ model.reportPreference(enabled, zone) }, enabled = !state.working && preference != null && zone.isNotBlank()) { Text("Save preferences") }
        OutlinedButton(model::reportPreview, enabled = !state.working) { Text("Preview current report") }
        SectionError(state, "reportPreview")
    }
    state.data["reportPreview"]?.let { Panel(title = "Current report preview") { ReportSnapshot(it) } }
    SectionError(state, "reports")
    if (state.lists["reports"].isNullOrEmpty()) Muted("Your weekly reports will appear here after they are generated.")
    state.lists["reports"].orEmpty().forEach { report -> Panel(title = "Weekly report · ${report.text("deliveryStatus")}") { ReportSnapshot(report.getJSONObject("snapshot")) } }
}
@Composable private fun ReportSnapshot(data: JSONObject) {
    Text("${data.text("periodStart")} – ${data.text("periodEnd")}", style = MaterialTheme.typography.titleMedium)
    data.objects("markets").forEach { market ->
        Kicker(regionLabel(market.text("marketRegion")))
        val currency = market.text("currency")
        ValueRow("Account value", money(market.decimal("accountValue"), currency)); ValueRow("Account value change", money(market.decimal("accountValueChange"), currency))
        ValueRow("Total P/L", money(market.decimal("totalProfitLoss"), currency), market.decimal("totalProfitLoss"))
        ValueRow("Total return", percent(market.decimal("totalReturnPercent"))); ValueRow("Trades executed", market.text("tradesExecuted")); Status(market.text("status"))
    }
    data.strings("learningNotes").forEach { Text(it) }; Muted(data.text("disclaimer"))
}

@Composable internal fun SubscriptionScreen(state: AppState, createSandbox: () -> Unit) {
    val user = state.user ?: return
    Panel("PLAN & PRACTICE ACCOUNTS", "Your benefits") {
        SectionError(state, "subscription")
        state.data["subscription"]?.let { data ->
            Text("${data.text("plan")} · ${data.text("status")}", style = MaterialTheme.typography.titleLarge)
            if (data.text("currentPeriodEnd").isNotBlank()) ValueRow("Current period ends", time(data.text("currentPeriodEnd")))
            data.optJSONObject("entitlements")?.let { entitlements ->
                ValueRow("Scenario allowance", entitlements.text("scenarioCredits")); ValueRow("Sandbox portfolios", entitlements.text("maximumSandboxPortfolios"))
                ValueRow("Analytics", entitlements.text("analyticsTier")); ValueRow("Finwiz", entitlements.text("finwizTier"))
                Muted(entitlements.text("finwizDescription"))
            }
            Text(data.text("notice"))
            Muted("Existing plan benefits work in the app. Android subscription purchases are not available in this build.")
            if (data.optJSONObject("sandboxProvisioning")?.optBoolean("canCreateAdditional") == true) OutlinedButton(createSandbox, enabled = !state.working) { Text("Create additional sandbox") }
        }
        user.accounts.forEach { account -> ValueRow(account.label, money(account.cash, account.currency)); Muted("${regionLabel(account.region)} · ${account.kind} · ${if (account.active) "Active" else "Locked"}") }
    }
    state.data["subscription"]?.objects("plans")?.forEach { plan -> Panel(title = plan.text("displayName")) {
        ValueRow("Monthly price", money(plan.decimal("monthlyPriceInr"), "INR"))
        plan.optJSONObject("entitlements")?.let { benefits ->
            ValueRow("Scenario credits", benefits.text("scenarioCredits"))
            ValueRow("Practice sandboxes", benefits.text("maximumSandboxPortfolios"))
            ValueRow("Analytics", benefits.text("analyticsTier"))
            Muted(benefits.text("finwizDescription"))
        }
        Muted("Purchases are not available in this app yet.")
    } }
}
