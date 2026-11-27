package com.stoxsim.app.data

import org.junit.Assert.*
import org.junit.Test

class FinwizLearningTest {
    @Test fun followUpCarriesOnlyTheReviewedConceptReference() {
        val history = listOf(
            FinwizMessage("1", "user", "Private question"),
            FinwizMessage("2", "assistant", "Private stored answer", learningReference = "price-earnings"),
            FinwizMessage("3", "error", "A transport failure")
        )
        assertEquals("price-earnings", finwizLearningReference(history))
        assertNull(finwizLearningReference(listOf(history.first())))
        assertNull(finwizLearningReference(listOf(history[1].copy(learningReference = "arbitrary previous text"))))
    }

    @Test fun sourcesRequireAnApprovedHttpsHost() {
        assertTrue(safeFinwizReference("https://www.sec.gov/education"))
        assertFalse(safeFinwizReference("https://www.sec.gov@unexpected.example/path"))
        assertFalse(safeFinwizReference("http://www.sec.gov/education"))
    }

    @Test fun modulesUseTheExistingServerTopicsAndHaveReadableLessons() {
        assertEquals(setOf("LEARN", "STOCK_FUNDAMENTALS", "TECHNICAL_ANALYSIS", "FUNDAMENTAL_ANALYSIS", "VALUATION", "CASH_FLOW", "MARKET_EVALUATION", "PORTFOLIO_EDUCATION"), learningModules.map { it.id }.toSet())
        assertTrue(learningModules.all { it.lessons.size == 3 && it.lessons.all { lesson -> lesson.first.isNotBlank() && lesson.second.isNotBlank() } && it.reference.startsWith("https://") })
    }
}
