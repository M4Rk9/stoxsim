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

internal data class AppState(
    val restoring: Boolean = true, val restoreFailed: Boolean = false,
    val working: Boolean = false, val user: User? = null,
    val region: String = "INDIA", val accountId: String = "", val screen: String = "Home",
    val dark: Boolean = false, val streamStatus: String = "OFFLINE", val portfolio: Portfolio? = null,
    val instruments: List<Instrument> = emptyList(), val searched: Boolean = false,
    val selected: Instrument? = null, val quote: Quote? = null, val candles: List<Candle> = emptyList(),
    val chartMonths: Long = 1, val insightPeriod: String = "quarterly",
    val data: Map<String, JSONObject> = emptyMap(), val lists: Map<String, List<JSONObject>> = emptyMap(),
    val sectionErrors: Map<String, String> = emptyMap(), val export: String? = null,
    val error: String? = null, val notice: String? = null
) {
    val account: Account? get() = user?.accounts?.find { it.id == accountId }
    val currency: String get() = account?.currency ?: if (region == "INDIA") "INR" else "USD"
}

internal class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val api = (application as StoxSimApplication).api
    private val preferences = application.getSharedPreferences("appearance", 0)
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    // Retain a write's key after an ambiguous network failure. Explicit retries reuse it.
    private var pendingOrder: Pair<OrderDraft, String>? = null
    private var pendingScenario: Pair<String, String>? = null
    private var pendingSandbox: String? = null
    private var pagePending = false
    private var foreground = false
    private var streamJob: Job? = null

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
    }
    private suspend fun signedIn(user: User) {
        pendingOrder = null; pendingScenario = null; pendingSandbox = null
        val first = user.accounts.firstOrNull { it.region == "INDIA" && it.kind == "STANDARD" }
            ?: user.accounts.firstOrNull()
        mutable.value = AppState(restoring = false, working = true, user = user,
            region = first?.region ?: "INDIA", accountId = first?.id.orEmpty(),
            dark = preferences.getBoolean(user.email, false))
        readAccounts()
        loadPage()
        startStream()
    }
    fun forgot(email: String) = runAction {
        api.forgotPassword(email)
        mutable.update { it.copy(notice = "If an account exists for this email, a reset link has been sent. Open the link in your email to finish resetting your password.") }
    }
    fun setDark(dark: Boolean) {
        mutable.value.user?.email?.let { preferences.edit().putBoolean(it, dark).apply() }
        mutable.update { it.copy(dark = dark) }
    }
    fun openScreen(screen: String) {
        if (screen !in listOf("Home", "Markets", "Stock", "Watchlist", "Portfolio", "Orders", "Explore", "Scenario Lab",
                "Learning path", "Competitions", "Campus", "Finwiz", "Account", "Weekly reports")) return
        mutable.update { it.copy(screen = screen, error = null, notice = null) }
        if (mutable.value.working) pagePending = true else refresh()
    }
    fun foreground(active: Boolean) {
        foreground = active
        if (!active) { streamJob?.cancel(); streamJob = null; mutable.update { it.copy(streamStatus = "OFFLINE") } }
        else { startStream(); if (mutable.value.user != null && !mutable.value.working) refresh() }
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
    override fun onCleared() { streamJob?.cancel(); super.onCleared() }
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
        val account = mutable.value.user?.accounts?.firstOrNull { it.region == region && it.kind == "STANDARD" }
            ?: mutable.value.user?.accounts?.firstOrNull { it.region == region }
        mutable.update { it.copy(region = region, accountId = account?.id.orEmpty(), portfolio = null,
            instruments = emptyList(), selected = null, quote = null, candles = emptyList(), searched = false,
            data = emptyMap(), lists = emptyMap(), sectionErrors = emptyMap(),
            screen = if (it.screen == "Stock") "Markets" else it.screen) }
        refresh()
    }
    fun selectAccount(id: String) {
        if (mutable.value.working) return
        val account = mutable.value.user?.accounts?.find { it.id == id } ?: return
        mutable.update { it.copy(accountId = id, region = account.region, portfolio = null,
            data = emptyMap(), lists = emptyMap(), sectionErrors = emptyMap(), selected = null, quote = null,
            candles = emptyList(), instruments = emptyList(), searched = false,
            screen = if (it.screen == "Stock") "Markets" else it.screen) }
        refresh()
    }
    fun search(query: String) = runAction {
        mutable.update { it.copy(instruments = emptyList(), searched = false) }
        val result = api.search(mutable.value.region, query)
        mutable.update { it.copy(instruments = result, searched = true) }
    }
    fun selectStock(instrument: Instrument) {
        if (mutable.value.working || instrument.region != mutable.value.region) return
        mutable.update { it.copy(selected = instrument, screen = "Stock", quote = null, candles = emptyList(),
            data = it.data - "stockInsights", chartMonths = 1, insightPeriod = "quarterly") }
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
                portfolio(); market(); read("watchlist", "watchlists/default"); read("onboarding", "onboarding")
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
            "Portfolio" -> {
                portfolio()
                if (id.isNotBlank()) { read("analytics", "accounts/$id/portfolio/analytics"); read("insights", "accounts/$id/portfolio/insights"); read("history", "accounts/$id/portfolio/history", mapOf("days" to "30")); list("sectors", "accounts/$id/portfolio/sectors") }
            }
            "Orders" -> if (id.isNotBlank()) { list("orders", "accounts/$id/orders"); list("trades", "accounts/$id/trades"); list("ledger", "accounts/$id/ledger"); portfolio() }
            "Scenario Lab" -> { list("scenarios", "scenarios"); read("credits", "scenarios/credits") }
            "Learning path" -> read("progression", "progression")
            "Competitions" -> { read("board", "competitions/current"); list("leagues", "leagues") }
            "Campus" -> { read("campus", "campus"); list("institutions", "campus/institutions"); read("membershipRequest", "campus/membership-request")
                mutable.value.data["campus"]?.optJSONObject("membership")?.text("institutionId")?.takeIf { it.isNotBlank() }?.let { campusWorkspace(it) } }
            "Account" -> { readAccounts(); read("subscription", "subscription"); list("sessions", "auth/sessions"); list("events", "auth/events") }
            "Weekly reports" -> { read("reportPreference", "reports/weekly/preferences"); list("reports", "reports/weekly") }
        }
    }
    fun watch(instrument: Instrument) = runAction {
        require(instrument.id.isNotBlank())
        putData("watchlist", api.mutate("watchlists/default/items", body = JSONObject().put("instrumentId", instrument.id)))
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
    }
    fun cancelOrder(id: String) = runAction {
        api.mutate("accounts/${mutable.value.accountId}/orders/$id", "DELETE")
        loadPage(); mutable.update { it.copy(notice = "Order canceled. Reserved cash or shares have been released.") }
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
    }
    fun checkIn() = runAction { putData("progression", api.mutate("progression/check-in")) }
    fun onboarding(action: String) = runAction { putData("onboarding", api.mutate("onboarding/$action")) }
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

    fun ask(question: String, topic: String, symbol: String, experience: String) = runAction {
        val body = JSONObject().put("question", question.trim()).put("topic", topic).put("experienceLevel", experience)
            .put("marketRegion", mutable.value.region)
        if (symbol.isNotBlank()) body.put("symbol", symbol.trim()).put("exchange", if (mutable.value.region == "INDIA") "NSE" else "NASDAQ")
        putData("finwiz", api.mutate("finwiz/ask", body = body))
    }
    fun profile(name: String, email: String, password: String) = runAction {
        val body = JSONObject().put("displayName", name.trim()).put("email", email.trim())
        if (password.isNotEmpty()) body.put("currentPassword", password)
        val user = parseUser(api.mutate("auth/me", "PATCH", body))
        if (user.email.lowercase() != mutable.value.user?.email?.lowercase()) {
            api.endLocalSession()
            mutable.value = AppState(restoring = false, working = true, notice = "Email updated. Sign in again and check your inbox for verification.")
        } else mutable.update { it.copy(user = user, notice = "Your profile has been updated.") }
    }
    fun resendVerification() = runAction { api.mutate("auth/email-verification/resend"); mutable.update { it.copy(notice = "Verification email requested. Check your inbox.") } }
    fun password(current: String, replacement: String) = runAction {
        api.mutate("auth/me/password", "PATCH", JSONObject().put("currentPassword", current).put("newPassword", replacement))
        api.endLocalSession(); mutable.value = AppState(restoring = false, working = true, notice = "Password updated. Sign in again.")
    }
    fun revokeSession(id: String) = runAction { api.mutate("auth/sessions/$id", "DELETE"); list("sessions", "auth/sessions") }
    fun logoutAll() = runAction { api.mutate("auth/logout-all"); api.endLocalSession(); mutable.value = AppState(restoring = false, working = true, notice = "All sessions signed out.") }
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
        streamJob?.cancel(); streamJob = null
        val revoked = api.logout()
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
            catch (_: SessionExpired) { mutable.value = AppState(restoring = false, working = true, error = "Your session has expired. Please sign in again.") }
            catch (failure: Exception) { mutable.update { it.copy(error = failureMessage(failure)) } }
            finally {
                mutable.update { it.copy(working = false) }
                if (mutable.value.user == null) { streamJob?.cancel(); streamJob = null } else startStream()
                if (pagePending && mutable.value.user != null) { pagePending = false; refresh() }
            }
        }
    }
}
