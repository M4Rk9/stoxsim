package com.stoxsim.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

/** Synthetic account data: no production mutations, credentials, AI requests, or scenario credits. */
class WebParityScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrument = Instrument("ABC", "ABC Company", "NSE", "INDIA", "instrument-id")
    private val account = Account("INDIA", "Standard India", "STANDARD", "INR", BigDecimal("500000"), BigDecimal.ZERO, true, "account-id")
    private fun state(active: Boolean = true) = AppState(restoring = false, user = User("Learner", "learner@example.com", true, listOf(account.copy(active = active))), accountId = account.id, selected = instrument)

    @Test fun orderReviewAndConfirmationSubmitOnlyTheSelectedAccount() {
        var submitted: OrderDraft? = null
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { OrderTicket(state(), {}, { submitted = it }) } } }
        compose.onNodeWithText("Quantity (whole shares)").performTextClearance()
        compose.onNodeWithText("Quantity (whole shares)").performTextInput("3")
        compose.onNodeWithText("Review buy order").performScrollTo().performClick()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithText("Confirm practice order").assertIsDisplayed()
        compose.onNodeWithText("Place practice order").performClick()
        compose.runOnIdle { assertEquals("account-id", submitted?.accountId); assertEquals(3L, submitted?.quantity); assertEquals("BUY", submitted?.side) }
    }
    @Test fun lockedSandboxCannotPlaceAnOrder() {
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { OrderTicket(state(false), {}, {}) } } }
        compose.onNodeWithText("Review buy order").performScrollTo().assertIsNotEnabled()
    }
    @Test fun wholeSharesAndPositiveLimitAreRequired() {
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { OrderTicket(state(), {}, {}) } } }
        compose.onNodeWithText("Quantity (whole shares)").performTextClearance()
        compose.onNodeWithText("Quantity (whole shares)").performTextInput("1.5")
        compose.onNodeWithText("Review buy order").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Quantity (whole shares)").performTextClearance()
        compose.onNodeWithText("Quantity (whole shares)").performTextInput("2")
        compose.onNodeWithText("Limit", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Limit price (INR)").performTextInput("-1")
        compose.onNodeWithText("Review buy order").performScrollTo().assertIsNotEnabled()
    }
    @Test fun nativeMarketResultOpensSelectedStock() {
        var selected: Instrument? = null
        compose.setContent { StoxSimTheme { Column(Modifier.verticalScroll(rememberScrollState())) { StockResults(state().copy(instruments = listOf(instrument), searched = true), { selected = it }) } } }
        compose.onNodeWithText("ABC").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(instrument, selected) }
    }
    @Test fun phoneNavigationExposesCoreWebsiteDestinations() {
        var destination = ""
        compose.setContent { StoxSimTheme { AppFrame(state(), { destination = it }, {}) { PortfolioMetrics(state()) } } }
        compose.onNodeWithText("Orders").performClick()
        compose.runOnIdle { assertEquals("Orders", destination) }
        compose.onNodeWithContentDescription("Account settings").performClick()
        compose.runOnIdle { assertEquals("Account", destination) }
    }
    @Test fun darkThemeUsesWebsiteColours() {
        var background = 0L; var primary = 0L; var selected = 0L
        compose.setContent { StoxSimTheme(dark = true) {
            background = androidx.compose.material3.MaterialTheme.colorScheme.background.value.toLong()
            primary = androidx.compose.material3.MaterialTheme.colorScheme.primary.value.toLong()
            selected = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer.value.toLong()
            Column { androidx.compose.material3.Text("Shared StoxSim theme") }
        } }
        compose.runOnIdle { assertEquals(androidx.compose.ui.graphics.Color(0xFF121212).value.toLong(), background); assertEquals(androidx.compose.ui.graphics.Color(0xFF00D09C).value.toLong(), primary); assertEquals(androidx.compose.ui.graphics.Color(0xFF17332B).value.toLong(), selected) }
    }
}
