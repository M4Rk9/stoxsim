package com.stoxsim.app.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import com.stoxsim.app.data.*
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun newsDestination(value: String): String? = value.toHttpUrlOrNull()?.takeIf {
    it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty()
}?.toString()

@Composable internal fun CompanyNews(data: JSONObject?, error: String?, loading: Boolean) {
    val browser = LocalUriHandler.current
    var linkError by remember(data) { mutableStateOf(false) }
    Panel(title = "Company news") {
        Text("Selected headlines from the past seven days. Coverage may be delayed or incomplete. Articles are not sent to Finwiz.")
        when {
            error != null -> Text("News could not be loaded. Refresh to try again; research and practice remain available.")
            data == null -> Text(if (loading) "Loading headlines…" else "News unavailable.")
            data.text("status") == "DISABLED" -> Text("News is not enabled for this market yet.")
            data.text("status") == "EMPTY" -> Text("No recent headlines are available for this listing.")
            data.text("status") != "AVAILABLE" -> Text("News is temporarily unavailable.")
            else -> {
                Text("Provider: ${data.text("provider")} · Checked ${time(data.text("checkedAt"))}")
                data.objects("articles").forEach { item ->
                    val destination = newsDestination(item.text("url"))
                    Text(item.text("headline"))
                    Text("${item.text("source")} · Published ${time(item.text("publishedAt"))}")
                    if (destination != null) TextButton({ try { browser.openUri(destination) } catch (_: Exception) { linkError = true } }) { Text("Read original article ↗") }
                }
            }
        }
        if (linkError) Text("No browser could open this article.")
    }
}
