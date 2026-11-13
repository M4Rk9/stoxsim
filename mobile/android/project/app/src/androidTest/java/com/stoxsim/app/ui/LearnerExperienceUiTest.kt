package com.stoxsim.app.ui

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
