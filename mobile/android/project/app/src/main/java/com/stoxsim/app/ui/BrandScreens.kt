package com.stoxsim.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.R

/** Visible only while an actual session restore is pending; no artificial launch delay. */
@Composable internal fun LaunchScreen(state: AppState, retry: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.stoxsim_logo), "StoxSim", Modifier.size(80.dp))
            Spacer(Modifier.height(20.dp))
            Text("StoxSim", style = MaterialTheme.typography.headlineLarge)
            Text("Practise today. Invest smarter tomorrow.", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            if (state.restoreFailed) {
                Text(state.error ?: "Check your connection and try again.", textAlign = TextAlign.Center)
                Button(retry, enabled = !state.working, modifier = Modifier.padding(top = 16.dp)) { Text("Retry connection") }
            } else {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                Text("Connecting to your account…", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable internal fun WelcomeScreen(state: AppState, authenticate: (String, String, String?) -> Unit,
    forgot: (String) -> Unit, clearMessage: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp)
            .widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.stoxsim_logo), null, Modifier.size(42.dp))
                Text("StoxSim", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your edge starts\nwith practice.", style = MaterialTheme.typography.headlineLarge)
                Text("Stocks. Finwiz AI. Scenario Lab.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Panel { AuthScreen(state, authenticate, forgot, clearMessage) }
        }
    }
}
