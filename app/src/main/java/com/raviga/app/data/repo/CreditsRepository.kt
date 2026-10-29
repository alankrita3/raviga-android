package com.raviga.app.data.repo

import android.app.Activity
import com.raviga.app.data.api.CreditPack
import com.raviga.app.data.api.CreditsResponse
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.apiCall
import com.raviga.app.data.billing.BillingManager
import com.raviga.app.data.billing.PurchaseOutcome
import com.raviga.app.data.demo.DemoApi
import com.raviga.app.data.local.CacheStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/** Balance and ledger, plus the purchase path through RevenueCat (section 13). */
class CreditsRepository(
    private val api: RavigaApi,
    private val json: Json,
    private val cache: CacheStore,
    private val billing: BillingManager,
    /** The in-process demo backend when there is one ([api] is wrapped by the consent guard, so not a DemoApi). */
    private val demo: DemoApi? = null,
) {
    private val _credits = MutableStateFlow<CreditsResponse?>(null)
    val credits: StateFlow<CreditsResponse?> = _credits.asStateFlow()

    /** Store-formatted prices by product id, once RevenueCat has them. */
    private val _prices = MutableStateFlow<Map<String, String>>(emptyMap())
    val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    val isDemoBilling: Boolean get() = demo != null && !billing.isAvailable

    private val gate = RefreshGate<CreditsResponse>()

    suspend fun warmFromCache() {
        cache.read(CacheStore.CREDITS, CreditsResponse.serializer())?.let { _credits.value = it }
    }

    fun reset() {
        _credits.value = null
        gate.reset()
    }

    /** [force] false lets a refresh from a few seconds ago stand (see [RefreshGate]). */
    suspend fun refresh(force: Boolean = true): CreditsResponse = gate.run(force) {
        val response = apiCall(json) { api.credits() }
        _credits.value = response
        cache.write(CacheStore.CREDITS, CreditsResponse.serializer(), response)
        response
    }

    /** Asks the backend to reconcile with RevenueCat, then reloads the ledger. */
    suspend fun sync(): Int {
        apiCall(json) { api.syncCredits(UUID.randomUUID().toString()) }
        return refresh().balance
    }

    suspend fun loadPrices(packs: List<CreditPack>) {
        if (!billing.isAvailable || packs.isEmpty()) return
        runCatching { billing.products(packs.map { it.productId }) }
            .onSuccess { products -> _prices.value = products.associate { it.id.substringBefore(':') to it.price.formatted } }
    }

    /**
     * Buys a pack. The webhook usually books the credits before the store
     * sheet closes; if the balance has not moved within 10s we ask for a sync.
     * Success means the credits are on the ledger; [PurchaseOutcome.NotBookedYet]
     * means the store took the money and the backend has not caught up.
     */
    suspend fun purchase(activity: Activity, pack: CreditPack): PurchaseOutcome {
        if (demo != null && !billing.isAvailable) {
            demo.demoPurchase(pack.productId)
            refresh()
            return PurchaseOutcome.Success
        }
        val before = runCatching { refresh().balance }.getOrNull() ?: _credits.value?.balance
        val outcome = billing.purchase(activity, pack.productId)
        if (outcome !is PurchaseOutcome.Success) return outcome
        for (attempt in 1..5) {
            delay(2_000)
            val now = runCatching { refresh().balance }.getOrNull()
            if (now != null && before != null && now > before) return PurchaseOutcome.Success
        }
        val synced = runCatching { sync() }.getOrNull()
        return if (synced != null && before != null && synced > before) PurchaseOutcome.Success else PurchaseOutcome.NotBookedYet
    }

    suspend fun restore(): Int {
        if (billing.isAvailable) billing.restore()
        return sync()
    }

    val storeReady: Boolean get() = billing.isReady
}
