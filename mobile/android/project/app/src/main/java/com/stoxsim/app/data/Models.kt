package com.stoxsim.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal data class Account(
    val region: String, val label: String, val kind: String, val currency: String,
    val cash: BigDecimal, val blocked: BigDecimal, val active: Boolean
)
internal data class User(val displayName: String, val email: String, val verified: Boolean, val accounts: List<Account>)
internal data class Instrument(val symbol: String, val name: String, val exchange: String)
internal data class Position(val symbol: String, val name: String, val quantity: Long, val value: BigDecimal, val status: String)
internal data class Portfolio(
    val currency: String, val total: BigDecimal, val cash: BigDecimal, val invested: BigDecimal,
    val profitLoss: BigDecimal, val status: String, val valuedAt: String, val positions: List<Position>
)

internal fun JSONObject.money(name: String): BigDecimal = getString(name).toBigDecimal()
internal fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = (0 until length()).map { transform(getJSONObject(it)) }
internal fun parseUser(json: JSONObject) = User(
    json.getString("displayName"), json.getString("email"), json.getBoolean("emailVerified"),
    json.getJSONArray("accounts").mapObjects {
        Account(it.getString("marketRegion"), it.getString("accountLabel"), it.getString("accountKind"),
            it.getString("currency"), it.money("availableCash"), it.money("blockedCash"), it.getBoolean("active"))
    }
)
internal fun parsePortfolio(json: JSONObject) = Portfolio(
    json.getString("currency"), json.money("totalAccountValue"), json.money("availableCash"),
    json.money("investedValue"), json.money("totalProfitLoss"), json.getString("dataStatus"), json.getString("valuedAt"),
    json.getJSONArray("holdings").mapObjects {
        Position(it.getString("symbol"), it.getString("name"), it.getLong("quantity"),
            it.money("marketValue"), it.getString("pricingStatus"))
    }
)
