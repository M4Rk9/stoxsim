package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.stoxsim.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DerivativesScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun preview() = JSONObject("""{"legs":[{"contract":{"id":"fixture","underlyingKey":"NSE_INDEX|Nifty 50","symbol":"EXAMPLE CALL","kind":"CE","expiry":"2026-12-29","lotSize":65,"providerKey":"SYNTHETIC","strike":27000},"action":"OPEN_LONG","lots":1,"quantity":65,"price":81,"quoteAt":"2026-11-20T05:30:00Z"}],"requiredFunds":9230,"cashAfter":490770,"payoff":{"maximumLoss":9230,"unlimitedLoss":false,"maximumProfit":null,"unlimitedProfit":true,"breakevens":[26858,27142],"zeroRanges":[],"points":[{"underlying":0,"pnl":1745770},{"underlying":27000,"pnl":-9230},{"underlying":33750,"pnl":429520}]}}""").apply {
        val put = JSONObject(getJSONArray("legs").getJSONObject(0).toString()).put("price", 61)
        put.getJSONObject("contract").put("id", "fixture-put").put("symbol", "EXAMPLE PUT").put("kind", "PE")
        getJSONArray("legs").put(put)
    }
    @Test fun reviewRequiresAcknowledgementAndMatchingComposition() {
        var submissions = 0
        compose.setContent {
            var r by remember { mutableStateOf(DerivativeReview("original", preview(), Long.MAX_VALUE)) }
            StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                DerivativeRiskReview(r, "original", true, false, { r = r.copy(accepted = it) }) { submissions++ }
            } }
        }
        compose.onNodeWithText("Place virtual basket").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Place virtual basket").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, submissions) }
    }
    @Test fun expiredReviewCannotSubmitEvenWhenAcknowledged() {
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            DerivativeRiskReview(DerivativeReview("old", preview(), 0, true), "new", true, false, {}) { fail("Expired review submitted") }
        } } }
        compose.onNodeWithText("Place virtual basket").performScrollTo().assertIsNotEnabled()
    }
    @Test fun unavailableCapabilityDoesNotOfferTrading() {
        compose.setContent { StoxSimTheme { Column { DerivativesScreen(DerivativesState(enabled = false), false, { _, _, _ -> }, {}, {}, {}, {}, {}, {}) } } }
        compose.onNodeWithText("F&O practice is unavailable on this server.").assertExists()
        compose.onNodeWithText("Review basket risk").assertDoesNotExist()
    }
    @Test fun disabledNewsIsHonest() {
        compose.setContent { StoxSimTheme { CompanyNews(JSONObject().put("status", "DISABLED"), null, false) } }
        compose.onNodeWithText("News is not enabled for this market yet.").assertExists()
        compose.onNodeWithText("Read original article ↗").assertDoesNotExist()
    }
    @Test fun captureDiscoveryAndNews() {
        val c = preview().getJSONArray("legs").getJSONObject(0).getJSONObject("contract")
        val d = DerivativesState(enabled = true, expiry = "2026-12-29", expiries = listOf("2026-12-29"),
            chain = listOf(JSONObject().put("contract", c).put("last", 80).put("fresh", false).put("lastTradeAt", "2026-11-20T05:30:00Z")))
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("SYNTHETIC FIXTURE · Not market data")
            DerivativesScreen(d, false, { _, _, _ -> }, {}, {}, {}, {}, {}, {})
            CompanyNews(JSONObject("""{"status":"AVAILABLE","provider":"Synthetic fixture","checkedAt":"2026-11-20T05:30:00Z","articles":[{"headline":"Example company publishes results","source":"example.test","url":"https://example.test/news","publishedAt":"2026-11-20T04:00:00Z"}]}"""), null, false)
        } } }
        compose.onNodeWithText("Open long EXAMPLE CALL").performScrollTo().assertIsNotEnabled()
        save("android-fno-discovery.png")
        compose.onNodeWithText("Read original article ↗").performScrollTo()
        save("android-company-news.png")
    }
    @Test fun captureLightPayoff() = capture(false)
    @Test fun captureDarkPayoff() = capture(true)
    private fun capture(dark: Boolean) {
        compose.setContent { StoxSimTheme(dark) { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("SYNTHETIC FIXTURE · Not market data")
            DerivativeRiskReview(DerivativeReview("fixture", preview(), Long.MAX_VALUE), "fixture", true, false, {}) {}
        } } }
        save("android-fno-review-${if (dark) "dark" else "light"}.png")
        compose.onNodeWithText("Payoff values · Calculated scenarios").performScrollTo()
        save("android-fno-payoff-${if (dark) "dark" else "light"}.png")
    }
    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview") }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
