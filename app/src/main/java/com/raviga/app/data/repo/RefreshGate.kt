package com.raviga.app.data.repo

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One resource's refreshes, one at a time. A non-forced call that finds a result younger than
 * [freshForMs] reuses it instead of fetching again, so the start-up and the home screen asking
 * at the same moment cost one request, not three. Forced calls (pull to refresh, retry, a push
 * saying something changed, after an action) always fetch.
 */
class RefreshGate<T : Any>(
    private val freshForMs: Long = 5_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private var last: T? = null
    private var lastAt = 0L

    suspend fun run(force: Boolean, fetch: suspend () -> T): T = mutex.withLock {
        val cached = last
        if (!force && cached != null && now() - lastAt < freshForMs) return cached
        fetch().also {
            last = it
            lastAt = now()
        }
    }

    /** Forgets the last result (sign-out, recovery, deletion). */
    fun reset() {
        last = null
    }
}
