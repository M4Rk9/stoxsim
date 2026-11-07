package com.stoxsim.app.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import org.json.JSONObject

internal data class PlayProduct(val plan: String, val productId: String, val basePlanId: String)
internal data class PlayConfiguration(val enabled: Boolean, val packageName: String, val accountId: String, val products: List<PlayProduct>)
internal data class PlayOffer(val plan: String, val productId: String, val price: String, val renewal: String)
internal data class PlayBillingState(val available: Boolean = false, val loading: Boolean = false,
    val offers: List<PlayOffer> = emptyList(), val message: String? = null)

internal fun parsePlayConfiguration(json: JSONObject, expectedPackage: String): PlayConfiguration {
    if (!json.optBoolean("enabled")) return PlayConfiguration(false, expectedPackage, "", emptyList())
    require(json.getString("packageName") == expectedPackage) { "Subscription configuration is unavailable for this app." }
    val account = json.getString("obfuscatedAccountId")
    require(account.length in 1..64 && account.all { it.isLetterOrDigit() || it in "-_" })
    val products = json.getJSONArray("products").mapObjects { item ->
        PlayProduct(item.getString("plan"), item.getString("productId"), item.getString("basePlanId")).also {
            require(it.plan in setOf("PLUS", "PRO") && it.productId.matches(Regex("[a-z0-9_.]{1,150}"))
                && it.basePlanId.matches(Regex("[a-z0-9-]{1,63}")))
        }
    }
    require(products.isNotEmpty() && products.size <= 2 && products.distinctBy { it.productId }.size == products.size
        && products.distinctBy { it.plan }.size == products.size)
    return PlayConfiguration(true, expectedPackage, account, products)
}

/** Local validation prevents accidental cross-account submission; the server verifies every token. */
internal fun maySubmitPlayPurchase(config: PlayConfiguration, packageName: String, productIds: List<String>, accountId: String?, purchased: Boolean): Boolean =
    config.enabled && purchased && packageName == config.packageName && accountId == config.accountId &&
        productIds.size == 1 && config.products.any { it.productId == productIds.single() }

internal fun playRenewalPeriod(period: String): String? = when (period) {
    "P1W" -> "week"; "P1M" -> "month"; "P3M" -> "3 months"; "P6M" -> "6 months"; "P1Y" -> "year"; else -> null
}

/** No purchase token is persisted or logged. No client path grants or acknowledges entitlements. */
internal class PlayBilling(context: Context, private val owner: String, private val config: PlayConfiguration,
    private val update: (String, PlayBillingState) -> Unit, private val verify: (String, String, String) -> Unit
) : PurchasesUpdatedListener {
    private var closed = false
    private var state = PlayBillingState(loading = true)
    private val details = mutableMapOf<String, Pair<ProductDetails, String>>()
    private val submitted = mutableSetOf<String>()
    private val client = BillingClient.newBuilder(context.applicationContext).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private fun emit(next: PlayBillingState) { if (!closed) { state = next; update(owner, next) } }
    fun connect() {
        if (!config.enabled) { emit(PlayBillingState(message = "Subscriptions are not available for purchase yet.")); return }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (closed) return
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    emit(PlayBillingState(message = "Google Play billing is unavailable. Check Play Store and try again.")); return
                }
                if (client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS).responseCode != BillingClient.BillingResponseCode.OK) {
                    emit(PlayBillingState(message = "Subscriptions are unavailable on this device.")); return
                }
                loadProducts(); restore()
            }
            override fun onBillingServiceDisconnected() {
                emit(state.copy(available = false, loading = false, message = "Google Play disconnected. Refresh subscriptions to reconnect."))
            }
        })
    }
    private fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(config.products.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(BillingClient.ProductType.SUBS).build()
        }).build()
        client.queryProductDetailsAsync(params) { result, response ->
            if (closed) return@queryProductDetailsAsync
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                emit(state.copy(loading = false, message = "Plan prices could not be loaded from Google Play.")); return@queryProductDetailsAsync
            }
            details.clear()
            val offers = response.productDetailsList.mapNotNull { product ->
                val configured = config.products.singleOrNull { it.productId == product.productId } ?: return@mapNotNull null
                // Only approved regular base plans; no implicit introductory or personalized offers.
                val offer = product.subscriptionOfferDetails?.singleOrNull { it.basePlanId == configured.basePlanId && it.offerId == null }
                    ?: return@mapNotNull null
                val phase = offer.pricingPhases.pricingPhaseList.singleOrNull() ?: return@mapNotNull null
                val period = playRenewalPeriod(phase.billingPeriod) ?: return@mapNotNull null
                if (phase.recurrenceMode != ProductDetails.RecurrenceMode.INFINITE_RECURRING) return@mapNotNull null
                details[product.productId] = product to offer.offerToken
                PlayOffer(configured.plan, product.productId, phase.formattedPrice,
                    "${phase.formattedPrice} every $period. Automatically renews until cancelled in Google Play.")
            }
            emit(state.copy(available = true, loading = false, offers = offers,
                message = if (offers.isEmpty()) "No approved subscription plans are available from Google Play yet." else null))
        }
    }
    fun purchase(activity: Activity, productId: String) {
        if (closed || !client.isReady) { emit(state.copy(message = "Refresh subscriptions before purchasing.")); return }
        val product = details[productId] ?: return
        val params = BillingFlowParams.newBuilder().setObfuscatedAccountId(config.accountId)
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product.first).setOfferToken(product.second).build())).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) handleResult(result)
    }
    fun restore() {
        if (closed || !client.isReady) { emit(state.copy(message = "Refresh subscriptions to connect to Google Play.")); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { result, purchases ->
            if (closed) return@queryPurchasesAsync
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.isEmpty()) emit(state.copy(message = "No Google Play subscriptions were found for this Play account."))
                purchases.forEach(::received)
            } else handleResult(result)
        }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (closed) return
        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.orEmpty().forEach(::received)
        else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
        else handleResult(result)
    }
    private fun received(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            emit(state.copy(message = "Payment is pending in Google Play. Paid access starts only after payment and server verification.")); return
        }
        if (!maySubmitPlayPurchase(config, purchase.packageName, purchase.products,
                purchase.accountIdentifiers?.obfuscatedAccountId, purchase.purchaseState == Purchase.PurchaseState.PURCHASED)) {
            emit(state.copy(message = "This purchase cannot be restored to this StoxSim account. Sign in to the account used when purchasing.")); return
        }
        if (!submitted.add(purchase.purchaseToken)) return
        emit(state.copy(message = "Verifying your subscription with StoxSim…"))
        verify(owner, purchase.products.single(), purchase.purchaseToken)
    }
    fun verificationFinished(token: String, success: Boolean) {
        submitted.remove(token)
        emit(state.copy(message = if (success) "Purchase verified. Your current benefits are shown below."
            else "Purchase verification is incomplete. Reconnect and use Restore purchases; do not purchase again."))
    }
    private fun handleResult(result: BillingResult) {
        emit(state.copy(loading = false, message = if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED)
            "Purchase cancelled. Your benefits have not changed." else "Google Play could not complete this request. Retry or restore purchases."))
    }
    fun close() { closed = true; details.clear(); submitted.clear(); client.endConnection() }
}
