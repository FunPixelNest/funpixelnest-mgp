package com.funpixelnest.mgp.billing

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.funpixelnest.mgp.platform.LocalPrefs

/**
 * The one-time "Remove Ads" purchase through Google Play Billing (non-consumable in-app product [productId]).
 * Google Play is the source of truth: purchases are re-checked on every start ("restore"), and the last
 * known answer is cached so ads stay off while Play is offline.
 */
class RemoveAdsBilling(private val activity: Activity, private val productId: String) {
    var owned by mutableStateOf(LocalPrefs.get(CACHE_KEY) == "1")
        private set
    var price by mutableStateOf<String?>(null)
        private set
    var available by mutableStateOf(false)
        private set

    /** Called with null after a successful purchase, or a message for the player. */
    private var pendingResult: ((String?) -> Unit)? = null
    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "billing setup: ${result.debugMessage}")
                    return
                }
                loadProduct()
                restore()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, query ->
            val d = query.productDetailsList.firstOrNull()
            activity.runOnUiThread {
                details = d
                price = d?.oneTimePurchaseOfferDetails?.formattedPrice
                available = result.responseCode == BillingClient.BillingResponseCode.OK && d != null
            }
        }
    }

    /** Re-reads past purchases from Google Play. */
    fun restore() {
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val mine = purchases.filter { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            mine.forEach { acknowledge(it) }
            activity.runOnUiThread { markOwned(mine.isNotEmpty()) }
        }
    }

    fun buy(onResult: (String?) -> Unit) {
        val d = details
        if (!client.isReady || d == null) {
            onResult("Remove Ads isn't available right now. Please check your Google Play connection and try again.")
            return
        }
        if (owned) {
            onResult(null)
            return
        }
        pendingResult = onResult
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        val launch = client.launchBillingFlow(activity, params)
        if (launch.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingResult = null
            onResult("Couldn't open Google Play (${launch.debugMessage.ifBlank { "code ${launch.responseCode}" }}).")
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val callback = pendingResult
        pendingResult = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val mine = purchases.orEmpty().filter { productId in it.products }
                val bought = mine.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                mine.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { acknowledge(it) }
                activity.runOnUiThread {
                    if (bought) markOwned(true)
                    callback?.invoke(
                        if (bought) null
                        else "Your payment is pending. Ads will be removed as soon as Google Play confirms it.",
                    )
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> activity.runOnUiThread {
                markOwned(true)
                callback?.invoke(null)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> activity.runOnUiThread { callback?.invoke("Purchase cancelled.") }
            else -> activity.runOnUiThread { callback?.invoke("The purchase didn't go through (${result.debugMessage.ifBlank { "code ${result.responseCode}" }}).") }
        }
    }

    /** Google refunds purchases that aren't acknowledged within 3 days. */
    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        client.acknowledgePurchase(params) { r -> if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge: ${r.debugMessage}") }
    }

    private fun markOwned(value: Boolean) {
        owned = value
        LocalPrefs.set(CACHE_KEY, if (value) "1" else "0")
    }

    private companion object {
        const val TAG = "FpnBilling"
        const val CACHE_KEY = "remove_ads_owned"
    }
}
