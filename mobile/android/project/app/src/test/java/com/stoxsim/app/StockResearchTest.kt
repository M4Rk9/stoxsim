package com.stoxsim.app

import com.stoxsim.app.data.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class StockResearchTest {
    private val loaded = AppState(screen = "Stock", selected = Instrument("RELIANCE", "Reliance", "NSE"),
        quote = Quote(BigDecimal.TEN, null, "CLOSED", "", null, null, null, null))

    @Test fun stockResearchRequiresASuccessfullyLoadedVisibleQuote() {
        assertTrue(loaded.hasLoadedStockResearch())
        assertFalse(loaded.copy(quote = null).hasLoadedStockResearch())
        assertFalse(loaded.copy(selected = null).hasLoadedStockResearch())
        assertFalse(loaded.copy(working = true).hasLoadedStockResearch())
        assertFalse(loaded.copy(screen = "Home").hasLoadedStockResearch())
        assertFalse(loaded.copy(sectionErrors = mapOf("quote" to "Offline")).hasLoadedStockResearch())
        // Optional fundamentals/chart failures do not erase successfully loaded research.
        assertTrue(loaded.copy(sectionErrors = mapOf("chart" to "Unavailable")).hasLoadedStockResearch())
    }
}
