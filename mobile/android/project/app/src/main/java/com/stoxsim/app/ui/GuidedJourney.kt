package com.stoxsim.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*

internal data class JourneyStep(val title: String, val body: String, val action: String, val route: String)
internal fun journeyStep(step: String) = when (step) {
    "INTRODUCTION" -> JourneyStep("Get to know your home screen", "Your balance is virtual cash. Market movers help you explore, search finds stocks and ETFs, and the four feature cards below open your learning tools.", "Start practising", "Markets")
    "FIRST_TRADE" -> JourneyStep("Make your first practice trade", "Search for a stock or ETF, inspect its quote, then review quantity and charges before placing an order in a standard account.", "Find a stock or ETF", "Markets")
    "WAITING" -> JourneyStep("Your order is waiting to execute", "Submission is not execution. Closed markets or limit prices can keep orders open. Review the status; your guide advances after execution. Explore Finwiz while you wait.", "Check open orders", "Orders")
    "FINWIZ" -> JourneyStep("First trade executed. Understand what happened.", "Open Finwiz, choose a topic and send a question. Try: How do charges and diversification affect my first trade?", "Learn with Finwiz AI", "Finwiz")
    "SCENARIO" -> JourneyStep("Put your learning to the test", "Select the portfolio holding your trade in Scenario Lab. Choose Market sell-off, review credits and run it. Compare cash and holdings in the result.", "Try Scenario Lab", "Scenario Lab")
    "LEARNING" -> JourneyStep("Choose your next learning mission", "Review your progress and choose a mission. Your daily visit is recorded automatically.", "Open Learning Path", "Learning path")
    "COMPETITIONS" -> JourneyStep("Discover how to practise together", "Explore the current season, standings and private leagues. Read the rules before deciding to join. Enrolment is optional.", "Explore Competitions", "Competitions")
    else -> JourneyStep("Your next visit, made useful", "Review your portfolio, ask Finwiz about something you noticed, or continue a learning mission. Check credits before running another scenario.", "Continue learning", "Learning path")
}

@Composable internal fun GuidedJourney(state: AppState, navigate: (String) -> Unit, onboardingAction: (String) -> Unit) {
    val progress = state.data["journey"]
    val dismissed = state.data["onboarding"]?.optBoolean("dismissed") == true
    if (dismissed) TextButton({ onboardingAction("resume") }, enabled = !state.working) { Text("Resume guide") }
    else if (progress != null && progress.has("nextStep")) {
        val step = journeyStep(progress.text("nextStep"))
        Panel("${progress.optInt("completedSteps")} OF ${progress.optInt("totalSteps", 6)} STEPS EXPLORED", step.title) {
            Text(step.body)
            Button({ if (progress.text("nextStep") == "INTRODUCTION") onboardingAction("introduction/complete") else navigate(step.route) },
                enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text(step.action) }
            TextButton({ onboardingAction("dismiss") }, enabled = !state.working) { Text("Hide guide") }
        }
    }
    SectionError(state, "journey")
    Text("Explore StoxSim", style = MaterialTheme.typography.titleLarge)
    // One column leaves enough room for descriptions and large system font sizes.
    listOf(
        Triple("Finwiz", "Finwiz AI", "Ask questions and understand risk, charges and diversification."),
        Triple("Scenario Lab", "Scenario Lab", "Explore a market fall without changing your holdings. Review available credits before running."),
        Triple("Learning path", "Learning Path", "Review achievements, choose a mission and build a daily learning habit."),
        Triple("Competitions", "Competitions", "Explore seasons and private leagues. Joining is optional.")
    ).forEachIndexed { index, (route, title, description) ->
        val keys = listOf("finwizAsked", "scenarioRun", "learningViewed", "competitionsViewed")
        OutlinedCard(onClick = { navigate(route) }, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description)
                Text(if (progress?.optBoolean(keys[index]) == true) "Explored · Open again →" else "Explore →", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun FeatureWalkthrough(state: AppState, navigate: (String) -> Unit) {
    if (state.data["onboarding"]?.optBoolean("dismissed") == true) return
    val copy = when (state.screen) {
        "Markets" -> if (state.data["journey"]?.optBoolean("firstTradeExecuted") != true) "Type at least two characters, select a matching stock or ETF, and inspect its quote before trading." else return
        "Stock", "Orders" -> if (state.data["journey"]?.optBoolean("firstTradeExecuted") != true) "Review the quote, choose a quantity and check simulated charges. Place an order only when you are ready; an open order is still waiting to execute." else "Your first practice trade has executed. Continue with Finwiz to understand charges, risk and diversification."
        "Finwiz" -> "Choose a learning topic, read a lesson, or send a question below. After your first trade, ask how charges and diversification affect your portfolio."
        "Scenario Lab" -> "Select the portfolio holding your executed trade. Choose a scenario, review the preview and credit cost, then run it and compare the projected cash and holdings."
        "Learning path" -> "Review your completed missions and choose one to work on next. Your daily visit is recorded automatically."
        "Competitions" -> "Review the current season and rules. You can explore without enrolling; joining a season or private league is optional."
        else -> return
    }
    Panel("YOUR GUIDE") {
        Text(copy)
        val journey = state.data["journey"]
        val next = journey?.text("nextStep")?.let(::journeyStep)
        if (next != null && next.route != state.screen && journey?.optBoolean("firstTradeExecuted") == true)
            TextButton({ navigate(next.route) }) { Text("Next: ${next.action} →") }
        TextButton({ navigate("Home") }) { Text("Back to your journey →") }
    }
}
