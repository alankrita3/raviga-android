package com.raviga.app.data.billing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review 2026-11-03 (Android), contract section 13 (app_user_id is clientId): RevenueCat's log-in to the phone's new
 * client (after a restore, a link or a new account) failed silently when offline and was counted as done, so the
 * next purchase was booked to the previous client. A purchase or restore now checks the buyer first.
 */
class StoreBuyerTest {

    private class FakeStore(var user: String?, private val logInWorks: Boolean) : BillingManager.StoreUser {
        override val current: String? get() = user
        var logIns = 0
        override suspend fun logIn(clientId: String): Boolean {
            logIns++
            if (logInWorks) user = clientId
            return logInWorks
        }
    }

    @Test fun theRightBuyerNeedsNoLogIn() = runBlocking {
        val store = FakeStore(user = "cl_new", logInWorks = true)
        assertTrue(BillingManager.ensureBuyer(store, "cl_new"))
        assertEquals(0, store.logIns)
    }

    @Test fun aStoreStillOnThePreviousClientLogsInFirst() = runBlocking {
        val store = FakeStore(user = "cl_old", logInWorks = true)
        assertTrue(BillingManager.ensureBuyer(store, "cl_new"))
        assertEquals("cl_new", store.user)
    }

    @Test fun aLogInThatFailsStopsThePurchase() = runBlocking {
        val store = FakeStore(user = "cl_old", logInWorks = false)
        assertFalse("never sold to cl_old", BillingManager.ensureBuyer(store, "cl_new"))
    }

    @Test fun noClientNoSale() = runBlocking {
        assertFalse(BillingManager.ensureBuyer(FakeStore(user = "cl_old", logInWorks = true), null))
    }
}
