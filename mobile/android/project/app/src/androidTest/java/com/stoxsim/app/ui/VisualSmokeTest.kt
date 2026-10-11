package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
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

/** Real components and synthetic data only; no production/API/AI transactions. */
class VisualSmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test @SdkSuppress(minSdkVersion = 29) fun captureProductScreensInBothAppearances() {
        val dark = mutableStateOf(false); val page = mutableStateOf("Home")
        val account = Account("INDIA", "Standard India", "STANDARD", "INR", BigDecimal("481725"), BigDecimal.ZERO, true, "synthetic", BigDecimal("500000"))
        val position = Position("EXAMPLE", "Example Company", 25, BigDecimal("20625"), "CLOSED", average = BigDecimal("731"), price = BigDecimal("825"), profit = BigDecimal("2350"), returnPercent = BigDecimal("12.86"))
        val portfolio = Portfolio("INR", BigDecimal("502350"), account.cash, BigDecimal("18275"), BigDecimal("2350"), "CLOSED", "2026-10-10T10:00:00Z", listOf(position), account.capital, unrealized = BigDecimal("2350"), returnPercent = BigDecimal("0.47"))
        val indices = listOf(JSONObject("""{"label":"Example Index","value":24580.75,"changePercent":0.82,"dataStatus":"CLOSED","exchangeTimestamp":"2026-10-10T10:00:00Z"}"""))
        val movers = JSONObject("""{"dataStatus":"CLOSED","generatedAt":"2026-10-10T10:00:00Z","gainers":[{"symbol":"EXAMPLE","name":"Example Company","exchange":"NSE","lastPrice":825,"changePercent":1.25},{"symbol":"SAMPLE","name":"Sample Industries","exchange":"NSE","lastPrice":215,"changePercent":0.92}]}""")
        val chat = FinwizConversation("synthetic-chat", "What is diversification?", "INDIA", topic = "PORTFOLIO_EDUCATION", messages = listOf(
            FinwizMessage("1", "user", "What is diversification?"), FinwizMessage("2", "assistant", "Diversification spreads exposure across different investments. Several stocks in one sector can still leave a portfolio concentrated. Look at your largest position and sector allocation in StoxSim.", disclaimer = "Synthetic screenshot example · Educational only")))
        val base = AppState(restoring = false, user = User("Learner", "learner@example.com", true, listOf(account)), accountId = "synthetic", portfolio = portfolio,
            recentStocks = listOf(Instrument("EXAMPLE", "Example Company", "NSE"), Instrument("SAMPLE", "Sample Industries", "NSE")),
            lists = mapOf("indices" to indices), data = mapOf("movers" to movers), chats = listOf(chat), activeChatId = chat.id)
        compose.setContent { StoxSimTheme(dark.value) { AppFrame(base.copy(screen = page.value, dark = dark.value), {}, {}) {
            when (page.value) {
                "Home" -> StocksExplore(base, {}, {})
                "Finwiz" -> FinwizHub(base, {}, { _, _, _ -> }, {}, {}, {})
                "Finwiz lesson" -> FinwizLesson(base, {}, {})
                "Finwiz chat" -> FinwizChatScreen(base, {}, {})
                "Account" -> AccountHub(base, {}, {}, {}, {}, {})
                "Wallet" -> WalletScreen(base, {}, {})
            }
        } } }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { dark.value = appearance == "dark" }
            for ((route, filename) in listOf("Home" to "stocks", "Finwiz" to "finwiz-modules", "Finwiz lesson" to "finwiz-lesson", "Finwiz chat" to "finwiz-chat", "Account" to "account", "Wallet" to "wallet")) {
                compose.runOnIdle { page.value = route }; compose.waitForIdle()
                if (route == "Home") {
                    compose.onNode(hasText("Watchlist") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertIsDisplayed()
                    compose.onNodeWithText("Example Company").assertIsDisplayed()
                    val priceBottom = compose.onNodeWithText("₹825.00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom
                    val navigationTop = compose.onNodeWithContentDescription("Open Stocks").fetchSemanticsNode().boundsInRoot.top
                    assertTrue("First mover price must fit above navigation", priceBottom <= navigationTop)
                }
                if (route == "Finwiz") compose.onAllNodesWithText("3 lessons · Start →").onFirst().assertIsDisplayed()
                capture("$filename-$appearance.png")
            }
        }
    }
    private fun capture(name: String) {
        compose.onNodeWithContentDescription("Open Stocks").assertIsDisplayed()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("The captured native screen must contain rendered content", pixels.toSet().size > 20)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview") }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
