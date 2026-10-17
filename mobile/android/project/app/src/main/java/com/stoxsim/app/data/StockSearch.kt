package com.stoxsim.app.data

import kotlinx.coroutines.*

/** Debounce typing and discard late responses even when a transport cannot cancel. */
internal class StockSearch(private val scope: CoroutineScope,
    private val fetch: suspend (String, String) -> List<Instrument>) {
    private var job: Job? = null
    private var generation = 0L
    fun cancel() { generation++; job?.cancel(); job = null }
    fun request(region: String, query: String, result: (List<Instrument>) -> Unit, failure: (Exception) -> Unit) {
        cancel()
        if (query.trim().length !in 2..100) return
        val request = generation
        job = scope.launch {
            try {
                delay(300)
                val found = fetch(region, query.trim())
                if (isActive && generation == request) result(found)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (isActive && generation == request) failure(error) }
        }
    }
}
