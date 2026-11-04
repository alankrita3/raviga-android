package com.raviga.app.data.repo

import com.raviga.app.data.billing.PurchaseOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Build 8 gate (purchase booking), and R-092 #20: Success only once the balance is seen above what it was; otherwise
 * one sync, then NotBookedYet ("Your purchase is confirmed. The credits arrive in a moment.").
 */
class PurchaseBookingTest {

    private class Run(val balances: List<Double?>, val synced: Double?, val onWait: () -> Unit = {}) {
        var reads = 0; var syncs = 0; var waits = 0
        /** The phone's creditsSyncDue flag as the booking leaves it. */
        var due = false
        suspend fun book(before: Double?, store: PurchaseOutcome = PurchaseOutcome.Success) = CreditsRepository.book(
            before = before,
            store = { store },
            balance = { balances.getOrNull(reads++) },
            sync = { syncs++; synced },
            wait = { waits++; onWait() },
            syncDue = { due = it },
        )
    }

    /**
     * Review 2026-11-03 (Android): the flag was set only once the 10 s were over. Leaving Credits (the ViewModel's
     * scope ends) or the app being ended during them left a paid purchase with no sync on the next open.
     */
    @Test fun aSyncIsDueFromTheMomentTheStoreTookTheMoney() = runBlocking {
        val run = Run(balances = List(5) { 10.0 }, synced = 10.0, onWait = { throw kotlinx.coroutines.CancellationException("left Credits") })
        runCatching { run.book(before = 10.0) }
        assertEquals("still due after the wait was cut short", true, run.due)
    }

    @Test fun aBookedPurchaseIsNoLongerDue() = runBlocking {
        val seen = Run(balances = listOf(10.0, 25.0), synced = null)
        seen.book(before = 10.0)
        assertEquals(false, seen.due)
        val synced = Run(balances = List(5) { 10.0 }, synced = 25.0)
        synced.book(before = 10.0)
        assertEquals(false, synced.due)
        val notYet = Run(balances = List(5) { 10.0 }, synced = 10.0)
        notYet.book(before = 10.0)
        assertEquals("NotBookedYet stays due", true, notYet.due)
        val cancelled = Run(balances = emptyList(), synced = null)
        cancelled.book(before = 10.0, store = PurchaseOutcome.Cancelled)
        assertEquals("nothing paid, nothing due", false, cancelled.due)
    }

    @Test fun theWebhookBookedItWhileWeWaited() = runBlocking {
        val run = Run(balances = listOf(10.0, 25.0), synced = null)
        assertEquals(PurchaseOutcome.Success, run.book(before = 10.0))
        assertEquals("stops at the second read", 2, run.reads)
        assertEquals("no sync needed", 0, run.syncs)
    }

    @Test fun notBookedAfterTenSecondsSoOneSyncBooksIt() = runBlocking {
        val run = Run(balances = List(5) { 10.0 }, synced = 25.0)
        assertEquals(PurchaseOutcome.Success, run.book(before = 10.0))
        assertEquals(5, run.reads)
        assertEquals(5, run.waits)
        assertEquals(1, run.syncs)
    }

    @Test fun storeTookTheMoneyButNothingIsBookedYet() = runBlocking {
        val run = Run(balances = List(5) { 10.0 }, synced = 10.0)
        assertEquals(PurchaseOutcome.NotBookedYet, run.book(before = 10.0))
        val offline = Run(balances = List(5) { null }, synced = null)
        assertEquals("unreadable balance and a failed sync", PurchaseOutcome.NotBookedYet, offline.book(before = 10.0))
    }

    @Test fun anUnknownStartingBalanceCantProveABooking() = runBlocking {
        val run = Run(balances = listOf(25.0, 25.0, 25.0, 25.0, 25.0), synced = 25.0)
        assertEquals(PurchaseOutcome.NotBookedYet, run.book(before = null))
    }

    @Test fun anythingButAStoreSuccessIsReturnedAsIs() = runBlocking {
        for (outcome in listOf(PurchaseOutcome.Cancelled, PurchaseOutcome.Pending, PurchaseOutcome.Failed("The purchase didn't go through. Try again."))) {
            val run = Run(balances = listOf(25.0), synced = 25.0)
            assertEquals(outcome, run.book(before = 10.0, store = outcome))
            assertEquals("no reads, no sync for $outcome", 0, run.reads + run.syncs + run.waits)
        }
    }

    /** R-094 P2-8: the read before the store sheet waits at most the cap, then the phone's copy stands. */
    @Test fun aSlowBaselineReadFallsBackToTheCachedBalance() = runBlocking {
        val started = System.nanoTime()
        val before = CreditsRepository.baseline(fresh = { kotlinx.coroutines.delay(5_000); 99.0 }, cached = 10.0, capMs = 50)
        assertEquals(10.0, before)
        assertTrue("held about the cap, not the read", (System.nanoTime() - started) / 1_000_000 < 2_000)
        assertEquals("a quick read wins", 12.0, CreditsRepository.baseline(fresh = { 12.0 }, cached = 10.0))
        assertEquals("a failed read falls back", 10.0, CreditsRepository.baseline(fresh = { error("offline") }, cached = 10.0))
    }
}
