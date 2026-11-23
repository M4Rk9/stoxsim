package com.stoxsim.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import com.stoxsim.app.data.learnerDestination
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.AppState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LearnerExperienceUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun journalReminderOpensNativeJournalLight() = journalReminder(false)
    @Test fun journalReminderOpensNativeJournalDark() = journalReminder(true)
    private fun journalReminder(dark: Boolean) {
        var destination: String? = null
        val inbox = JSONObject("""{"unreadCount":1,"items":[{"id":"synthetic-reminder","category":"PORTFOLIO_EDUCATION","title":"Time to review your strategy","body":"Synthetic fixture: compare your thesis with what happened and what you learned; profit alone does not measure a good decision.","href":"/journal","createdAt":"2026-11-22T00:00:00Z"}]}""")
        compose.setContent { StoxSimTheme(dark) { Surface { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("SYNTHETIC FIXTURE")
            NotificationsScreen(AppState(restoring = false, data = mapOf("notifications" to inbox)), { _, href -> destination = href?.let(::learnerDestination) }, {}, { _, _ -> })
        } } } }
        compose.onNodeWithText("Open notification").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Reflections", destination) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "journal-reminder-${if (dark) "dark" else "light"}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StoxSimReview")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
    @Test fun readingReflectionNeverSharesWithAIUntilExplicitConfirmation() {
        var requests = 0
        val item = JSONObject("""{"id":"00000000-0000-0000-0000-000000000001","reason":"Review my assumptions","reflection":"I learned patience"}""")
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReflectionsScreen(AppState(restoring = false, lists = mapOf("reflections" to listOf(item))), { _, _ -> }, {}, { _, _ -> }, { _, consent -> assertTrue(consent); requests++ }, {})
        } } }
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Ask Finwiz about this reflection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Keep private").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("Ask Finwiz about this reflection").performScrollTo().performClick()
        compose.onNodeWithText("Share and ask Finwiz").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }
    @Test fun learningHubKeepsAllExistingLearningToolsAvailable() {
        var route = ""
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { LearnHub(AppState(), { route = it }) } } }
        listOf("Ask Finwiz" to "Finwiz", "Continue learning missions" to "Learning path", "Explore a portfolio scenario" to "Scenario Lab", "Strategy journal and reviews" to "Reflections").forEach { (label, target) ->
            compose.onNodeWithText(label).performScrollTo().performClick()
            compose.runOnIdle { assertEquals(target, route) }
        }
    }
}
