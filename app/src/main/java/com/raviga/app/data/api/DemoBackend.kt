package com.raviga.app.data.api

/**
 * The in-process demo backend, for debug builds only: its code lives in `src/debug` and is not in a release build
 * (build 8 gate), where [com.raviga.app.di.DemoBackends] gives null. [api] serves every call like the real backend.
 */
interface DemoBackend {
    val api: RavigaApi

    /** Plays the team's next move on the latest submitted project now; the line to show, or null if none. */
    suspend fun advanceLatest(): String?
    suspend fun addCredits(credits: Int)
    suspend fun reset()
    suspend fun purchase(productId: String)
}
