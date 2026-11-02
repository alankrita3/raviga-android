package com.raviga.app.data.billing

import android.app.Activity
import android.app.Application
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.ProductType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.getProductsWith
import com.revenuecat.purchases.logInWith
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith
import com.raviga.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PurchaseOutcome {
    data object Success : PurchaseOutcome
    data object Cancelled : PurchaseOutcome
    /** The store has not settled the payment yet (common with UPI); credits follow once it does. */
    data object Pending : PurchaseOutcome
    /** Paid, but the credits were not on the ledger yet when we stopped waiting. */
    data object NotBookedYet : PurchaseOutcome
    data class Failed(val message: String) : PurchaseOutcome
}

class BillingException(val error: PurchasesError) : Exception(error.message)

/**
 * RevenueCat over Google Play Billing. The backend is the ledger of record:
 * after any purchase or restore the app calls credits/sync and trusts that.
 * `app_user_id` is the backend-issued clientId so webhooks land on the right
 * ledger. Without a key (no secrets.properties yet) billing is unavailable and
 * the demo backend grants credits directly.
 */
class BillingManager(private val app: Application, private val apiKey: String) {

    val isAvailable: Boolean get() = apiKey.isNotBlank()
    private var configuredFor: String? = null

    /** Ready to sell: a key exists and the SDK knows which client is buying. */
    val isReady: Boolean get() = isAvailable && configuredFor != null

    /**
     * Points RevenueCat at [clientId]: configure the first time, log in when the
     * device moves to another client. Never anonymous (contract section 13).
     */
    fun identify(clientId: String) {
        if (!isAvailable || configuredFor == clientId) return
        if (configuredFor == null) {
            if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(
                PurchasesConfiguration.Builder(app, apiKey)
                    .appUserID(clientId)
                    .build(),
            )
        } else {
            Purchases.sharedInstance.logInWith(clientId, onError = {}, onSuccess = { _, _ -> })
        }
        configuredFor = clientId
    }


    suspend fun products(productIds: List<String>): List<StoreProduct> = suspendCancellableCoroutine { cont ->
        if (!isReady) { cont.resumeWithException(IllegalStateException("The store isn't ready yet. Try again in a moment.")); return@suspendCancellableCoroutine }
        Purchases.sharedInstance.getProductsWith(
            productIds,
            ProductType.INAPP,
            onError = { if (cont.isActive) cont.resumeWithException(BillingException(it)) },
            onGetStoreProducts = { if (cont.isActive) cont.resume(it) },
        )
    }

    suspend fun purchase(activity: Activity, productId: String): PurchaseOutcome {
        val product = runCatching { products(listOf(productId)).firstOrNull() }
            .getOrElse { return PurchaseOutcome.Failed("Google Play isn't available right now. Try again in a moment.") }
            ?: return PurchaseOutcome.Failed("This pack is not available in your store right now.")
        return suspendCancellableCoroutine { cont ->
            Purchases.sharedInstance.purchaseWith(
                PurchaseParams.Builder(activity, product).build(),
                onError = { error, userCancelled ->
                    if (!cont.isActive) return@purchaseWith
                    cont.resume(
                        when {
                            userCancelled -> PurchaseOutcome.Cancelled
                            error.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
                            // R-092 #27: the store's own text is never shown.
                            else -> PurchaseOutcome.Failed("The purchase didn't go through. Try again.")
                        },
                    )
                },
                onSuccess = { _, _ -> if (cont.isActive) cont.resume(PurchaseOutcome.Success) },
            )
        }
    }

    suspend fun restore(): CustomerInfo = suspendCancellableCoroutine { cont ->
        if (!isReady) { cont.resumeWithException(IllegalStateException("The store isn't ready yet. Try again in a moment.")); return@suspendCancellableCoroutine }
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { if (cont.isActive) cont.resumeWithException(BillingException(it)) },
            onSuccess = { if (cont.isActive) cont.resume(it) },
        )
    }
}
