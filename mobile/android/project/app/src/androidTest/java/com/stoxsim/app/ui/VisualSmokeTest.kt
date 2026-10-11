package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import java.math.BigDecimal

/** Render real native components with synthetic data for visual review, never a live account. */
class VisualSmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test @SdkSuppress(minSdkVersion = 29) fun captureWebsiteThemeOnPhoneInBothAppearances() {
        val dark = mutableStateOf(false)
        val account = Account("INDIA", "Standard India", "STANDARD", "INR", BigDecimal("481725"), BigDecimal.ZERO, true, "synthetic", BigDecimal("500000"))
        val position = Position("EXAMPLE", "Example Company", 25, BigDecimal("20625"), "CLOSED", average = BigDecimal("731"), price = BigDecimal("825"), profit = BigDecimal("2350"), returnPercent = BigDecimal("12.86"))
        val portfolio = Portfolio("INR", BigDecimal("502350"), account.cash, BigDecimal("18275"), BigDecimal("2350"), "CLOSED", "2026-10-10T10:00:00Z", listOf(position), account.capital, unrealized = BigDecimal("2350"), returnPercent = BigDecimal("0.47"))
        val indices = listOf(JSONObject("""{"label":"Example Index","value":24580.75,"changePercent":0.82,"dataStatus":"CLOSED"}"""))
        val state = AppState(restoring = false, user = User("Learner", "learner@example.com", true, listOf(account)), accountId = "synthetic", portfolio = portfolio, lists = mapOf("indices" to indices))
        compose.setContent { StoxSimTheme(dark.value) { AppFrame(state, {}, {}) {
            Text("Good day, Learner.", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            AccountSelector(state, {}, {})
            PortfolioMetrics(state)
            Panel("QUICK ACTIONS", "Keep practising") { Text("Find a stock & trade · Watchlist · Scenario Lab") }
        } } }
        compose.waitForIdle(); capture("dashboard-light.png")
        compose.runOnIdle { dark.value = true }; compose.waitForIdle(); capture("dashboard-dark.png")
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview") }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        compose.onNodeWithText("Good day, Learner.").assertIsDisplayed()
        // PixelCopy waits for the Compose root's draw, rather than racing the system compositor.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("The captured native screen must contain rendered content", pixels.toSet().size > 20)
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
