package com.raviga.app.data.repo

import android.app.Activity
import com.raviga.app.data.api.CreditPack
import com.raviga.app.data.api.CreditsResponse
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.apiCall
import com.raviga.app.data.billing.BillingManager
import com.raviga.app.data.billing.PurchaseOutcome
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
    /** The in-process demo backend in debug builds ([api] is wrapped by the consent guard, so not the demo itself). */
    private val demo: com.raviga.app.data.api.DemoBackend? = null,
    /** Remembers on the phone that a paid purchase still needs a sync (prefs' creditsSyncDue). */
    private val syncDue: suspend (Boolean) -> Unit = {},
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

    /**
     * QA J5 (as iOS's Show more): the next older page of the ledger, after [CreditsResponse.nextCursor], added below
     * the rows already shown. A refresh goes back to the newest page, as on iOS.
     */
    suspend fun loadOlder() {
        val current = _credits.value ?: return
        val cursor = current.nextCursor ?: return
        val page = apiCall(json) { api.credits(before = cursor) }
        _credits.value = withOlder(current, page)
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
            demo.purchase(pack.productId)
            refresh()
            return PurchaseOutcome.Success
        }
        // R-094 P2-8: a slow network holds the store sheet at most 5 s for this read; then the phone's copy stands.
        val before = baseline(fresh = { refresh().balance }, cached = _credits.value?.balance)
        return book(
            before = before,
            store = { billing.purchase(activity, pack.productId) },
            balance = { runCatching { refresh().balance }.getOrNull() },
            sync = { runCatching { sync() }.getOrNull() },
            syncDue = { due -> runCatching { syncDue(due) } },
        )
    }

    companion object {
        const val BASELINE_CAP_MS = 5_000L

        /** The balance to measure a purchase against: a fresh read within [capMs], else [cached]. */
        internal suspend fun baseline(fresh: suspend () -> Int, cached: Int?, capMs: Long = BASELINE_CAP_MS): Int? =
            runCatching { kotlinx.coroutines.withTimeoutOrNull(capMs) { fresh() } }.getOrNull() ?: cached

        /** [older] added below [current] without repeating a row; the cursor moves on, the balance is the newer read. */
        internal fun withOlder(current: CreditsResponse, older: CreditsResponse): CreditsResponse {
            val known = current.ledger.mapTo(HashSet()) { it.id }
            return current.copy(
                balance = older.balance,
                ledger = current.ledger + older.ledger.filter { known.add(it.id) },
                nextCursor = Paging.next(sent = current.nextCursor, answered = older.nextCursor),   // R-094 P2-9
            )
        }

        /**
         * The booking half of [purchase], apart from the store so it can be tested (build 8 gate: purchase booking).
         * Anything but a store Success is returned as is. Then the balance is read up to 5 times, 2 s apart; if it
         * hasn't risen above [before], one sync is asked for. Only a balance seen above [before] is Success; an
         * unknown [before] can't show that, so it is NotBookedYet (the next Credits open syncs, R-092 #20).
         */
        internal suspend fun book(
            before: Int?,
            store: suspend () -> PurchaseOutcome,
            balance: suspend () -> Int?,
            sync: suspend () -> Int?,
            wait: suspend () -> Unit = { delay(2_000) },
            /**
             * Review 2026-11-03 (Android): the store took the money, so a sync is due (true) from that moment until
             * the credits are seen (false). Marked before the wait, so leaving Credits or the app being ended during
             * the 10 s still has the next Credits open sync it (R-092 #20); it used to be marked only at the end.
             */
            syncDue: suspend (Boolean) -> Unit = {},
        ): PurchaseOutcome {
            val outcome = store()
            if (outcome !is PurchaseOutcome.Success) return outcome
            syncDue(true)
            repeat(5) {
                wait()
                val now = balance()
                if (now != null && before != null && now > before) return PurchaseOutcome.Success.also { syncDue(false) }
            }
            val synced = sync()
            return if (synced != null && before != null && synced > before) PurchaseOutcome.Success.also { syncDue(false) } else PurchaseOutcome.NotBookedYet
        }
    }

    /**
     * R-094 P2-8 (iOS review 2): measured against a fresh read, so the screen can say what came back; [Restored.added]
     * is 0 when nothing new was booked (or the starting balance couldn't be read).
     */
    suspend fun restore(): Restored {
        val before = baseline(fresh = { refresh().balance }, cached = null)
        if (billing.isAvailable) billing.restore()
        val after = sync()
        return Restored(balance = after, added = if (before != null && after > before) after - before else 0)
    }

    data class Restored(val balance: Int, val added: Int)

    val storeReady: Boolean get() = billing.isReady
}
