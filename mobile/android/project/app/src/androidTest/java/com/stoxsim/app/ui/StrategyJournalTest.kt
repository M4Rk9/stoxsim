package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.stoxsim.app.AppState
import com.stoxsim.app.data.User
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Synthetic fixtures; callbacks never submit an order or contact an AI provider. */
class StrategyJournalTest {
    @get:Rule val compose = createComposeRule()
    private fun note() = JSONObject("""{"id":"strategy-fixture","reason":"Example: examine margin resilience","reflection":"","plan":{"exchange":"NSE","symbol":"EXAMPLE","horizon":"Next results","target":"Stable margins","invalidation":"Rising costs","reviewOn":"2026-12-01"}}""")

    @Test fun completionRequiresLearningAndPreservesThePlan() {
        var saved: JSONObject? = null
        var aiRequests = 0
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReflectionsScreen(AppState(restoring = false, lists = mapOf("reflections" to listOf(note()))),
                { _, body -> saved = body }, {}, { _, _ -> }, { _, _ -> aiRequests++ }, {})
        } } }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Complete review after recording what I learned").performScrollTo().performClick()
        compose.onNodeWithText("Save reflection").assertIsNotEnabled()
        compose.onNodeWithText("What I learned").performScrollTo().performTextInput("Evidence challenged my margin assumption.")
        compose.onNodeWithText("Save reflection").performClick()
        compose.runOnIdle {
            assertNotNull(saved)
            assertTrue(saved!!.getBoolean("completeReview"))
            assertEquals("EXAMPLE", saved!!.getJSONObject("plan").getString("symbol"))
            assertEquals("2026-12-01", saved!!.getJSONObject("plan").getString("reviewOn"))
            assertEquals(0, aiRequests)
        }
    }

    @Test fun linkingUsesOnlyAnExistingMatchingOrder() {
        var linked = ""
        val orders = listOf(
            JSONObject("""{"id":"matching-order","symbol":"EXAMPLE","exchange":"NSE","side":"BUY","quantity":2,"status":"EXECUTED"}"""),
            JSONObject("""{"id":"different-order","symbol":"OTHER","exchange":"NSE","side":"BUY","quantity":1,"status":"EXECUTED"}"""))
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReflectionsScreen(AppState(restoring = false, lists = mapOf("reflections" to listOf(note()), "journalOrders" to orders)),
                { _, _ -> }, {}, { strategy, order -> assertEquals("strategy-fixture", strategy); linked = order }, { _, _ -> fail("No AI consent given") }, {})
        } } }
        compose.onNodeWithText("Link an existing practice order").performScrollTo().performClick()
        compose.onNodeWithText("OTHER", substring = true).assertDoesNotExist()
        compose.onNodeWithText("EXAMPLE · buy 2 · executed", substring = true).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("matching-order", linked) }
    }

    @Test fun captureJournalLight() = capture(false)
    @Test fun captureJournalDark() = capture(true)
    private fun capture(dark: Boolean) {
        val reviewed = note().put("reflection", "Costs challenged my thesis. A gain alone would not validate it.")
            .put("reviewedAt", "2026-11-13T07:00:00Z").put("orderId", "matching-order")
            .put("linkedOrder", JSONObject("""{"side":"BUY","quantity":2,"symbol":"EXAMPLE","status":"EXECUTED","executionPrice":1500,"currency":"INR"}"""))
        val state = AppState(restoring = false, user = User("Example Learner", "learner@example.test", true, emptyList()), screen = "Reflections", dark = dark, lists = mapOf("reflections" to listOf(reviewed)))
        compose.setContent { StoxSimTheme(dark) { AppFrame(state, {}, {}) {
            Text("REVIEW FIXTURE · Synthetic data · Not production")
            ReflectionsScreen(state,
                { _, _ -> }, {}, { _, _ -> }, { _, _ -> }, {})
        } } }
        compose.onNodeWithText("Strategy journal").assertIsDisplayed()
        captureFrame("strategy-journal-${if (dark) "dark" else "light"}.png")
        compose.onNodeWithText("Practice outcome").performScrollTo()
        captureFrame("strategy-outcome-${if (dark) "dark" else "light"}.png")
    }
    private fun captureFrame(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
