package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FundamentalsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val stock = Instrument("EXAMPLE", "Example Company", "BSE")
    private val description = "Example Company makes equipment for customers in several industries. " +
        "Its operations include manufacturing and support services in different regions. ".repeat(8)
    private fun insights(period: String = "quarterly"): JSONObject {
        val history = if (period == "yearly") """
          {"period":"Mar 2026","value":390},{"period":"Mar 2025","value":360},{"period":"Mar 2024","value":320},{"period":"Mar 2023","value":280},{"period":"Mar 2022","value":250}
        """ else """
          {"period":"Jun 2026","value":120},{"period":"Mar 2026","value":110},{"period":"Dec 2025","value":100},{"period":"Sep 2025","value":90}
        """
        val profits = if (period == "yearly") """
          {"period":"Mar 2026","value":30},{"period":"Mar 2025","value":25},{"period":"Mar 2024","value":20},{"period":"Mar 2023","value":15},{"period":"Mar 2022","value":10}
        """ else """
          {"period":"Jun 2026","value":12},{"period":"Dec 2025","value":-5},{"period":"Sep 2025","value":0}
        """
        return JSONObject("""{"provider":"SYNTHETIC_TEST","status":"AVAILABLE","asOf":"2026-10-11T08:00:00Z",
          "profile":{"sector":"Manufacturing"},"ratios":[{"name":"P/E","companyValue":"17.97","sectorValue":"21.5"},
          {"name":"P/B","companyValue":"1.75"},{"name":"ROA","companyValue":"4.04%"},
          {"name":"ROE","companyValue":"8.94%"},{"name":"ROCE","companyValue":"10.39%"},{"name":"Quick ratio","companyValue":"0.79"}],
          "financials":{"timePeriod":"$period","statementType":"Consolidated","unitsIn":"crore","metrics":[
          {"category":"revenue","history":[$history]},{"category":"net_profit","history":[$profits]},
          {"category":"operating_profit","history":[{"period":"Jun 2026","value":16}]}]}}""").also {
            it.getJSONObject("profile").put("description", description)
        }
    }
    private fun state(period: String = "quarterly") = AppState(restoring = false, selected = stock, insightPeriod = period, data = mapOf("stockInsights" to insights(period)))
    private fun scrollContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { StoxSimTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() } } }
    }
    @Test fun watchlistButtonAcceptsBseStocksWithoutLocalIdsAndRecognisesAddedItems() {
        var added: Instrument? = null
        val data = mutableStateOf(AppState(restoring = false))
        compose.setContent { StoxSimTheme { WatchStockButton(data.value, stock) { added = it } } }
        compose.onNodeWithText("☆ Add to watchlist").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(stock, added)
            data.value = data.value.copy(data = mapOf("watchlist" to JSONObject("""{"items":[{"symbol":"EXAMPLE","exchange":"BSE","marketRegion":"INDIA"}]}""")))
        }
        compose.onNodeWithText("★ Watching").assertIsNotEnabled()
    }
    @Test fun metricCardsAndDescriptionHaveACompactDefaultAndExpandOnDemand() {
        scrollContent { CompanyFundamentals(state(), {}) }
        compose.onNodeWithText("P/E").assertIsDisplayed()
        compose.onNodeWithText("17.97").assertIsDisplayed()
        compose.onNodeWithText("Sector 21.5").assertIsDisplayed()
        compose.onNodeWithText(normalizedDescription(description)).assertDoesNotExist()
        compose.onNodeWithText(companyExcerpt(description)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read more").performScrollTo().performClick()
        compose.onNodeWithText(normalizedDescription(description)).assertExists()
        compose.onNodeWithText("Show less").performScrollTo().performClick()
        compose.onNodeWithText(normalizedDescription(description)).assertDoesNotExist()
    }
    @Test fun yearlySelectionChangesTheChartAndPeriodInspection() {
        val period = mutableStateOf("quarterly")
        scrollContent { CompanyFundamentals(state(period.value), { period.value = it }) }
        compose.onNodeWithText("Yearly").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("yearly", period.value) }
        compose.onNodeWithContentDescription("Revenue and net profit bar chart, Yearly").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("2022").performScrollTo().performClick()
        compose.onNodeWithText("Mar 2022").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹250 Cr").assertExists()
        compose.onNodeWithText("₹10 Cr").assertExists()
    }
    @Test fun missingProfitIsNotZeroAndOtherFinancialMetricsRemainAvailable() {
        scrollContent { CompanyFundamentals(state(), {}) }
        compose.onNodeWithText("Mar\n2026").performScrollTo().performClick()
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithText("Show financial details").performScrollTo().performClick()
        compose.onNodeWithText("Operating profit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₹16 Cr").assertExists()
    }
    @Test fun mismatchedProviderHistoryIsNotLabelledYearly() {
        scrollContent { CompanyFundamentals(state().copy(insightPeriod = "yearly"), {}) }
        compose.onNodeWithText("The provider returned quarterly data", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Revenue and net profit bar chart, Yearly").assertDoesNotExist()
    }
    @Test fun captureMetricCardsAndBothFinancialPeriodsInLightAndDark() {
        val dark = mutableStateOf(false); val period = mutableStateOf("quarterly")
        val user = User("Learner", "learner@example.com", true, emptyList())
        compose.setContent { StoxSimTheme(dark.value) { AppFrame(state(period.value).copy(user = user, screen = "Stock"), {}, {}) {
            CompanyFundamentals(state(period.value), { period.value = it })
        } } }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle { dark.value = appearance == "dark"; period.value = "quarterly" }
            compose.onNodeWithText("Company fundamentals").performScrollTo(); capture("fundamentals-$appearance.png")
            for (value in listOf("quarterly", "yearly")) {
                compose.runOnIdle { period.value = value }
                compose.onNodeWithContentDescription("Revenue and net profit bar chart, ${if (value == "yearly") "Yearly" else "Quarterly"}").performScrollTo()
                capture("financials-$value-$appearance.png")
            }
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.toSet().size > 20)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview") }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
