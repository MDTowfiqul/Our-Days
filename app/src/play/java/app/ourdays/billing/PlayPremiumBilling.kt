package app.ourdays.billing

import android.app.Activity
import android.content.Context
import app.ourdays.data.PREMIUM_PRODUCT_ID
import app.ourdays.data.PremiumEntitlement
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Play Billing for the one-time product [PREMIUM_PRODUCT_ID]. The product
 * must exist in Play Console (Monetize -> Products) or every purchase
 * attempt fails with "product not found" — see the pull request body.
 * One purchase flow at a time; the listener completes the pending result.
 */
class PlayPremiumBilling(context: Context) : PremiumBilling {

    private var pending = CompletableDeferred<PremiumEntitlement?>()

    private val listener: PurchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            pending.complete(null)
            return@PurchasesUpdatedListener
        }
        val purchase = purchases?.firstOrNull {
            it.products.contains(PREMIUM_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (purchase == null) {
            pending.complete(null)
            return@PurchasesUpdatedListener
        }
        if (purchase.isAcknowledged) {
            pending.complete(purchase.toEntitlement())
        } else {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            ) { ack ->
                pending.complete(if (ack.responseCode == BillingClient.BillingResponseCode.OK) purchase.toEntitlement() else null)
            }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(listener)
        .enablePendingPurchases()
        .build()

    override suspend fun queryOwned(): PremiumEntitlement? {
        if (!ensureConnected()) return null
        val purchases = suspendCancellableCoroutine<List<Purchase>> { cont ->
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            ) { billingResult, list ->
                if (cont.isActive) {
                    cont.resume(if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) list else emptyList())
                }
            }
        }
        val purchase = purchases.firstOrNull {
            it.products.contains(PREMIUM_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        } ?: return null
        if (!purchase.isAcknowledged && !acknowledge(purchase)) return null
        return purchase.toEntitlement()
    }

    override suspend fun launchPurchase(activity: Activity): PremiumEntitlement? {
        if (!ensureConnected()) throw IOException("Billing service unavailable")
        val details = queryDetails() ?: throw IOException("Premium product not found in Play Console")
        pending = CompletableDeferred()
        val flowResult = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build(),
        )
        if (flowResult.responseCode != BillingClient.BillingResponseCode.OK) {
            throw IOException("Could not start the purchase flow")
        }
        return pending.await()
    }

    private suspend fun ensureConnected(): Boolean = suspendCancellableCoroutine { cont ->
        if (client.isReady) {
            cont.resume(true)
            return@suspendCancellableCoroutine
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (cont.isActive) cont.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
            }

            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    private suspend fun queryDetails(): ProductDetails? = suspendCancellableCoroutine { cont ->
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PREMIUM_PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build(),
        ) { billingResult, detailsList ->
            if (cont.isActive) {
                cont.resume(
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        detailsList.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
                    } else {
                        null
                    },
                )
            }
        }
    }

    private suspend fun acknowledge(purchase: Purchase): Boolean = suspendCancellableCoroutine { cont ->
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
        ) { billingResult ->
            if (cont.isActive) cont.resume(billingResult.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    private fun Purchase.toEntitlement(): PremiumEntitlement =
        // orderId can be missing for very old purchases; the token is stable per purchase
        PremiumEntitlement(orderId ?: purchaseToken, purchaseTime, "play")
}
