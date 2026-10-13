package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class ProductNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val account = Account("INDIA", "Standard India", "STANDARD", "INR", BigDecimal("500000"), BigDecimal.ZERO, true, "practice")
    private fun state() = AppState(restoring = false, user = User("Learner", "learner@example.com", true, listOf(account)), accountId = account.id)

    @Test fun fourProductTabsAndTopSearchAreFunctional() {
        var route = ""; var query = ""
        val screen = mutableStateOf("Home")
        val tabs = listOf("Stock" to "Home", "Finwiz AI" to "Finwiz", "Scenario Lab" to "Scenario Lab", "Competition" to "Competitions")
        compose.setContent { StoxSimTheme { AppFrame(state().copy(screen = screen.value), { route = it; screen.value = it }, {}, { query = it }) { androidx.compose.material3.Text("Product content") } } }
        tabs.forEach { (label, destination) ->
            compose.onNodeWithContentDescription("Open $label").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(destination, route) }
            compose.onNodeWithContentDescription("Open $label").assertIsSelected()
            tabs.filter { it.first != label }.forEach { (other, _) ->
                compose.onNodeWithContentDescription("Open $other").assertIsNotSelected()
            }
            compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        }
        compose.runOnIdle { assertEquals("Home", parentScreen("Competitions")) }
        compose.onNodeWithContentDescription("Open Stock").performClick()
        compose.onNodeWithContentDescription("Stock and ETF search").performTextInput("AAPL")
        compose.onNodeWithContentDescription("Stock and ETF search").performImeAction()
        compose.runOnIdle { assertEquals("AAPL", query) }
        compose.onNodeWithContentDescription("Account settings").performClick()
        compose.runOnIdle { assertEquals("Account", route) }
    }
    @Test fun finwizLessonsKeepTheFinwizTabSelected() {
        compose.setContent { StoxSimTheme { AppFrame(state().copy(screen = "Finwiz lesson"), {}, {}) {} } }
        compose.onNodeWithContentDescription("Open Finwiz AI").assertIsSelected()
        compose.onNodeWithContentDescription("Open Competition").assertIsNotSelected()
    }
    @Test fun signedOutUsersDoNotSeeProductNavigation() {
        compose.setContent { StoxSimTheme { AppFrame(AppState(restoring = false), {}, {}) {} } }
        listOf("Stock", "Finwiz AI", "Scenario Lab", "Competition").forEach { label ->
            compose.onNodeWithContentDescription("Open $label").assertDoesNotExist()
        }
    }
    @Test fun tappingAModuleOpensTheLessonWithoutAskingAI() {
        var module = ""; var chats = 0
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FinwizHub(state(), { module = it }, { _, _, _ -> chats++ }, {}, {}, {}) } } }
        compose.onNodeWithText("Market basics").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("LEARN", module); assertEquals(0, chats) }
    }
    @Test fun lessonsAreReadableAndChatIsAnExplicitAction() {
        var read = ""; var topic = ""
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FinwizLesson(state(), { read = it }, { topic = it }) } } }
        compose.onNodeWithText("Shares & ETFs").assertIsDisplayed()
        compose.onNodeWithText("Mark module as read").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("LEARN", read); assertEquals("", topic) }
        compose.onNodeWithText("Ask Finwiz about this module").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("LEARN", topic) }
    }
    @Test fun accountHubOwnsMarketsAppearanceSubscriptionsAndWallet() {
        var route = ""; var region = ""; var dark = false
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AccountHub(state(), { route = it }, { region = it }, {}, { dark = it }, {}) } } }
        compose.onNodeWithText("🇺🇸 US · USD").performClick()
        compose.runOnIdle { assertEquals("UNITED_STATES", region) }
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dark) }
        compose.onNodeWithText("Wallet").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Wallet", route) }
        compose.onNodeWithText("Manage subscriptions").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Subscriptions", route) }
    }
    @Test fun chatShowsHistoryAndSendsOnlyWhenTheUserPressesSend() {
        var sent = ""
        val chat = FinwizConversation("chat", "Example", "INDIA", messages = listOf(FinwizMessage("q", "user", "What is an ETF?"), FinwizMessage("a", "assistant", "A fund traded on an exchange.")))
        compose.setContent { StoxSimTheme { AppFrame(state().copy(screen = "Finwiz chat", chats = listOf(chat), activeChatId = chat.id), {}, {}) { FinwizChatScreen(state().copy(chats = listOf(chat), activeChatId = chat.id), { sent = it }, {}) } } }
        compose.onNodeWithText("A fund traded on an exchange.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send question").assertIsNotEnabled()
        compose.onNodeWithText("Ask Finwiz").performTextInput("How does it trade?")
        compose.runOnIdle { assertEquals("", sent) }
        compose.onNodeWithContentDescription("Send question").performClick()
        compose.runOnIdle { assertEquals("How does it trade?", sent) }
    }
    @Test fun newStockChatPreservesTopicAndSymbol() {
        var symbol = ""; var topic = ""
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { FinwizHub(state(), {}, { t, s, _ -> topic = t; symbol = s }, {}, {}, {}) } } }
        compose.onNodeWithText("+ New chat").performClick()
        compose.onNodeWithText("Stock symbol (optional)").performTextInput("AAPL")
        compose.onNodeWithText("Start chat").performClick()
        compose.runOnIdle { assertEquals("LEARN", topic); assertEquals("AAPL", symbol) }
    }
}
