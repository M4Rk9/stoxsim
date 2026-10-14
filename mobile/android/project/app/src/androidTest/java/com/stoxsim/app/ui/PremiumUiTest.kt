package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** All callbacks are synthetic; no production AI requests, trades, or credit-consuming runs. */
class PremiumUiTest {
    @get:Rule val compose = createComposeRule()
    private val account = Account("INDIA", "Standard India", "STANDARD", "INR", BigDecimal("9000"), BigDecimal.ZERO, true, "practice")
    private val holding = Position("EXAMPLE", "Example Company", 2, BigDecimal("1100"), "CLOSED", average = BigDecimal("500"), price = BigDecimal("550"), profit = BigDecimal("100"), returnPercent = BigDecimal("10"))
    private fun state() = AppState(restoring = false, user = User("Learner", "learner@example.com", true, listOf(account)), accountId = account.id,
        portfolio = Portfolio("INR", BigDecimal("10100"), account.cash, BigDecimal("1000"), BigDecimal("100"), "CLOSED", "2026-10-13T10:00:00Z", listOf(holding),
            capital = BigDecimal("10000"), unrealized = BigDecimal("100"), returnPercent = BigDecimal.ONE),
        lists = mapOf("scenarios" to listOf(
            JSONObject("""{"id":"recovery","title":"Fall and partial recovery","version":1,"shocks":[-20,-35,10],"lesson":"A synthetic price path."}"""),
            JSONObject("""{"id":"custom","title":"Custom price shock","version":1,"shocks":[-10],"lesson":"Build a synthetic price path."}"""))),
        data = mapOf("credits" to JSONObject("""{"remaining":2,"allowance":2,"plan":"FREE"}""")))

    @Test fun holdingsSummarySeparatesStocksFromCashAndAnalysisOpensExplicitly() {
        var opened = false
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HoldingsWorkspace(state(), {}, { opened = true }) } } }
        compose.onNodeWithText("HOLDINGS (1)").assertIsDisplayed()
        compose.onAllNodesWithText("₹1,100.00").onFirst().assertIsDisplayed()
        compose.onNodeWithText("₹1,000.00").assertIsDisplayed()
        compose.onNodeWithText("₹100.00 (+10%)").assertIsDisplayed()
        compose.onNodeWithText("1D returns").assertDoesNotExist()
        compose.onNodeWithContentDescription("Open portfolio analysis").performClick()
        compose.runOnIdle { assertTrue(opened); assertEquals("Portfolio", parentScreen("Portfolio analysis")) }
    }
    @Test fun holdingsSearchFiltersRealRowsAndOpensTheCorrectInstrument() {
        var selected: Instrument? = null
        val base = state()
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HoldingsList(base.copy(portfolio = base.portfolio!!.copy(positions = listOf(holding, holding.copy(symbol = "OTHER", name = "Other Company")))), { selected = it })
        } } }
        compose.onNodeWithContentDescription("Search holdings").performTextInput("Example")
        compose.onNodeWithText("OTHER").assertDoesNotExist()
        compose.onNodeWithText("EXAMPLE").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("EXAMPLE", selected?.symbol); assertEquals("NSE", selected?.exchange) }
    }
    @Test fun reactorSelectionDoesNotAskAIAndComposerSubmitsOnlyOnSend() {
        var topic = ""; var question = ""
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FinwizHub(state(), {}, { _, _, _ -> }, {}, {}, {}, { t, q -> topic = t; question = q })
        } } }
        compose.onNodeWithContentDescription("Select module Valuation").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Send Finwiz question").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Finwiz question").performTextInput("What is P/E?")
        compose.runOnIdle { assertEquals("", question) }
        compose.onNodeWithContentDescription("Send Finwiz question").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("VALUATION", topic); assertEquals("What is P/E?", question) }
    }
    @Test fun customScenarioEditsAreFreeAndOnlyRunSubmitsThePath() {
        var runs = 0; var path = emptyList<BigDecimal>(); var id = ""
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ScenarioWorkspace(state(), {}, {}, { definition, _, moves -> runs++; id = definition.text("id"); path = moves }, {})
        } } }
        compose.onNodeWithContentDescription("Select scenario Custom price shock").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Increase selected shock by 5 percent").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, runs) }
        compose.onNodeWithText("Run scenario · 1 credit").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, runs); assertEquals("custom", id); assertEquals(listOf("15", "25", "50"), path.map { it.stripTrailingZeros().toPlainString() }) }
    }
    @Test fun inlineChatShowsAnswersBelowTheReactorAndCanOpenItsHistory() {
        var opened = ""
        val chat = FinwizConversation("example-chat", "What is an ETF?", "INDIA", messages = listOf(
            FinwizMessage("q", "user", "What is an ETF?"), FinwizMessage("a", "assistant", "A fund traded on an exchange.")))
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            FinwizHub(state().copy(chats = listOf(chat), activeChatId = chat.id), {}, { _, _, _ -> }, { opened = it }, {}, {})
        } } }
        compose.onNodeWithText("A fund traded on an exchange.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("View full conversation →").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(chat.id, opened) }
    }
    @Test fun invalidPathsCannotRun() {
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ScenarioWorkspace(state(), {}, {}, { _, _, _ -> fail("Invalid scenario ran") }, {}) } } }
        compose.onNodeWithContentDescription("Select scenario Custom price shock").performScrollTo().performClick()
        compose.onNodeWithText("Price changes (%) separated by commas").performScrollTo().performTextClearance()
        compose.onNodeWithText("Price changes (%) separated by commas").performTextInput("101")
        compose.onNodeWithText("Run scenario · 1 credit").performScrollTo().assertIsNotEnabled()
    }
    @Test fun exhaustedCreditsCannotRun() {
        val base = state()
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ScenarioWorkspace(base.copy(data = mapOf("credits" to JSONObject("""{"remaining":0,"allowance":2,"plan":"FREE"}"""))), {}, {}, { _, _, _ -> fail("Zero-credit scenario ran") }, {})
        } } }
        compose.onNodeWithText("Run scenario · 1 credit").performScrollTo().assertIsNotEnabled()
    }
    @Test fun largeTextKeepsEveryReactorModuleReachable() {
        var selected = ""
        compose.setContent { StoxSimTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                Column(Modifier.verticalScroll(rememberScrollState())) { FinwizReactor("LEARN", emptySet(), { selected = it }, true) }
            }
        } }
        learningModules.forEach { module ->
            compose.onNodeWithContentDescription("Select module ${module.title}").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(module.id, selected) }
        }
    }
    @Test fun restoreFailureShowsBrandedRetryWithoutExposingProductNavigation() {
        var retried = false
        compose.setContent { StoxSimTheme { LaunchScreen(AppState(restoring = false, restoreFailed = true, error = "Offline")) { retried = true } } }
        compose.onNodeWithText("StoxSim").assertIsDisplayed()
        compose.onNodeWithText("Retry connection").performClick()
        compose.onNodeWithContentDescription("Open Stock").assertDoesNotExist()
        compose.runOnIdle { assertTrue(retried) }
    }
    @Test fun largeTextHeaderAndNavigationLabelsDoNotOverflow() {
        compose.setContent { StoxSimTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    AppFrame(state().copy(screen = "Finwiz"), {}, {}) { FinwizHub(state(), {}, { _, _, _ -> }, {}, {}, {}) }
                }
            }
        } }
        listOf("Finwiz AI", "LEARNING MAP", "0 of 8 modules read", "Stock", "Finwiz", "Lab", "Contest").forEach { label ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            compose.runOnIdle { assertTrue("$label must fit its text box", results.isNotEmpty() && results.none { it.hasVisualOverflow }) }
        }
    }
    @Test fun analysisGraphFitsAboveBottomNavigationOnANarrowPhone() {
        val base = state().copy(screen = "Portfolio analysis", data = mapOf("history" to JSONObject("""{"tier":"FREE","currency":"INR","status":"OBSERVED","missingSessions":0,"cashFlowBreaks":0,"points":[{"day":"2026-10-12","equity":10000},{"day":"2026-10-13","equity":10100}]}""")))
        compose.setContent { StoxSimTheme { Box(Modifier.width(320.dp).height(640.dp)) { AppFrame(base, {}, {}) { PortfolioAnalysisScreen(base, {}) } } } }
        val graph = compose.onNodeWithContentDescription("Observed portfolio history").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithContentDescription("Open Stock").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { assertTrue("The full graph must fit above navigation", graph.bottom <= navigation.top) }
    }
}
