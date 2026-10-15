package com.stoxsim.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CompletionControlsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun orderEditsRequireValidInputAndExplicitReview() {
        var saved = ""
        compose.setContent { StoxSimTheme { EditPendingOrder(JSONObject("""{"id":"one","side":"BUY","symbol":"TEST","quantity":2,"limitPrice":100}"""), "INR", true, {}) { qty, price -> saved = "$qty:$price" } } }
        compose.onNodeWithText("Quantity (whole shares)").performTextReplacement("0")
        compose.onNodeWithText("Review changes").assertIsNotEnabled()
        compose.onNodeWithText("Quantity (whole shares)").performTextReplacement("3")
        compose.onNodeWithText("Review changes").performClick()
        assertEquals("", saved)
        compose.onNodeWithText("Confirm changes").performClick()
        assertEquals("3:100", saved)
    }
    @Test fun organizerRecoveryRequiresReasonAndEmail() {
        var submitted = false
        compose.setContent { StoxSimTheme { CampusDecisionDialog("Recover organizer", "Check affiliation", true, true, {}) { _, _ -> submitted = true } } }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Review reason").performTextInput("Reviewed official affiliation")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Verified learner email").performTextInput("learner@example.test")
        compose.onNodeWithText("Confirm").performClick()
        assertTrue(submitted)
    }
}
