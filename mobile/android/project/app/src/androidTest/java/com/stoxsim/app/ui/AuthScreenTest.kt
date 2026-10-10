package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.AppState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun signInRequiresCredentialsAndInvokesNativeCallback() {
        var submitted = false
        compose.setContent {
            StoxSimTheme { Column { AuthScreen(AppState(restoring = false),
                { email, password, name -> submitted = email == "learner@example.com" && password == "password1" && name == null }, {}, {}) } }
        }
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
        compose.onNodeWithText("Email address").performTextInput("learner@example.com")
        compose.onNodeWithText("Password").performTextInput("password1")
        compose.onNodeWithText("Sign in").assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun recoveryUsesGenericAccountExistenceMessage() {
        var requested = false
        compose.setContent {
            StoxSimTheme { Column { AuthScreen(AppState(restoring = false), { _, _, _ -> },
                { requested = it == "learner@example.com" }, {}) } }
        }
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Email address").performTextInput("learner@example.com")
        compose.onNodeWithText("Send reset link").performClick()
        compose.runOnIdle { assertTrue(requested) }
        compose.onNodeWithText("Back to sign in").performClick()
        compose.onNodeWithText("Welcome back.").assertIsDisplayed()
    }
}
