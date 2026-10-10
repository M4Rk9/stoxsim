package com.stoxsim.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import com.stoxsim.app.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualLauncherCreatesViewModelAndShowsNativeSignIn() {
        // Fresh test installation has no session; startup needs neither a browser nor a backend request.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Welcome back.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("StoxSim").assertIsDisplayed()
        compose.onNodeWithText("Welcome back.").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsNotEnabled()
    }
}
