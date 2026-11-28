package com.stoxsim.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.money
import com.stoxsim.app.data.objects
import com.stoxsim.app.data.text
import java.math.BigDecimal

@Composable internal fun RewardsScreen(state: AppState, createCode: () -> Unit, navigate: (String) -> Unit) {
    val rewards = state.data["rewards"]
    if (rewards == null) {
        Text(state.sectionErrors["rewards"] ?: "Loading referral rewards…")
        return
    }
    val context = LocalContext.current
    val link = rewards.text("link")
    val code = rewards.text("code")
    val earned = rewards.optJSONObject("earned")
    val age = rewards.optInt("minimumAgeHours")
    val days = rewards.optInt("qualificationDays")
    val reward = "${money(rewards.money("rewardInr"), "INR")} and ${money(rewards.money("rewardUsd"), "USD")}"
    Panel(title = "Your invite link") {
        when {
            !rewards.optBoolean("enabled") -> Text("New referrals and approvals are paused.")
            !rewards.optBoolean("canInvite") -> Text(
                if (rewards.optBoolean("canReview")) "Administrators can review referrals on the web."
                else "Verify your email to create an invite link."
            )
            link.isEmpty() -> Button(createCode, enabled = !state.working) { Text("Create invite link") }
            else -> {
                ValueRow("Invite code", code)
                Text(link)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("StoxSim invite link", link))
                        if (android.os.Build.VERSION.SDK_INT < 33) {
                            Toast.makeText(context, "Invite link copied", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Copy link") }
                    OutlinedButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
                        context.startActivity(Intent.createChooser(share, "Share StoxSim invite"))
                    }) { Text("Share link") }
                }
            }
        }
        Text("Each approved referral credits $reward in virtual capital to your reward practice accounts.")
        Muted("A new learner must sign up with your link, verify their email, then complete an executed paper trade in a main practice account within $days days.")
        if (age > 0) Muted("Their account must be at least $age hours old.")
        Muted("Eligible referrals go to an administrator for approval.")
    }
    Panel(title = "Your referrals") {
        ValueRow("Pending or qualified", rewards.optLong("pending").toString())
        ValueRow("Rewarded", rewards.optLong("successful").toString())
        ValueRow("Net virtual INR credited", money(earned?.money("inr") ?: BigDecimal.ZERO, "INR"))
        ValueRow("Net virtual USD credited", money(earned?.money("usd") ?: BigDecimal.ZERO, "USD"))
        val referrals = rewards.objects("referrals")
        if (referrals.isEmpty()) Muted("No referrals yet.")
        referrals.forEach { item ->
            val status = item.text("status").lowercase().replaceFirstChar { it.uppercase() }
            ValueRow(item.text("createdAt").take(10), status)
        }
    }
    Panel(title = "Reward history") {
        val history = rewards.objects("history")
        if (history.isEmpty()) Muted("No rewards have been credited.")
        history.forEach { entry ->
            val label = if (entry.text("source") == "ADMIN_CORRECTION") "Reward correction" else "Referral reward"
            ValueRow("$label · ${entry.text("createdAt").take(10)}", "${money(entry.money("inr"), "INR")} · ${money(entry.money("usd"), "USD")}")
        }
        Muted("Use the Wallet account selector to see your practice balances.")
        TextButton(onClick = { navigate("Wallet") }) { Text("Open wallet") }
    }
}
