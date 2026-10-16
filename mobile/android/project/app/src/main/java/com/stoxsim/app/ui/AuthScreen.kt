package com.stoxsim.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoxsim.app.AppState
import com.stoxsim.app.AppViewModel
import com.stoxsim.app.data.User
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable internal fun AuthScreen(
    state: AppState, authenticate: (String, String, String?) -> Unit,
    forgot: (String) -> Unit, clearMessage: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    // Never put a password in saved instance state / SavedStateHandle.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val register = mode == "register"
    val recovering = mode == "forgot"
    BackHandler(mode != "login" && !state.working) { mode = "login"; password = ""; clearMessage() }
    Text(when (mode) { "register" -> "Start practising."; "forgot" -> "Reset your password."; else -> "Welcome back." },
        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("Build your investing skills with virtual money.", style = MaterialTheme.typography.bodyLarge)
    Message(state.error, state.notice)
    if (register) OutlinedTextField(name, { name = it }, label = { Text("Display name") },
        singleLine = true, enabled = !state.working, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(email, { email = it }, label = { Text("Email address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true,
        enabled = !state.working, modifier = Modifier.fillMaxWidth())
    if (!recovering) OutlinedTextField(password, { password = it }, label = { Text("Password") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
        supportingText = if (register) { { Text("8–72 characters, at most 72 UTF-8 bytes.") } } else null,
        trailingIcon = { TextButton(onClick = { passwordVisible = !passwordVisible }) { Text(if (passwordVisible) "Hide" else "Show") } },
        enabled = !state.working, modifier = Modifier.fillMaxWidth())
    if (register || recovering) Text(com.stoxsim.app.data.EMAIL_GUIDANCE, style = MaterialTheme.typography.bodySmall)
    if (register) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(accepted, { accepted = it }, enabled = !state.working)
            Text("I accept the Terms and Privacy Notice.", Modifier.weight(1f))
        }
        Row {
            TextButton(onClick = { uri.openUri("https://stoxsim.com/terms") }) { Text("Read Terms ↗") }
            TextButton(onClick = { uri.openUri("https://stoxsim.com/privacy") }) { Text("Read Privacy ↗") }
        }
    }
    val validEmail = email.trim().contains('@') && email.trim().length <= 320
    val validPassword = password.isNotBlank() && password.length <= 72 && password.toByteArray(Charsets.UTF_8).size <= 72
    val enabled = !state.working && validEmail && (recovering || (validPassword && (!register || (password.length >= 8 && name.trim().length in 2..100 && accepted))))
    Button(onClick = {
        if (recovering) forgot(email) else { authenticate(email, password, if (register) name else null); password = "" }
    }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Text(if (state.working) "Please wait…" else when (mode) { "register" -> "Create account"; "forgot" -> "Send reset link"; else -> "Sign in" })
    }
    if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
    TextButton(onClick = { mode = if (mode == "login") "register" else "login"; password = ""; accepted = false; clearMessage() }, enabled = !state.working) {
        Text(if (mode == "login") "Create a StoxSim account" else "Back to sign in")
    }
    if (mode == "login") TextButton(onClick = { mode = "forgot"; password = ""; clearMessage() }, enabled = !state.working) { Text("Forgot password?") }
    Text("Educational simulator • No real-money trading", style = MaterialTheme.typography.bodySmall)
}

