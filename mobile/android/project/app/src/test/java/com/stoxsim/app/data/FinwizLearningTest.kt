package com.stoxsim.app.data

import org.junit.Assert.*
import org.junit.Test

class FinwizLearningTest {
    @Test fun followUpCarriesRecentContextWithoutErrors() {
        val history = listOf(FinwizMessage("1", "user", "Explain P/E"), FinwizMessage("2", "assistant", "Price divided by earnings per share."), FinwizMessage("3", "error", "A transport failure"))
        val request = finwizQuestion("What if earnings are negative?", history)
        assertTrue(request.contains("Explain P/E")); assertTrue(request.contains("Price divided by earnings"))
        assertFalse(request.contains("A transport failure")); assertTrue(request.endsWith("What if earnings are negative?"))
    }
    @Test fun contextFitsServerLimitAndKeepsTheLatestQuestionIntact() {
        val history = (1..10).map { FinwizMessage("$it", if (it % 2 == 0) "assistant" else "user", "Older answer $it " + "x".repeat(2000)) }
        val current = "q".repeat(1900)
        assertEquals(current, finwizQuestion(current, history))
        val short = finwizQuestion("Explain that again", history)
        assertTrue(short.length <= 2000); assertTrue(short.endsWith("Explain that again")); assertFalse(short.contains("Older answer 1 "))
    }
    @Test fun modulesUseTheExistingServerTopicsAndHaveReadableLessons() {
        assertEquals(setOf("LEARN", "STOCK_FUNDAMENTALS", "TECHNICAL_ANALYSIS", "FUNDAMENTAL_ANALYSIS", "VALUATION", "CASH_FLOW", "MARKET_EVALUATION", "PORTFOLIO_EDUCATION"), learningModules.map { it.id }.toSet())
        assertTrue(learningModules.all { it.lessons.size == 3 && it.lessons.all { lesson -> lesson.first.isNotBlank() && lesson.second.isNotBlank() } && it.reference.startsWith("https://") })
    }
}
