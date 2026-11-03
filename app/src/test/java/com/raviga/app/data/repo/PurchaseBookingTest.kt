package com.raviga.app.data.repo

import com.raviga.app.data.billing.PurchaseOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Build 8 gate (purchase booking), and R-092 #20: Success only once the balance is seen above what it was; otherwise
 * one sync, then NotBookedYet ("Your purchase is confirmed. The credits arrive in a moment.").
 */
class PurchaseBookingTest {

    private class Run(val balances: List<Int?>, val synced: Int?, val onWait: () -> Unit = {}) {
        var reads = 0; var syncs = 0; var waits = 0
        /** The phone's creditsSyncDue flag as the booking leaves it. */
        var due = false
        suspend fun book(before: Int?, store: PurchaseOutcome = PurchaseOutcome.Success) = CreditsRepository.book(
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
        val run = Run(balances = List(5) { 10 }, synced = 10, onWait = { throw kotlinx.coroutines.CancellationException("left Credits") })
        runCatching { run.book(before = 10) }
        assertEquals("still due after the wait was cut short", true, run.due)
    }

    @Test fun aBookedPurchaseIsNoLongerDue() = runBlocking {
        val seen = Run(balances = listOf(10, 25), synced = null)
        seen.book(before = 10)
        assertEquals(false, seen.due)
        val synced = Run(balances = List(5) { 10 }, synced = 25)
        synced.book(before = 10)
        assertEquals(false, synced.due)
        val notYet = Run(balances = List(5) { 10 }, synced = 10)
        notYet.book(before = 10)
        assertEquals("NotBookedYet stays due", true, notYet.due)
        val cancelled = Run(balances = emptyList(), synced = null)
        cancelled.book(before = 10, store = PurchaseOutcome.Cancelled)
        assertEquals("nothing paid, nothing due", false, cancelled.due)
    }

    @Test fun theWebhookBookedItWhileWeWaited() = runBlocking {
        val run = Run(balances = listOf(10, 25), synced = null)
        assertEquals(PurchaseOutcome.Success, run.book(before = 10))
        assertEquals("stops at the second read", 2, run.reads)
        assertEquals("no sync needed", 0, run.syncs)
    }

    @Test fun notBookedAfterTenSecondsSoOneSyncBooksIt() = runBlocking {
        val run = Run(balances = List(5) { 10 }, synced = 25)
        assertEquals(PurchaseOutcome.Success, run.book(before = 10))
        assertEquals(5, run.reads)
        assertEquals(5, run.waits)
        assertEquals(1, run.syncs)
    }

    @Test fun storeTookTheMoneyButNothingIsBookedYet() = runBlocking {
        val run = Run(balances = List(5) { 10 }, synced = 10)
        assertEquals(PurchaseOutcome.NotBookedYet, run.book(before = 10))
        val offline = Run(balances = List(5) { null }, synced = null)
        assertEquals("unreadable balance and a failed sync", PurchaseOutcome.NotBookedYet, offline.book(before = 10))
    }

    @Test fun anUnknownStartingBalanceCantProveABooking() = runBlocking {
        val run = Run(balances = listOf(25, 25, 25, 25, 25), synced = 25)
        assertEquals(PurchaseOutcome.NotBookedYet, run.book(before = null))
    }

    @Test fun anythingButAStoreSuccessIsReturnedAsIs() = runBlocking {
        for (outcome in listOf(PurchaseOutcome.Cancelled, PurchaseOutcome.Pending, PurchaseOutcome.Failed("The purchase didn't go through. Try again."))) {
            val run = Run(balances = listOf(25), synced = 25)
            assertEquals(outcome, run.book(before = 10, store = outcome))
            assertEquals("no reads, no sync for $outcome", 0, run.reads + run.syncs + run.waits)
        }
    }
}
