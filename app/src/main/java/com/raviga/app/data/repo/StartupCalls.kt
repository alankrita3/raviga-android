package com.raviga.app.data.repo

import com.raviga.app.data.api.ApiException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Review #13 (Android), the PM's decision (as iOS R2): with an account cached on the phone the app opens at once and
 * refreshes behind; with nothing cached, each start-up call gets [CAP_MS] and a slow one counts as offline (the
 * "Couldn't reach Raviga" line with Try again), instead of the 20 s connect / 60 s read timeouts.
 */
object StartupCalls {
    const val CAP_MS = 5_000L

    suspend fun <T> capped(ms: Long = CAP_MS, block: suspend () -> T): T =
        try {
            withTimeout(ms) { block() }
        } catch (e: TimeoutCancellationException) {
            throw ApiException(ApiException.NETWORK, 0, "Raviga took too long to answer.", cause = e)
        }

    /** The app can open from the phone's copy: agreed here, registered, and the account read before. */
    fun opensFromCache(agreed: Boolean, registered: Boolean, hasCachedMe: Boolean): Boolean = agreed && registered && hasCachedMe
}
