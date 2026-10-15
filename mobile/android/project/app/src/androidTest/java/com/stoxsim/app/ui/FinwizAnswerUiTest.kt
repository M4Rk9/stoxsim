package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.AppState
import com.stoxsim.app.data.FinwizConversation
import com.stoxsim.app.data.FinwizMessage
import org.junit.Rule
import org.junit.Test

class FinwizAnswerUiTest {
    @get:Rule val compose = createComposeRule()
    private val conversation = FinwizConversation("chat", "Learning", "INDIA", messages = listOf(
        FinwizMessage("question", "user", "Explain **P/E**"),
        FinwizMessage("answer", "assistant", "### P/E ratio\n\n**Price** divided by earnings.\n\n* Check profits\n* Compare companies")
    ))
    private val state = AppState(restoring = false, chats = listOf(conversation), activeChatId = "chat")

    @Test fun inlineAnswerIsFormattedAndComposerHasNoVerificationTagline() {
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FinwizHub(state, {}, { _, _, _ -> }, {}, {}, {})
        } } }
        compose.onNodeWithText("P/E ratio\n\nPrice divided by earnings.\n\n• Check profits\n• Compare companies").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Educational AI · Verify answers").assertDoesNotExist()
        compose.onNodeWithText("Explain **P/E**").assertExists() // User input stays literal.
    }

    @Test fun fullChatUsesTheSameNativeFormattingInDarkMode() {
        compose.setContent { StoxSimTheme(dark = true) { FinwizChatScreen(state, {}, {}) } }
        compose.onNodeWithText("P/E ratio\n\nPrice divided by earnings.\n\n• Check profits\n• Compare companies").assertIsDisplayed()
        compose.onNodeWithText("Educational AI · Verify answers · Recent context is limited").assertDoesNotExist()
    }
}
