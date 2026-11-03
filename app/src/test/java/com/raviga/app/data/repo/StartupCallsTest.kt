package com.raviga.app.data.repo

import com.raviga.app.data.api.ApiException
import com.raviga.app.ui.status.StatusCopy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Review #13 (Android), the PM's decision: open from the phone's copy when there is one; else 5 s a call, then offline. */
class StartupCallsTest {
    @Test fun aSlowStartUpCallCountsAsOffline() = runBlocking {
        try {
            StartupCalls.capped(ms = 50) { delay(5_000); "late" }
            fail("expected the cap")
        } catch (e: ApiException) {
            assertEquals(ApiException.NETWORK, e.code)
            assertEquals("Couldn't reach Raviga. Check your connection and try again.", StatusCopy.errorLine(e.code, e.message))
        }
    }

    @Test fun aQuickOneAnswers() = runBlocking {
        assertEquals("me", StartupCalls.capped(ms = 1_000) { delay(10); "me" })
    }

    @Test fun onlyAnAccountReadBeforeOpensFromThePhone() {
        assertTrue(StartupCalls.opensFromCache(agreed = true, registered = true, hasCachedMe = true))
        assertFalse("never read /me (cache cleared)", StartupCalls.opensFromCache(agreed = true, registered = true, hasCachedMe = false))
        assertFalse("signed out here", StartupCalls.opensFromCache(agreed = true, registered = false, hasCachedMe = true))
        assertFalse("before Agree", StartupCalls.opensFromCache(agreed = false, registered = false, hasCachedMe = false))
    }
}
