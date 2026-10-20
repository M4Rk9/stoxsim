package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class PricingNoticeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fallbackNoticeDisappearsWhenAnExternalQuoteArrives() {
        val source = mutableStateOf("SIMULATED")
        compose.setContent { StoxSimTheme { Column { PricingNotice(source.value) } } }
        compose.onNodeWithText("Simulated fallback price").assertIsDisplayed()
        compose.onNodeWithText("excluded from observed performance history", substring = true).assertIsDisplayed()
        compose.runOnIdle { source.value = "EXTERNAL" }
        compose.onNodeWithText("Simulated fallback price").assertDoesNotExist()
        compose.runOnIdle { source.value = "UNKNOWN" }
        compose.onNodeWithText("Price source unconfirmed").assertIsDisplayed()
    }
}
