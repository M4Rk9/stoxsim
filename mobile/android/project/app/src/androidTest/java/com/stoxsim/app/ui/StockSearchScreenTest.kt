package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class StockSearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val stock = Instrument("BSE", "BSE LIMITED", "NSE")
    private val etf = Instrument("NIFTYBEES", "NIP IND ETF NIFTY BEES", "NSE", type = "ETF")
    private fun state() = AppState(restoring = false, user = User("Learner", "search@example.test", true, emptyList()), screen = "Markets", searchQuery = "bse", searched = true, instruments = listOf(stock, etf))
    @Test fun stockAndEtfSuggestionsFitCompactLightScreen() = results(false, 1f)
    @Test fun stockAndEtfSuggestionsFitCompactDarkLargeTextScreen() = results(true, 1.5f)
    private fun results(dark: Boolean, fontScale: Float) {
        var selected: Instrument? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StoxSimTheme(dark = dark) { Box(Modifier.width(320.dp)) {
                    AppFrame(state(), {}, {}) { StockResults(state(), { selected = it }) }
                } }
            }
        }
        compose.onNodeWithText("NSE stocks & ETFs").assertIsDisplayed()
        compose.onNodeWithText("BSE").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(stock, selected) }
        compose.onNodeWithText("NIFTYBEES").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(etf, selected) }
        val form = compose.onNodeWithContentDescription("Stock and ETF search").fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithText("BSE").fetchSemanticsNode().boundsInRoot
        assertTrue(first.top >= form.bottom)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "stock-search-${if (dark) "dark-large-text" else "light"}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun loadingAndErrorsDoNotClaimThereAreNoMatches() {
        val current = mutableStateOf(state().copy(instruments = emptyList(), searched = false, searchLoading = true))
        compose.setContent { StoxSimTheme { AppFrame(current.value, {}, {}) { StockResults(current.value, {}) } } }
        compose.onNodeWithText("Finding matches…").assertIsDisplayed()
        compose.onNodeWithText("No matching stocks or ETFs found.").assertDoesNotExist()
        compose.runOnIdle { current.value = current.value.copy(searchLoading = false, searchError = "Search unavailable. Please try again.") }
        compose.onNodeWithText("Search unavailable. Please try again.").assertIsDisplayed()
    }
}
