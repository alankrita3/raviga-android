package com.raviga.app.data.repo

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.LegalConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * R-149 (iOS found it from tester feedback; Android had it too): the terms changed while the app was open. The sheet
 * sent the version from the /config cached at launch, the server refused it (400 invalid_argument, "not the current
 * terms version") and every tap sent the same stale version again, until the app was restarted.
 */
class StaleTermsTest {
    private val atLaunch = LegalConfig(termsVersion = "2026-11-12", privacyVersion = "2026-11-12")
    private val now = LegalConfig(termsVersion = "2026-11-17", privacyVersion = "2026-11-17")
    private val later = LegalConfig(termsVersion = "2026-11-18", privacyVersion = "2026-11-18")
    private fun stale() = ApiException(ApiException.INVALID_ARGUMENT, 400, "termsVersion is not the current terms version")

    /** The server: accepts only its current versions. */
    private class Server(var current: LegalConfig) {
        val sent = mutableListOf<String>()
        fun put(v: LegalConfig): String {
            sent += v.termsVersion
            if (v.termsVersion != current.termsVersion || v.privacyVersion != current.privacyVersion) {
                throw ApiException(ApiException.INVALID_ARGUMENT, 400, "termsVersion is not the current terms version")
            }
            return "ok"
        }
    }

    @Test fun theSheetSendsTheServersVersionsNotTheOnesCachedAtLaunch() = runBlocking {
        val server = Server(now)
        val (agreed, result) = agreeToCurrentVersions(atLaunch, fresh = { server.current }) { server.put(it) }
        assertEquals("ok", result)
        assertEquals(now, agreed)
        assertEquals("one PUT, with the current version", listOf("2026-11-17"), server.sent)
    }

    @Test fun beforeTheFixTheCachedVersionWasRefused() {
        // What the app did before R-149: the cached version, as is.
        val server = Server(now)
        try {
            server.put(atLaunch)
            fail("expected 400")
        } catch (e: ApiException) {
            assertEquals(ApiException.INVALID_ARGUMENT, e.code)
        }
    }

    @Test fun refusedForTheVersionItReadsConfigAgainAndRetriesOnce() = runBlocking {
        // The terms moved again between the sheet's read and the tap.
        val server = Server(now)
        var reads = 0
        val (agreed, _) = agreeToCurrentVersions(atLaunch, fresh = { reads++; if (reads == 1) now.also { server.current = later } else server.current }) { server.put(it) }
        assertEquals(later, agreed)
        assertEquals(listOf("2026-11-17", "2026-11-18"), server.sent)
    }

    @Test fun offlineItSendsTheCopyItHas() = runBlocking {
        val server = Server(atLaunch)
        val (agreed, _) = agreeToCurrentVersions(atLaunch, fresh = { throw ApiException(ApiException.NETWORK, 0, "offline") }) { server.put(it) }
        assertEquals(atLaunch, agreed)
    }

    @Test fun refusedAgainWithTheSameVersionsTheErrorGoesToTheButton() = runBlocking {
        try {
            agreeToCurrentVersions(atLaunch, fresh = { now }) { throw stale() }
            fail("expected the refusal")
        } catch (e: ApiException) {
            assertEquals(ApiException.INVALID_ARGUMENT, e.code)
        }
    }

    @Test fun anyOtherErrorIsNotRetried() = runBlocking {
        var puts = 0
        try {
            agreeToCurrentVersions(atLaunch, fresh = { now }) { puts++; throw ApiException(ApiException.NETWORK, 0, "offline") }
            fail("expected the error")
        } catch (e: ApiException) {
            assertEquals(ApiException.NETWORK, e.code)
        }
        assertEquals(1, puts)
    }
}
