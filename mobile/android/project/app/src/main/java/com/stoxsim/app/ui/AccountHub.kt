package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*

@Composable internal fun AccountHub(state: AppState, navigate: (String) -> Unit, region: (String) -> Unit,
    account: (String) -> Unit, dark: (Boolean) -> Unit, logout: () -> Unit) {
    val user = state.user ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Text(user.displayName.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary) }
        }
        Column { Text(user.displayName, style = MaterialTheme.typography.titleLarge); Muted(user.email); Muted(if (user.verified) "Email verified" else "Email verification pending") }
    }
    Panel(title = "Market & practice account") {
        ChoiceRow(listOf("INDIA" to "🇮🇳 IND · INR", "UNITED_STATES" to "🇺🇸 US · USD"), state.region, !state.working, region)
        AccountSelector(state, region, account)
        Muted("Stocks, wallet and new AI chats use the selected market.")
    }
    Panel(title = "Appearance") {
        ChoiceRow(listOf("light" to "Light", "dark" to "Dark"), if (state.dark) "dark" else "light") { dark(it == "dark") }
    }
    AccountLink("Wallet", "Virtual cash, reserves and account activity", navigate)
    AccountLink("Subscriptions", state.data["subscription"]?.let { "${it.text("plan")} · ${it.text("status")} · Manage your benefits" } ?: "Manage your plan and benefits", navigate)
    AccountLink("Profile & security", "Profile, passwords, sessions and your data", navigate)
    Panel(title = "More from StoxSim") {
        listOf("Learning path" to "Challenges & achievements", "Competitions" to "Leaderboard & private leagues", "Campus" to "Your institution & competitions", "Weekly reports" to "Reports & email preferences").forEach { (route, label) ->
            TextButton({ navigate(route) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("→") } }
        }
    }
    OutlinedButton(logout, modifier = Modifier.fillMaxWidth(), enabled = !state.working) { Text("Sign out") }
}
@Composable private fun AccountLink(route: String, subtitle: String, navigate: (String) -> Unit) {
    Surface(onClick = { navigate(route) }, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(if (route == "Subscriptions") "Manage subscriptions" else route, style = MaterialTheme.typography.titleMedium); Muted(subtitle) }
            Text("→", color = MaterialTheme.colorScheme.primary)
        }
    }
}
@Composable internal fun WalletScreen(state: AppState, select: (String) -> Unit, navigate: (String) -> Unit) {
    AccountSelector(state, {}, select)
    Panel("${regionLabel(state.region)} · ${state.account?.label.orEmpty()}", "Practice wallet") {
        Muted("Available virtual cash")
        Text(money(state.portfolio?.cash ?: state.account?.cash, state.currency), style = MaterialTheme.typography.headlineLarge)
        ValueRow("Reserved for open orders", money(state.portfolio?.blocked ?: state.account?.blocked, state.currency))
        ValueRow("Starting virtual capital", money(state.portfolio?.capital ?: state.account?.capital, state.currency))
        state.portfolio?.let { Status(it.status); Muted("Valued ${time(it.valuedAt)}") }
        SectionError(state, "portfolio")
        Muted("Simulation funds only. There are no deposits, withdrawals or real-money transfers.")
        TextButton({ navigate("Orders") }) { Text("View orders & reserved funds →") }
    }
    LedgerPanel(state)
}
@Composable internal fun LedgerPanel(state: AppState) {
    Panel("CASH ACTIVITY", "Wallet activity") {
        SectionError(state, "ledger")
        if (state.lists["ledger"].isNullOrEmpty()) Muted("Your practice account activity will appear here.")
        state.lists["ledger"].orEmpty().forEach { entry ->
            Text(entry.text("description"), fontWeight = FontWeight.SemiBold)
            ValueRow(entry.text("direction"), money(entry.decimal("amount"), state.currency))
            ValueRow("Balance after", money(entry.decimal("balanceAfter"), state.currency))
            Muted(time(entry.text("createdAt"))); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
