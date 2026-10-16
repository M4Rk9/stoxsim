package com.stoxsim.app.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stoxsim.app.MainActivity
import com.stoxsim.app.data.EmailAction

@Composable internal fun EmailLinkScreen(action: EmailAction, working: Boolean, error: String?,
    confirm: (String?) -> Unit, dismiss: () -> Unit, browserUrl: () -> String?) {
    androidx.activity.compose.BackHandler(!working) { dismiss() }
    val context = LocalContext.current
    var password by remember(action) { mutableStateOf("") }
    var repeated by remember(action) { mutableStateOf("") }
    var browserError by remember { mutableStateOf<String?>(null) }
    val reset = action == EmailAction.RESET_PASSWORD
    val valid = !reset || (password.length in 8..72 && password.toByteArray(Charsets.UTF_8).size <= 72 && password == repeated)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if (reset) "Choose a new password" else "Verify your email", style = MaterialTheme.typography.headlineLarge)
            Text(if (reset) "This changes the account associated with this email link and signs out all its sessions."
                else "Confirm to verify the account associated with this email link.")
            if (reset) {
                PasswordField(password, { password = it }, "New password", !working)
                PasswordField(repeated, { repeated = it }, "Confirm new password", !working)
                Muted("Use 8–72 characters and at most 72 UTF-8 bytes. Passwords must match.")
            }
            Message(error ?: browserError, null)
            Button({ confirm(if (reset) password else null); password = ""; repeated = "" }, enabled = !working && valid,
                modifier = Modifier.fillMaxWidth()) { Text(if (working) "Please wait…" else if (reset) "Reset password" else "Verify email") }
            TextButton({
                browserUrl()?.let { url ->
                    try {
                        // Force an explicit browser choice and exclude this activity to avoid an App Link loop.
                        val target = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
                        val chooser = Intent.createChooser(target, "Continue in your browser")
                            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
                        context.startActivity(chooser)
                    } catch (_: Exception) { browserError = "No browser is available. Install a browser or continue here." }
                }
            }, enabled = !working) { Text("Continue in browser") }
            TextButton(dismiss, enabled = !working) { Text("Back to StoxSim") }
            Muted("If this link expired or was already used, request a new one. Check Inbox and Spam/Junk for StoxSim emails.")
        }
    }
}
