package com.stoxsim.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal data class Account(
    val region: String, val label: String, val kind: String, val currency: String,
    val cash: BigDecimal, val blocked: BigDecimal, val active: Boolean,
    val id: String = "", val capital: BigDecimal = BigDecimal.ZERO
)
internal data class User(val displayName: String, val email: String, val verified: Boolean, val accounts: List<Account>, val id: String = "")
internal data class Instrument(val symbol: String, val name: String, val exchange: String,
    val region: String = "INDIA", val id: String = "", val currency: String = "INR", val key: String = "", val type: String = "EQUITY") {
    fun isSearchEligible(market: String) = region == market && type in setOf("EQUITY", "ETF") &&
        (if (market == "INDIA") exchange == "NSE" else exchange in setOf("NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE"))
    val canWatch get() = symbol.isNotBlank() && when (region) {
        "INDIA" -> exchange in setOf("NSE", "BSE")
        "UNITED_STATES" -> exchange in setOf("NASDAQ", "NYSE", "NYSE_ARCA", "AMEX", "CBOE")
        else -> false
    }
    fun watchlistPayload(): JSONObject {
        require(canWatch)
        return JSONObject().put("marketRegion", region).put("exchange", exchange).put("symbol", symbol.trim())
    }
}
internal data class Position(val symbol: String, val name: String, val quantity: Long, val value: BigDecimal, val status: String,
    val exchange: String = "NSE", val average: BigDecimal? = null, val price: BigDecimal? = null,
    val profit: BigDecimal? = null, val returnPercent: BigDecimal? = null, val blockedQuantity: Long = 0, val pricingSource: String = "UNKNOWN")
internal data class Portfolio(
    val currency: String, val total: BigDecimal, val cash: BigDecimal, val invested: BigDecimal,
    val profitLoss: BigDecimal, val status: String, val valuedAt: String, val positions: List<Position>,
    val capital: BigDecimal = BigDecimal.ZERO, val blocked: BigDecimal = BigDecimal.ZERO,
    val unrealized: BigDecimal = BigDecimal.ZERO, val returnPercent: BigDecimal = BigDecimal.ZERO
)
internal data class OrderDraft(val accountId: String, val instrument: Instrument, val side: String,
    val type: String, val quantity: Long, val limit: BigDecimal?) {
    fun payload() = JSONObject().put("marketRegion", instrument.region).put("exchange", instrument.exchange)
        .put("symbol", instrument.symbol).put("side", side).put("orderType", type)
        .put("quantity", quantity).put("limitPrice", limit ?: JSONObject.NULL)
}
internal data class Quote(val price: BigDecimal?, val previous: BigDecimal?, val status: String, val at: String,
    val open: BigDecimal?, val high: BigDecimal?, val low: BigDecimal?, val volume: Long?, val key: String = "", val pricingSource: String = "UNKNOWN")
internal data class Candle(val at: String, val close: BigDecimal)

internal fun JSONObject.money(name: String): BigDecimal = get(name).toString().toBigDecimal()
internal fun JSONObject.decimal(name: String): BigDecimal? = if (isNull(name)) null else opt(name)?.toString()?.toBigDecimalOrNull()
internal fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name, "")
internal fun JSONObject.objects(name: String): List<JSONObject> = optJSONArray(name)?.mapObjects { it } ?: emptyList()
internal fun JSONObject.strings(name: String): List<String> = optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
internal fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }
internal fun parseAccount(json: JSONObject) = Account(json.getString("marketRegion"), json.getString("accountLabel"),
    json.getString("accountKind"), json.getString("currency"), json.money("availableCash"), json.money("blockedCash"),
    json.getBoolean("active"), json.optString("id", ""), json.decimal("startingCapital") ?: BigDecimal.ZERO)
internal fun parseUser(json: JSONObject) = User(json.getString("displayName"), json.getString("email"),
    json.getBoolean("emailVerified"), json.getJSONArray("accounts").mapObjects(::parseAccount), json.text("id"))
internal fun parseInstrument(json: JSONObject, region: String) = Instrument(json.getString("tradingSymbol"), json.getString("name"),
    json.getString("exchange"), json.optString("marketRegion", region), json.optString("id", ""),
    json.optString("currency", if (region == "INDIA") "INR" else "USD"), json.text("instrumentKey"), json.text("instrumentType"))
internal fun parsePortfolio(json: JSONObject) = Portfolio(
    json.getString("currency"), json.money("totalAccountValue"), json.money("availableCash"), json.money("investedValue"),
    json.money("totalProfitLoss"), json.getString("dataStatus"), json.getString("valuedAt"),
    json.getJSONArray("holdings").mapObjects {
        Position(it.getString("symbol"), it.getString("name"), it.getLong("quantity"), it.money("marketValue"),
            it.getString("pricingStatus"), it.optString("exchange", "NSE"), it.decimal("averagePrice"),
            it.decimal("currentPrice"), it.decimal("unrealizedProfitLoss"), it.decimal("returnPercent"), it.optLong("blockedQuantity"), it.optString("pricingSource", "UNKNOWN"))
    }, json.decimal("startingCapital") ?: BigDecimal.ZERO, json.decimal("blockedCash") ?: BigDecimal.ZERO,
    json.decimal("unrealizedProfitLoss") ?: BigDecimal.ZERO, json.decimal("totalReturnPercent") ?: BigDecimal.ZERO
)
internal fun parseQuote(json: JSONObject) = Quote(json.decimal("lastPrice"), json.decimal("previousClose"),
    json.getString("dataStatus"), json.text("exchangeTimestamp"), json.decimal("open"), json.decimal("high"),
    json.decimal("low"), if (json.isNull("volume")) null else json.optLong("volume"), json.text("instrumentKey"), json.optString("pricingSource", "UNKNOWN"))
