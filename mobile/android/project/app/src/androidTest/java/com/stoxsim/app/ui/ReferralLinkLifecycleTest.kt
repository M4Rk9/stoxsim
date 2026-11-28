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

class ReferralLinkLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun invitationSurvivesColdStartAndOpensRegistration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val invite = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("https://stoxsim.com/invite?ref=ABCDEFGHIJKLMNOP"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(invite)
        try {
            compose.waitUntil(10_000) {
                runCatching { compose.onAllNodesWithText("Invite code applied.").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
            }
            compose.onNodeWithText("Create account").assertExists()
            compose.runOnIdle {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().first()
                assertNull(activity.intent.data)
            }
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.finish() }
            }
        }
    }
}
