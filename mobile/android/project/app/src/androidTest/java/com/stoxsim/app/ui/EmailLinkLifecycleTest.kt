package com.stoxsim.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.stoxsim.app.MainActivity
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EmailLinkLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun coldAndWarmLinksRouteWithoutSubmittingAndSurviveRotationWithoutSavingPasswords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun link(path: String) = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://stoxsim.com/$path?token=" + "s".repeat(43)))
        ActivityScenario.launch<MainActivity>(link("verify-email")).use { scenario ->
            compose.onNodeWithText("Verify email").assertIsDisplayed()
            scenario.onActivity { assertNull(it.intent.data) }
            scenario.onActivity { it.startActivity(link("reset-password")) }
            compose.onNodeWithText("New password").assertIsDisplayed().performTextInput("password2")
            compose.onNodeWithText("Confirm new password").performTextInput("password2")
            compose.onNodeWithText("Reset password").assertIsEnabled()
            scenario.recreate()
            compose.onNodeWithText("Reset password").assertIsNotEnabled()
            compose.onNodeWithText("Continue in browser").assertExists()
            scenario.onActivity { assertNull(it.intent.data) }
        }
    }
}
