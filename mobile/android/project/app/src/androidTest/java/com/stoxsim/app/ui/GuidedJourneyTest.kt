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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GuidedJourneyTest {
    @get:Rule val compose = createComposeRule()
    private fun state(step: String = "WAITING", dismissed: Boolean = false) = AppState(restoring = false,
        user = User("Learner", "journey@example.test", true, emptyList()), screen = "Home",
        data = mapOf("onboarding" to JSONObject().put("dismissed", dismissed), "journey" to JSONObject()
            .put("nextStep", step).put("completedSteps", 1).put("totalSteps", 6)))
    @Test fun executionChangesTheSuggestedActionAndDismissCanResume() {
        val current = mutableStateOf(state())
        var destination = ""
        compose.setContent { StoxSimTheme { AppFrame(current.value, {}, {}) {
            GuidedJourney(current.value, { destination = it }, { action -> current.value = state("FINWIZ", action == "dismiss") })
        } } }
        compose.onNodeWithText("Check open orders").performClick()
        compose.runOnIdle { assertEquals("Orders", destination); current.value = state("FINWIZ") }
        compose.onNodeWithText("Learn with Finwiz AI").performClick()
        compose.runOnIdle { assertEquals("Finwiz", destination) }
        compose.onNodeWithText("Hide guide").performClick()
        compose.onNodeWithText("Learn with Finwiz AI").assertDoesNotExist()
        compose.onNodeWithText("Resume guide").performClick()
        compose.onNodeWithText("Learn with Finwiz AI").assertIsDisplayed()
    }
    @Test fun compactLightCardsFitAndNavigate() = cards(false, 1f)
    @Test fun compactDarkLargeTextCardsFitAndNavigate() = cards(true, 1.5f)
    private fun cards(dark: Boolean, fontScale: Float) {
        var destination = ""
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                StoxSimTheme(dark) { Box(Modifier.width(320.dp)) { AppFrame(state("FINWIZ"), {}, {}) {
                    GuidedJourney(state("FINWIZ"), { destination = it }, {})
                } } }
            }
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "journey-${if (dark) "dark-large-text" else "light"}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        listOf("Finwiz AI" to "Finwiz", "Scenario Lab" to "Scenario Lab", "Learning Path" to "Learning path", "Competitions" to "Competitions").forEach { (label, route) ->
            // Bottom navigation also has these names; select the card by its description.
            val description = when (route) {
                "Finwiz" -> "Ask questions and understand risk, charges and diversification."
                "Scenario Lab" -> "Explore a market fall without changing your holdings. Review available credits before running."
                "Learning path" -> "Review achievements, choose a mission and build a daily learning habit."
                else -> "Explore seasons and private leagues. Joining is optional."
            }
            compose.onNodeWithText(description).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(route, destination) }
        }
    }
}
