package com.stoxsim.app.data

import org.json.JSONArray
import org.json.JSONObject

internal data class DerivativeLeg(val contract: JSONObject, val action: String, val lots: Int) {
    fun payload() = JSONObject().put("contractId", contract.getString("id")).put("action", action).put("lots", lots)
    val signature get() = "${contract.getString("id")}:$action:$lots"
    val valid get() = lots in 1..100 && action in listOf("OPEN_LONG", "OPEN_SHORT", "CLOSE")
}
internal data class DerivativeReview(val signature: String, val result: JSONObject, val expiresAt: Long, val accepted: Boolean = false) {
    fun valid(signature: String, now: Long) = this.signature == signature && now < expiresAt
}
internal data class DerivativeSubmission(val signature: String, val requestKey: String, val body: String, val basket: Boolean)
internal data class DerivativesState(
    val enabled: Boolean? = null, val underlying: String = "NSE_INDEX|Nifty 50", val expiry: String = "",
    val expiries: List<String> = emptyList(), val offset: Int = 0, val chain: List<JSONObject> = emptyList(),
    val portfolio: JSONObject? = null, val ticket: DerivativeLeg? = null, val basket: List<DerivativeLeg> = emptyList(),
    val review: DerivativeReview? = null, val basketReview: Boolean = false,
    val uncertain: DerivativeSubmission? = null, val error: String? = null
) {
    fun legs(isBasket: Boolean) = if (isBasket) basket else listOfNotNull(ticket)
    fun signature(isBasket: Boolean) = legs(isBasket).map { it.signature }.sorted().joinToString(";")
    fun payload(isBasket: Boolean): JSONObject = if (isBasket) JSONObject().put("legs", JSONArray(basket.map { it.payload() })) else requireNotNull(ticket).payload()
}
