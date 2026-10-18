package com.stoxsim.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stoxsim.app.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.math.RoundingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID

internal data class EmailLinkPrompt(val action: EmailAction, val id: String = UUID.randomUUID().toString())

internal data class AppState(
    val restoring: Boolean = true, val restoreFailed: Boolean = false,
    val working: Boolean = false, val user: User? = null,
    val region: String = "INDIA", val accountId: String = "", val screen: String = "Home",
    val dark: Boolean = false, val streamStatus: String = "OFFLINE", val portfolio: Portfolio? = null,
    val instruments: List<Instrument> = emptyList(), val searched: Boolean = false,
    val searchLoading: Boolean = false, val searchError: String? = null,
    val selected: Instrument? = null, val quote: Quote? = null, val candles: List<Candle> = emptyList(),
    val chartMonths: Long = 1, val insightPeriod: String = "quarterly",
    val data: Map<String, JSONObject> = emptyMap(), val lists: Map<String, List<JSONObject>> = emptyMap(),
    val sectionErrors: Map<String, String> = emptyMap(), val export: String? = null,
    val searchQuery: String = "", val recentStocks: List<Instrument> = emptyList(),
    val chats: List<FinwizConversation> = emptyList(), val activeChatId: String? = null,
    val learningModule: String = "LEARN", val readModules: Set<String> = emptySet(),
    val error: String? = null, val notice: String? = null
) {
    val account: Account? get() = user?.accounts?.find { it.id == accountId }
    val activeChat: FinwizConversation? get() = chats.find { it.id == activeChatId }
    val currency: String get() = account?.currency ?: if (region == "INDIA") "INR" else "USD"
}

internal class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val api = (application as StoxSimApplication).api
    private val stockSearch = StockSearch(viewModelScope, api::search)
    private fun cancelSearch() { stockSearch.cancel(); mutable.update { it.copy(searchLoading = false) } }
    private val preferences = application.getSharedPreferences("appearance", 0)
    private val mutable = MutableStateFlow(AppState(dark = preferences.getBoolean("last-dark", false)))
    val state = mutable.asStateFlow()
    // Retain a write's key after an ambiguous network failure. Explicit retries reuse it.
    private var pendingOrder: Pair<OrderDraft, String>? = null
    private var pendingScenario: Pair<String, String>? = null
    private var pendingSandbox: String? = null
    private var pagePending = false
    private var journeyJob: Job? = null
    private var foreground = false
    private var streamJob: Job? = null
    private var streakJob: Job? = null
    private var streakTracker = DailyStreakTracker()
    private var activityTracker = ProductActivityTracker()
    private var activityJob: Job? = null
    private var pendingEmailLink: EmailActionLink? = null
    private val emailAction = MutableStateFlow<EmailLinkPrompt?>(null)
    val emailLinkAction = emailAction.asStateFlow()

    fun receiveEmailLink(raw: String?) {
        val link = EmailActionLink.parse(raw)
        if (link == null) {
            pendingEmailLink = null; emailAction.value = null
            mutable.update { it.copy(error = "This StoxSim email link is invalid. Request a new link and check Inbox and Spam/Junk.") }
            return
        }
        pendingEmailLink = link
        emailAction.value = EmailLinkPrompt(link.action)
        clearMessage()
    }
    fun dismissEmailLink() { pendingEmailLink = null; emailAction.value = null; clearMessage() }
    fun emailBrowserUrl(): String? = pendingEmailLink?.browserUrl()
    fun completeEmailLink(password: String? = null) = runAction {
        val link = pendingEmailLink ?: return@runAction
        try { api.completeEmailLink(link, password) }
        catch (failure: ApiFailure) {
            mutable.update { it.copy(error = if (failure.status == 401) "This email link is invalid, expired or already used. Request a new link and check Inbox and Spam/Junk." else failure.message) }
            return@runAction
        }
        if (pendingEmailLink === link) { pendingEmailLink = null; emailAction.value = null }
        if (link.action == EmailAction.RESET_PASSWORD) {
            cancelSearch()
            mutable.value = AppState(restoring = false, working = true, notice = "Password reset. All existing sessions have been signed out. Sign in with your new password.")
        } else {
            if (mutable.value.user != null) {
                try { val user = api.currentUser(); mutable.update { it.copy(user = user) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Verification succeeded; profile can refresh independently. */ }
            }
            mutable.update { it.copy(notice = "Email verified. You can continue in StoxSim.") }
        }
    }

    private fun startProductActivity() {
        if (!foreground || mutable.value.user == null || activityJob?.isActive == true) return
        val tracker = activityTracker
        val userId = mutable.value.user!!.id
        activityJob = viewModelScope.launch {
            while (isActive && foreground && mutable.value.user?.id == userId && tracker === activityTracker) {
                if (!mutable.value.working && emailAction.value == null) {
                    val events = if (mutable.value.screen == "Stock" && mutable.value.selected != null)
                        listOf(ProductActivity.ACTIVE, ProductActivity.STOCK_OPENED) else listOf(ProductActivity.ACTIVE)
                    for (event in events) {
                        if (!foreground || mutable.value.user?.id != userId || tracker !== activityTracker) break
                        val day = tracker.attempt(event, Instant.now()) ?: continue
                        if (api.recordActivity(userId, event) && tracker === activityTracker) tracker.confirmed(event, day)
                    }
                }
                delay(60_000)
            }
        }
    }
    private fun restartProductActivity() { activityJob?.cancel(); activityJob = null; startProductActivity() }


    init { restore() }

    fun restore() = runAction {
        mutable.update { it.copy(restoring = true, restoreFailed = false) }
        try {
            val user = api.restore()
            mutable.update { it.copy(restoring = false) }
            if (user != null) signedIn(user)
        } catch (_: SessionExpired) {
            mutable.update { it.copy(user = null, restoring = false) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mutable.update { it.copy(restoring = false, restoreFailed = true,
                error = "Cannot reconnect to StoxSim. Check your connection and retry.") }
        }
    }

    fun authenticate(email: String, password: String, name: String?) = runAction {
        signedIn(api.authenticate(email, password, name))
        if (name != null) mutable.update { it.copy(notice = "Account created. Verify your email address. " + EMAIL_GUIDANCE) }
    }
    private suspend fun signedIn(user: User) {
        cancelSearch()
        streakJob?.cancel(); streakJob = null
        streakTracker = DailyStreakTracker()
        activityJob?.cancel(); activityJob = null; activityTracker = ProductActivityTracker()
        pendingOrder = null; pendingScenario = null; pendingSandbox = null
        val first = user.accounts.firstOrNull { it.region == "INDIA" && it.kind == "STANDARD" }
            ?: user.accounts.firstOrNull()
        mutable.value = AppState(restoring = false, working = true, user = user,
            region = first?.region ?: "INDIA", accountId = first?.id.orEmpty(),
            dark = preferences.getBoolean(user.email, false),
            readModules = preferences.getStringSet("lessons:${user.email}", emptySet())?.toSet().orEmpty())
        preferences.edit().putBoolean("last-dark", mutable.value.dark).apply()
        readAccounts()
        loadPage()
        startStream()
    }
    fun forgot(email: String) = runAction {
        api.forgotPassword(email)
        mutable.update { it.copy(notice = "If an account exists for this email, a reset link has been sent. " + EMAIL_GUIDANCE + " Open the link to finish resetting your password.") }
    }
    fun setDark(dark: Boolean) {
        mutable.value.user?.email?.let { preferences.edit().putBoolean(it, dark).apply() }
        preferences.edit().putBoolean("last-dark", dark).apply()
        mutable.update { it.copy(dark = dark) }
    }
    fun openScreen(screen: String) {
        if (screen !in listOf("Home", "Markets", "Stock", "Watchlist", "Portfolio", "Orders", "Explore", "Scenario Lab",
                "Learning path", "Competitions", "Campus", "Finwiz", "Finwiz lesson", "Finwiz chat", "Account", "Profile & security", "Subscriptions", "Wallet", "Weekly reports", "Portfolio analysis")) return
        cancelSearch()
        mutable.update { it.copy(screen = screen, error = null, notice = null) }
        if (mutable.value.working) pagePending = true else refresh()
    }
    fun foreground(active: Boolean) {
        foreground = active
        if (!active) { journeyJob?.cancel(); journeyJob = null; cancelSearch(); activityJob?.cancel(); activityJob = null; streakJob?.cancel(); streakJob = null; streamJob?.cancel(); streamJob = null; mutable.update { it.copy(streamStatus = "OFFLINE") } }
        else {
            startStream(); startDailyStreak(); startProductActivity()
            if (mutable.value.user != null && !mutable.value.working) refresh()
            val current = mutable.value
            if (current.screen == "Markets" && !current.searched && current.searchQuery.trim().length >= 2) search(current.searchQuery)
        }
    }
    private fun startDailyStreak() {
        if (!foreground || mutable.value.user == null || streakJob?.isActive == true) return
        val tracker = streakTracker
        streakJob = viewModelScope.launch {
            while (isActive && foreground && mutable.value.user != null && tracker === streakTracker) {
                val now = Instant.now()
                // Do not queue optional work ahead of a trade or another explicit user action.
                if (!mutable.value.working && tracker.shouldRecord(now)) {
                    tracker.attempted(now)
                    try {
                        val result = api.mutate("progression/check-in")
                        if (isActive && tracker === streakTracker && mutable.value.user != null) {
                            if (result.optBoolean("checkedInToday")) tracker.confirmed(result.text("lastCheckInDate"), result.text("checkInZoneId"))
                            putData("progression", result)
                            mutable.update { it.copy(sectionErrors = it.sectionErrors - "dailyStreak") }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: SessionExpired) {
                        mutable.value = AppState(restoring = false, error = "Your session has expired. Please sign in again.")
                        streamJob?.cancel(); streamJob = null
                        break
                    } catch (_: Exception) {
                        if (isActive && tracker === streakTracker) mutable.update { it.copy(sectionErrors = it.sectionErrors +
                            ("dailyStreak" to "Your daily streak will sync automatically when the connection is available.")) }
                    }
                }
                delay(60_000)
            }
        }
    }
    private fun startStream() {
        if (!foreground || mutable.value.user == null || streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            while (isActive && foreground && mutable.value.user != null) {
                mutable.update { it.copy(streamStatus = "CONNECTING") }
                try {
                    api.currentUser() // Check revocation before reconnecting a read-only stream.
                    api.quotes().collect { tick ->
                        if (tick.optBoolean("streamConnected")) mutable.update { it.copy(streamStatus = "CONNECTED") }
                        else applyTick(tick)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: SessionExpired) {
                    mutable.value = AppState(restoring = false, error = "Your session has expired. Please sign in again.")
                    break
                } catch (_: Exception) { }
                mutable.update { it.copy(streamStatus = "RECONNECTING") }
                delay(5000)
            }
        }
    }
    private fun applyTick(tick: JSONObject) {
        val old = mutable.value
        if (tick.text("marketRegion") != old.region || tick.decimal("lastPrice") == null) return
        val key = tick.text("instrumentKey")
        if (key.isBlank()) return
        val at = try { Instant.parse(tick.text("exchangeTimestamp")) } catch (_: Exception) { return }
        val now = Instant.now()
        if (at.isAfter(now.plusSeconds(5))) return
        val status = if (old.data["market"]?.text("phase") != "REGULAR") "CLOSED" else if (at.isBefore(now.minusSeconds(60))) "STALE" else "LIVE"
        val price = tick.decimal("lastPrice")!!
        val previous = tick.decimal("previousClose")
        val change = previous?.let { price.subtract(it) }
        val percentage = if (previous != null && previous.signum() > 0) change!!.multiply(BigDecimal(100)).divide(previous, 4, RoundingMode.HALF_UP) else null
        fun patch(json: JSONObject): JSONObject {
            val existing = try { Instant.parse(json.text("exchangeTimestamp")) } catch (_: Exception) { null }
            if (existing != null && at.isBefore(existing)) return json
            return JSONObject(json.toString()).put("lastPrice", price).put("value", price).put("change", change ?: JSONObject.NULL)
                .put("changePercent", percentage ?: JSONObject.NULL).put("dataStatus", status).put("exchangeTimestamp", at.toString())
        }
        mutable.update { state ->
            val watch = state.data["watchlist"]?.let { source ->
                JSONObject(source.toString()).put("items", org.json.JSONArray(source.objects("items").map { if (it.text("instrumentKey") == key) patch(it) else it }))
            }
            val indices = state.lists["indices"]?.map { if (it.text("instrumentKey") == key) patch(it) else it }
            val quote = if ((state.selected?.key?.ifBlank { state.quote?.key.orEmpty() }) == key && state.quote != null && (state.quote.at.isBlank() || try { !at.isBefore(Instant.parse(state.quote.at)) } catch (_: Exception) { true }))
                state.quote.copy(price = price, previous = previous ?: state.quote.previous, status = status, at = at.toString(),
                    high = tick.decimal("high") ?: state.quote.high, low = tick.decimal("low") ?: state.quote.low,
                    open = tick.decimal("open") ?: state.quote.open, volume = tick.decimal("volume")?.toLong() ?: state.quote.volume) else state.quote
            state.copy(quote = quote, data = if (watch == null) state.data else state.data + ("watchlist" to watch), lists = if (indices == null) state.lists else state.lists + ("indices" to indices))
        }
    }
    override fun onCleared() { stockSearch.cancel(); pendingEmailLink = null; activityJob?.cancel(); streakJob?.cancel(); streamJob?.cancel(); super.onCleared() }
    fun refresh() = runAction { loadPage() }
    fun refreshUser() = runAction { readAccounts() }
    fun loadPortfolio() = refresh()
    private suspend fun readAccounts() {
        val user = api.currentUser()
        val accounts = api.accounts()
        val old = mutable.value
        val selected = accounts.find { it.id == old.accountId }
            ?: accounts.firstOrNull { it.region == old.region && it.kind == "STANDARD" }
            ?: accounts.firstOrNull { it.region == old.region }
        mutable.update { it.copy(user = user.copy(accounts = accounts), accountId = selected?.id.orEmpty()) }
    }
    fun changeRegion(region: String) {
        if (mutable.value.working || region !in listOf("INDIA", "UNITED_STATES")) return
        cancelSearch()
        val account = mutable.value.user?.accounts?.firstOrNull { it.region == region && it.kind == "STANDARD" }
            ?: mutable.value.user?.accounts?.firstOrNull { it.region == region }
        mutable.update { it.copy(region = region, accountId = account?.id.orEmpty(), portfolio = null,
            instruments = emptyList(), selected = null, quote = null, candles = emptyList(), searched = false,
            data = emptyMap(), lists = emptyMap(), sectionErrors = emptyMap(),
            screen = when (it.screen) { "Stock" -> "Markets"; "Finwiz chat" -> "Finwiz"; else -> it.screen }, activeChatId = null, searchQuery = "", searchError = null) }
        refresh()
    }
    fun selectAccount(id: String) {
        if (mutable.value.working) return
        val account = mutable.value.user?.accounts?.find { it.id == id } ?: return
        cancelSearch()
        mutable.update { it.copy(accountId = id, region = account.region, portfolio = null,
            data = emptyMap(), lists = emptyMap(), sectionErrors = emptyMap(), selected = null, quote = null,
            candles = emptyList(), instruments = emptyList(), searched = false,
            screen = when (it.screen) { "Stock" -> "Markets"; "Finwiz chat" -> "Finwiz"; else -> it.screen }, activeChatId = null, searchQuery = "", searchError = null) }
        refresh()
    }
    fun search(query: String) {
        if (mutable.value.user == null) return
        val text = query.take(100)
        mutable.update { it.copy(screen = "Markets", searchQuery = text, instruments = emptyList(),
            searched = false, searchLoading = text.trim().length >= 2, searchError = null) }
        stockSearch.request(mutable.value.region, text, { result ->
            mutable.update { it.copy(instruments = result, searched = true, searchLoading = false) }
        }, { failure ->
            if (failure is SessionExpired) {
                mutable.value = AppState(restoring = false, error = "Your session has expired. Please sign in again.")
            } else mutable.update { it.copy(searchLoading = false, searchError = "Search unavailable. Please try again.") }
        })
    }
    fun selectStock(instrument: Instrument) {
        if (mutable.value.working || instrument.region != mutable.value.region) return
        cancelSearch()
        mutable.update { it.copy(selected = instrument, screen = "Stock", quote = null, candles = emptyList(),
            data = it.data - "stockInsights", chartMonths = 1, insightPeriod = "quarterly",
            recentStocks = (listOf(instrument) + it.recentStocks.filterNot { recent -> recent.symbol == instrument.symbol && recent.exchange == instrument.exchange && recent.region == instrument.region }).take(10)) }
        restartProductActivity()
        refresh()
    }
    fun chartRange(months: Long) = runAction {
        val instrument = mutable.value.selected ?: return@runAction
        mutable.update { it.copy(chartMonths = months, candles = emptyList()) }
        section("chart") { val result = api.candles(instrument, months); mutable.update { it.copy(candles = result) } }
    }
    fun financialPeriod(period: String) = runAction {
        val instrument = mutable.value.selected ?: return@runAction
        mutable.update { it.copy(insightPeriod = period, data = it.data - "stockInsights") }
        section("stockInsights") { putData("stockInsights", api.insights(instrument, period)) }
    }
    private fun putData(key: String, value: JSONObject) { mutable.update { it.copy(data = it.data + (key to value)) } }
    private fun putList(key: String, value: List<JSONObject>) { mutable.update { it.copy(lists = it.lists + (key to value)) } }
    private suspend fun read(key: String, path: String, params: Map<String, String> = emptyMap()) = section(key) { putData(key, api.objectData(path, params)) }
    private suspend fun list(key: String, path: String, params: Map<String, String> = emptyMap()) = section(key) { putList(key, api.arrayData(path, params)) }
    private suspend fun section(key: String, action: suspend () -> Unit) {
        mutable.update { it.copy(sectionErrors = it.sectionErrors - key) }
        try { action() } catch (cancelled: CancellationException) { throw cancelled }
        catch (expired: SessionExpired) { throw expired }
        catch (failure: Exception) { mutable.update { it.copy(sectionErrors = it.sectionErrors + (key to failureMessage(failure))) } }
    }
    private suspend fun portfolio() {
        val id = mutable.value.accountId
        if (id.isBlank()) return
        section("portfolio") { val result = api.accountPortfolio(id); mutable.update { it.copy(portfolio = result) } }
    }
    private suspend fun market() {
        val region = mutable.value.region
        read("market", "market/status", mapOf("exchange" to if (region == "INDIA") "NSE" else "NASDAQ"))
        list("indices", "market/indices", mapOf("marketRegion" to region))
        read("movers", "market/movers", mapOf("marketRegion" to region))
    }
    private suspend fun loadPage() {
        val id = mutable.value.accountId
        when (mutable.value.screen) {
            "Home" -> {
                portfolio(); market(); read("watchlist", "watchlists/default"); read("onboarding", "onboarding"); read("journey", "onboarding/journey")
                if (id.isNotBlank()) list("trades", "accounts/$id/trades")
            }
            "Markets" -> market()
            "Watchlist" -> { read("watchlist", "watchlists/default"); read("market", "market/status", mapOf("exchange" to if (mutable.value.region == "INDIA") "NSE" else "NASDAQ")) }
            "Stock" -> mutable.value.selected?.let { instrument ->
                read("market", "market/status", mapOf("exchange" to instrument.exchange))
                section("quote") { val quote = api.quote(instrument); mutable.update { it.copy(quote = quote) } }
                section("chart") { val candles = api.candles(instrument, mutable.value.chartMonths); mutable.update { it.copy(candles = candles) } }
                section("stockInsights") { putData("stockInsights", api.insights(instrument, mutable.value.insightPeriod)) }
                read("watchlist", "watchlists/default")
            }
            "Portfolio", "Portfolio analysis" -> {
                portfolio()
                if (id.isNotBlank()) { read("analytics", "accounts/$id/portfolio/analytics"); read("insights", "accounts/$id/portfolio/insights"); read("history", "accounts/$id/portfolio/history", mapOf("days" to "30")); list("sectors", "accounts/$id/portfolio/sectors") }
            }
            "Orders" -> if (id.isNotBlank()) { list("orders", "accounts/$id/orders"); list("trades", "accounts/$id/trades"); list("ledger", "accounts/$id/ledger"); portfolio() }
            "Scenario Lab" -> { list("scenarios", "scenarios"); read("credits", "scenarios/credits") }
            "Learning path" -> { section("progression") { putData("progression", api.mutate("progression/visit")) }; read("journey", "onboarding/journey") }
            "Competitions" -> { section("board") { putData("board", api.mutate("competitions/current/visit")) }; list("leagues", "leagues"); read("journey", "onboarding/journey") }
            "Campus" -> { read("campus", "campus"); list("institutions", "campus/institutions"); read("membershipRequest", "campus/membership-request")
                if (mutable.value.data["campus"]?.optBoolean("platformAdmin") == true) list("campusVerificationQueue", "campus/admin/verification-requests")
                mutable.value.data["campus"]?.optJSONObject("membership")?.text("institutionId")?.takeIf { it.isNotBlank() }?.let { campusWorkspace(it) } }
            "Account" -> { readAccounts(); read("subscription", "subscription") }
            "Profile & security" -> { readAccounts(); list("sessions", "auth/sessions"); list("events", "auth/events") }
            "Subscriptions" -> { readAccounts(); read("subscription", "subscription") }
            "Wallet" -> { readAccounts(); portfolio(); mutable.value.accountId.takeIf { it.isNotBlank() }?.let { account -> list("ledger", "accounts/$account/ledger") } }
            "Weekly reports" -> { read("reportPreference", "reports/weekly/preferences"); list("reports", "reports/weekly") }
        }
    }
    fun watch(instrument: Instrument) = runAction {
        putData("watchlist", api.addToWatchlist(instrument))
        mutable.update { it.copy(notice = "Added ${instrument.symbol} to My Watchlist.") }
    }
    fun removeWatch(id: String) = runAction { putData("watchlist", api.mutate("watchlists/default/items/$id", "DELETE")) }
    fun estimate(draft: OrderDraft) = runAction {
        mutable.update { it.copy(data = it.data - "charges") }
        val price = if (draft.type == "LIMIT") draft.limit else mutable.value.quote?.price
        require(price != null && price.signum() > 0)
        putData("charges", api.objectData("trading/charges/estimate", mapOf("exchange" to draft.instrument.exchange,
            "side" to draft.side, "turnover" to price.multiply(BigDecimal(draft.quantity)).toPlainString())))
    }
    fun placeOrder(draft: OrderDraft) = runAction {
        require(draft.accountId == mutable.value.accountId && mutable.value.account?.active == true)
        val key = pendingOrder?.takeIf { it.first == draft }?.second ?: UUID.randomUUID().toString()
        pendingOrder = draft to key
        val result = api.mutate("accounts/${draft.accountId}/orders", body = draft.payload(), idempotencyKey = key)
        pendingOrder = null
        putData("orderResult", result)
        mutable.update { it.copy(notice = "${result.text("symbol")} · ${result.text("status")}. ${result.text("rejectionReason")}") }
        portfolio()
        list("orders", "accounts/${draft.accountId}/orders")
        read("journey", "onboarding/journey")
    }
    fun cancelOrder(id: String) = runAction {
        api.mutate("accounts/${mutable.value.accountId}/orders/$id", "DELETE")
        loadPage(); mutable.update { it.copy(notice = "Order canceled. Reserved cash or shares have been released.") }
    }
    fun modifyOrder(id: String, quantity: Long, limit: BigDecimal) = runAction {
        require(quantity > 0 && limit.signum() > 0 && mutable.value.account?.active == true)
        api.mutate("accounts/${mutable.value.accountId}/orders/$id", "PUT", JSONObject().put("quantity", quantity).put("limitPrice", limit))
        loadPage()
        mutable.update { it.copy(notice = "Pending order updated. Reservations have been recalculated.") }
    }
    fun history(days: Int) = runAction {
        mutable.update { it.copy(data = it.data - "history") }
        read("history", "accounts/${mutable.value.accountId}/portfolio/history", mapOf("days" to days.toString()))
    }
    fun scenario(definition: JSONObject, title: String, shocks: List<BigDecimal>) = runAction {
        val id = mutable.value.accountId
        require(id.isNotBlank())
        val body = JSONObject().put("scenarioId", definition.getString("id")).put("version", definition.getInt("version"))
        if (definition.getString("id") == "custom") body.put("title", title.trim()).put("customShocks", org.json.JSONArray(shocks))
        val signature = "$id:$body"
        val requestId = pendingScenario?.takeIf { it.first == signature }?.second ?: UUID.randomUUID().toString()
        pendingScenario = signature to requestId
        putData("scenarioResult", api.mutate("accounts/$id/scenarios", body = body.put("requestId", requestId)))
        pendingScenario = null
        read("credits", "scenarios/credits")
        read("journey", "onboarding/journey")
    }
    fun refreshJourney() {
        if (!foreground || mutable.value.working || journeyJob?.isActive == true) return
        val userId = mutable.value.user?.id ?: return
        journeyJob = viewModelScope.launch {
            try {
                val result = api.objectData("onboarding/journey")
                if (foreground && mutable.value.user?.id == userId) putData("journey", result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Explicit refresh handles errors; optional polling preserves notices. */ }
        }
    }
    fun onboarding(action: String) = runAction {
        putData("onboarding", api.mutate("onboarding/$action"))
        read("journey", "onboarding/journey")
        if (action == "introduction/complete") openScreen("Markets")
    }
    fun premiumBoard(enroll: Boolean = false) = runAction {
        if (enroll) putData("premiumBoard", api.mutate("competitions/premium/enroll")) else read("premiumBoard", "competitions/premium")
    }
    fun enroll() = runAction { putData("board", api.mutate("competitions/current/enroll")) }
    fun league(id: String) = runAction { read("league", "leagues/$id") }
    fun createLeague(name: String) = runAction {
        val result = api.mutate("leagues", body = JSONObject().put("name", name.trim()))
        putData("league", result.getJSONObject("league")); putData("invite", result); list("leagues", "leagues")
    }
    fun joinLeague(code: String) = runAction {
        putData("league", api.mutate("leagues/join", body = JSONObject().put("inviteCode", code.trim())))
        list("leagues", "leagues")
    }
    fun leagueAction(id: String, action: String) = runAction {
        when (action) {
            "rotate" -> putData("invite", api.mutate("leagues/$id/invite/rotate"))
            "leave" -> { api.mutate("leagues/$id/leave"); mutable.update { it.copy(data = it.data - "league" - "invite") }; list("leagues", "leagues") }
            "delete" -> { api.mutate("leagues/$id", "DELETE"); mutable.update { it.copy(data = it.data - "league" - "invite") }; list("leagues", "leagues") }
        }
    }
    fun campusSearch(query: String) = runAction { list("institutions", "campus/institutions", mapOf("q" to query.trim())) }
    fun openCampus(id: String) = runAction { campusWorkspace(id) }
    private suspend fun campusWorkspace(id: String) {
        putData("selectedInstitution", JSONObject().put("id", id))
        mutable.update { it.copy(data = it.data - "campusWorkspace" - "campusBoard" - "campusManagement") }
        read("campusWorkspace", "campus/institutions/$id")
        if (mutable.value.data["campusWorkspace"]?.text("viewerRole") in listOf("ORGANIZER", "ADMIN")) read("campusManagement", "campus/institutions/$id/manage")
    }
    fun campusApply(note: String) = runAction {
        val id = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        putData("membershipRequest", api.mutate("campus/institutions/$id/membership-requests", body = JSONObject().put("note", note.trim())))
    }
    fun cancelCampusRequest(id: String) = runAction { api.mutate("campus/membership-requests/$id/cancel"); read("membershipRequest", "campus/membership-request") }
    fun campusVerification(name: String, domain: String, website: String) = runAction {
        api.mutate("campus/verification-requests", body = JSONObject().put("institutionName", name.trim()).put("emailDomain", domain.trim()).put("websiteUrl", website.trim()))
        read("campus", "campus")
    }
    fun campusBoard(id: String) = runAction {
        val institution = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        read("campusBoard", "campus/institutions/$institution/competitions/$id")
    }
    fun campusBoardAction(action: String, note: String = "") = runAction {
        val institution = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        val competition = mutable.value.data["campusBoard"]?.getJSONObject("competition")?.text("id") ?: return@runAction
        api.mutate("campus/institutions/$institution/competitions/$competition/$action", body = if (action == "cancel") JSONObject().put("note", note.trim()) else JSONObject())
        read("campusBoard", "campus/institutions/$institution/competitions/$competition")
    }
    fun createCampusCompetition(title: String, start: String, end: String, capacity: Int) = runAction {
        val institution = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        val body = JSONObject().put("title", title.trim()).put("startsAt", start.ifBlank { null } ?: JSONObject.NULL).put("endsAt", end).put("capacity", capacity)
        api.mutate("campus/institutions/$institution/competitions", body = body)
        campusWorkspace(institution)
    }
    fun campusReview(id: String, approved: Boolean, note: String) = runAction {
        val institution = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        api.mutate("campus/institutions/$institution/membership-requests/$id/review", body = JSONObject().put("approve", approved).put("note", note.trim()))
        read("campusManagement", "campus/institutions/$institution/manage")
    }

    fun campusLeave(note: String) = runAction {
        val institution = mutable.value.data["campus"]?.optJSONObject("membership")?.text("institutionId") ?: return@runAction
        val user = mutable.value.user?.id?.takeIf { it.isNotBlank() } ?: return@runAction
        api.mutate("campus/institutions/$institution/members/$user/remove", body = JSONObject().put("note", note.trim()))
        read("campus", "campus")
        mutable.update { it.copy(data = it.data - "selectedInstitution" - "campusWorkspace" - "campusManagement" - "campusBoard") }
    }
    fun campusMember(target: String, role: String?, note: String = "") = runAction {
        val institution = mutable.value.data["selectedInstitution"]?.text("id") ?: return@runAction
        require(role == null || role in listOf("ORGANIZER", "MEMBER"))
        api.mutate("campus/institutions/$institution/members/$target/${if (role == null) "remove" else "role"}",
            body = if (role == null) JSONObject().put("note", note.trim()) else JSONObject().put("role", role))
        read("campus", "campus")
        if (target == mutable.value.user?.id && role == null) {
            mutable.update { it.copy(data = it.data - "selectedInstitution" - "campusWorkspace" - "campusManagement" - "campusBoard") }
        } else campusWorkspace(institution)
    }
    fun campusSuspension(institution: String, suspended: Boolean, note: String) = runAction {
        api.mutate("campus/institutions/$institution/suspension", body = JSONObject().put("suspended", suspended).put("note", note.trim()))
        campusWorkspace(institution)
    }
    fun campusRecovery(institution: String, email: String, note: String) = runAction {
        api.mutate("campus/institutions/$institution/organizer-recovery", body = JSONObject().put("email", email.trim()).put("note", note.trim()))
        campusWorkspace(institution)
    }
    fun campusVerificationReview(id: String, approve: Boolean, note: String) = runAction {
        api.mutate("campus/admin/verification-requests/$id/${if (approve) "approve" else "reject"}", body = JSONObject().put("note", note.trim()))
        list("campusVerificationQueue", "campus/admin/verification-requests")
    }

    fun openLesson(id: String) {
        if (learningModules.none { it.id == id }) return
        mutable.update { it.copy(learningModule = id) }; openScreen("Finwiz lesson")
    }
    fun markLessonRead(id: String) {
        if (learningModules.none { it.id == id }) return
        val read = mutable.value.readModules + id
        mutable.value.user?.email?.let { preferences.edit().putStringSet("lessons:$it", read).apply() }
        mutable.update { it.copy(readModules = read) }
    }
    fun newChat(topic: String = "LEARN", symbol: String = "", experience: String = "BEGINNER") {
        if (mutable.value.working || learningModules.none { it.id == topic }) return
        val chat = FinwizConversation(UUID.randomUUID().toString(), "New conversation", mutable.value.region,
            topic, symbol.trim().take(32), experience)
        mutable.update { it.copy(chats = (listOf(chat) + it.chats).take(10), activeChatId = chat.id) }
        openScreen("Finwiz chat")
    }
    fun openChat(id: String) {
        if (mutable.value.working || mutable.value.chats.none { it.id == id && it.region == mutable.value.region }) return
        mutable.update { it.copy(activeChatId = id) }; openScreen("Finwiz chat")
    }
    fun startChatWithQuestion(topic: String, question: String) {
        if (mutable.value.user == null || mutable.value.working || question.trim().length !in 1..2000 || learningModules.none { it.id == topic }) return
        val current = mutable.value.activeChat
        if (current != null && current.region == mutable.value.region && current.topic == topic && current.symbol.isBlank()) {
            if (current.messages.size < 40) askChat(question)
            return
        }
        val chat = FinwizConversation(UUID.randomUUID().toString(), "New conversation", mutable.value.region, topic)
        mutable.update { it.copy(chats = (listOf(chat) + it.chats).take(10), activeChatId = chat.id,
            screen = "Finwiz", error = null, notice = null) }
        askChat(question)
    }
    fun deleteChat(id: String) {
        if (mutable.value.working) return
        mutable.update { it.copy(chats = it.chats.filterNot { chat -> chat.id == id },
            activeChatId = if (it.activeChatId == id) null else it.activeChatId,
            screen = if (it.activeChatId == id) "Finwiz" else it.screen) }
    }
    fun askChat(question: String) = runAction {
        val chat = mutable.value.activeChat ?: return@runAction
        require(chat.region == mutable.value.region && question.trim().length in 1..2000)
        require(chat.messages.size < 40)
        val message = FinwizMessage(UUID.randomUUID().toString(), "user", question.trim())
        val messages = chat.messages + message
        mutable.update { it.copy(chats = it.chats.map { item -> if (item.id == chat.id) item.copy(
            title = if (item.messages.isEmpty()) question.trim().take(60) else item.title, messages = messages) else item }) }
        val body = JSONObject().put("question", finwizQuestion(question, chat.messages)).put("topic", chat.topic).put("experienceLevel", chat.experience)
            .put("marketRegion", chat.region)
        if (chat.symbol.isNotBlank()) body.put("symbol", chat.symbol).put("exchange", if (chat.region == "INDIA") "NSE" else "NASDAQ")
        try {
            val result = api.mutate("finwiz/ask", body = body)
            val answer = FinwizMessage(UUID.randomUUID().toString(), "assistant", result.text("answer").take(24000),
                result.text("provider"), result.text("generatedAt"), result.text("disclaimer"), result.strings("suggestedQuestions"), result.text("finwizTier"))
            mutable.update { it.copy(chats = it.chats.map { item -> if (item.id == chat.id) item.copy(messages = messages + answer) else item }) }
            read("journey", "onboarding/journey")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (expired: SessionExpired) { throw expired }
        catch (failure: Exception) {
            val error = FinwizMessage(UUID.randomUUID().toString(), "error", failureMessage(failure))
            mutable.update { it.copy(chats = it.chats.map { item -> if (item.id == chat.id) item.copy(messages = messages + error) else item }) }
        }
    }
    fun profile(name: String, email: String, password: String) = runAction {
        val body = JSONObject().put("displayName", name.trim()).put("email", email.trim())
        if (password.isNotEmpty()) body.put("currentPassword", password)
        val user = parseUser(api.mutate("auth/me", "PATCH", body))
        if (user.email.lowercase() != mutable.value.user?.email?.lowercase()) {
            api.endLocalSession()
            mutable.value = AppState(restoring = false, working = true, notice = "Email updated. Sign in again to verify your email. " + EMAIL_GUIDANCE)
        } else mutable.update { it.copy(user = user, notice = "Your profile has been updated.") }
    }
    fun resendVerification() = runAction { api.mutate("auth/email-verification/resend"); mutable.update { it.copy(notice = "Verification email requested. " + EMAIL_GUIDANCE) } }
    fun password(current: String, replacement: String) = runAction {
        api.mutate("auth/me/password", "PATCH", JSONObject().put("currentPassword", current).put("newPassword", replacement))
        api.endLocalSession(); mutable.value = AppState(restoring = false, working = true, notice = "Password updated. Sign in again.")
    }
    fun revokeSession(id: String) = runAction { api.mutate("auth/sessions/$id", "DELETE"); list("sessions", "auth/sessions") }
    fun logoutAll() = runAction { cancelSearch(); api.mutate("auth/logout-all"); api.endLocalSession(); mutable.value = AppState(restoring = false, working = true, notice = "All sessions signed out.") }
    fun deleteAccount(password: String) = runAction {
        api.mutate("auth/me", "DELETE", JSONObject().put("password", password)); api.endLocalSession()
        mutable.value = AppState(restoring = false, working = true, notice = "Account deleted.")
    }
    fun exportAccount() = runAction { val result = api.mutate("auth/me/export").toString(2); mutable.update { it.copy(export = result) } }
    fun exportSaved() { mutable.update { it.copy(export = null, notice = "Account export saved.") } }
    fun createSandbox() = runAction {
        val key = pendingSandbox ?: UUID.randomUUID().toString().also { pendingSandbox = it }
        api.mutate("accounts/sandboxes", idempotencyKey = key); pendingSandbox = null
        readAccounts(); read("subscription", "subscription")
    }
    fun reportPreference(enabled: Boolean, zone: String) = runAction {
        putData("reportPreference", api.mutate("reports/weekly/preferences", "PUT", JSONObject().put("enabled", enabled).put("zoneId", zone)))
    }
    fun reportPreview() = runAction { read("reportPreview", "reports/weekly/preview", mapOf("zoneId" to (mutable.value.data["reportPreference"]?.text("zoneId") ?: "Asia/Kolkata"))) }
    fun clearData(key: String) { mutable.update { it.copy(data = it.data - key) } }
    fun clearMessage() { mutable.update { it.copy(error = null, notice = null) } }
    fun logout() = runAction {
        cancelSearch()
        streakJob?.cancel(); streakJob = null
        streakTracker = DailyStreakTracker()
        streamJob?.cancel(); streamJob = null
        val revoked = api.logout()
        preferences.edit().putBoolean("last-dark", false).apply()
        pendingOrder = null; pendingScenario = null; pendingSandbox = null
        mutable.value = AppState(restoring = false, working = true,
            notice = if (revoked) "Signed out." else "Signed out on this device. Server sign-out could not be confirmed. Revoke this session from another signed-in device.")
    }
    private fun failureMessage(failure: Exception): String = when (failure) {
        is ApiFailure -> failure.message.orEmpty()
        is IOException -> if (failure.message in listOf("Email or password is incorrect.", "Session reconnected. Review the action and try again.")) failure.message!!
            else "Connection interrupted. Refresh to check the result before retrying."
        else -> "Could not load this information. Please try again."
    }
    private fun runAction(action: suspend () -> Unit) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, error = null, notice = null) }
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SessionExpired) { cancelSearch(); mutable.value = AppState(restoring = false, working = true, error = "Your session has expired. Please sign in again.") }
            catch (failure: Exception) { mutable.update { it.copy(error = failureMessage(failure)) } }
            finally {
                mutable.update { it.copy(working = false) }
                if (mutable.value.user == null) { activityJob?.cancel(); activityJob = null; streakJob?.cancel(); streakJob = null; streamJob?.cancel(); streamJob = null } else { startStream(); startDailyStreak(); restartProductActivity() }
                if (pagePending && mutable.value.user != null) { pagePending = false; refresh() }
            }
        }
    }
}
