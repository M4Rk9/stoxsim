package com.stoxsim.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.stoxsim.app.MainActivity
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class EmailLinkLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun resumedActivity(): MainActivity? {
        var result: MainActivity? = null
        instrumentation.runOnMainSync {
            result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull()
        }
        return result
    }
    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    @Test fun coldAndWarmLinksRouteWithoutSubmittingAndSurviveRotationWithoutSavingPasswords() {
        val context = instrumentation.targetContext
        fun link(path: String) = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://stoxsim.com/$path?token=" + "s".repeat(43)))
        // ActivityScenario matches lifecycle events by the original Intent. This activity
        // deliberately scrubs that secret Intent, so observe its component's lifecycle instead.
        context.startActivity(link("verify-email").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            waitForText("Verify email")
            val cold = resumedActivity()!!
            compose.runOnIdle { assertNull(cold.intent.data); cold.startActivity(link("reset-password")) }
            waitForText("New password")
            compose.onNodeWithText("New password").performTextInput("password2")
            compose.onNodeWithText("Confirm new password").performTextInput("password2")
            compose.onNodeWithText("Reset password").assertIsEnabled()
            val beforeRotation = resumedActivity()!!
            compose.runOnIdle { beforeRotation.recreate() }
            compose.waitUntil(10_000) { resumedActivity()?.let { it !== beforeRotation } == true }
            waitForText("Reset password")
            compose.onNodeWithText("Reset password").assertIsNotEnabled()
            compose.onNodeWithText("Continue in browser").assertExists()
            val rotated = resumedActivity()!!
            compose.runOnIdle { assertNull(rotated.intent.data) }
        } finally {
            resumedActivity()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
        }
    }
}
