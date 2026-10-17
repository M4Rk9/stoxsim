package com.stoxsim.app.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StockSearchTest {
    @Test fun typingDebouncesAndShortQueriesDoNotFetch() = runTest {
        val calls = mutableListOf<String>(); var result = ""
        val search = StockSearch(this) { region, query -> calls.add("$region:$query"); listOf(Instrument(query, query, "NSE")) }
        fun type(query: String) = search.request("INDIA", query, { result = it.single().symbol }, { throw it })
        type("r"); advanceUntilIdle(); assertTrue(calls.isEmpty())
        type("re"); advanceTimeBy(200); type("rel"); advanceTimeBy(299); assertTrue(calls.isEmpty())
        advanceUntilIdle(); assertEquals(listOf("INDIA:rel"), calls); assertEquals("rel", result)
    }
    @Test fun staleResponsesCannotOverwriteNewQueryOrReappearAfterCancel() = runTest {
        val slow = CompletableDeferred<Unit>(); val results = mutableListOf<String>()
        val search = StockSearch(this) { _, query ->
            if (query == "old") withContext(NonCancellable) { slow.await() }
            listOf(Instrument(query, query, "NSE"))
        }
        search.request("INDIA", "old", { results.add(it.single().symbol) }, { throw it })
        advanceTimeBy(301)
        search.request("INDIA", "new", { results.add(it.single().symbol) }, { throw it })
        advanceTimeBy(301); slow.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("new"), results)
        search.request("INDIA", "cancelled", { results.add(it.single().symbol) }, { throw it })
        search.cancel(); advanceUntilIdle(); assertEquals(listOf("new"), results)
    }
    @Test fun failuresAreRetryableAndRegionSwitchCancelsPendingWork() = runTest {
        var calls = 0; var error = false; var exchange = ""
        val search = StockSearch(this) { region, _ ->
            calls++; if (calls == 1) throw java.io.IOException("offline")
            listOf(Instrument("SPY", "ETF", "NYSE_ARCA", region, type = "ETF"))
        }
        search.request("INDIA", "rel", {}, { error = true }); advanceUntilIdle(); assertTrue(error)
        search.request("INDIA", "rel", { fail("Wrong market") }, { throw it })
        search.request("UNITED_STATES", "spy", { exchange = it.single().exchange }, { throw it }); advanceUntilIdle()
        assertEquals("NYSE_ARCA", exchange); assertEquals(2, calls)
    }
}
