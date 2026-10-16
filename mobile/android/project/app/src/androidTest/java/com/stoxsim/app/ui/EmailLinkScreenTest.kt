package com.stoxsim.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.data.EmailAction
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmailLinkScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun verificationDoesNotConsumeTokenUntilExplicitConfirmation() {
        var confirmations = 0
        compose.setContent { StoxSimTheme { EmailLinkScreen(EmailAction.VERIFY_EMAIL, false, null, { confirmations++ }, {}, { null }) } }
        assertEquals(0, confirmations)
        compose.onNodeWithText("Continue in browser").assertExists()
        compose.onNodeWithText("Verify email").performClick()
        assertEquals(1, confirmations)
    }
    @Test fun resetRequiresMatchingPasswordsAndValidUtf8Length() {
        var submitted: String? = null
        compose.setContent { StoxSimTheme { EmailLinkScreen(EmailAction.RESET_PASSWORD, false, null, { submitted = it }, {}, { null }) } }
        compose.onNodeWithText("Reset password").assertIsNotEnabled()
        compose.onNodeWithText("New password").performTextInput("password2")
        compose.onNodeWithText("Confirm new password").performTextInput("different")
        compose.onNodeWithText("Reset password").assertIsNotEnabled()
        compose.onNodeWithText("Confirm new password").performTextReplacement("password2")
        compose.onNodeWithText("Reset password").performClick()
        assertEquals("password2", submitted)
    }
}
