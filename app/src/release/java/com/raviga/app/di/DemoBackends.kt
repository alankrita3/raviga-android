package com.raviga.app.di

import com.raviga.app.data.api.DemoBackend
import com.raviga.app.data.local.CacheStore
import kotlinx.serialization.json.Json

/** Release builds have no demo backend (build 8 gate: no demo or mock code in Release). */
object DemoBackends {
    @Suppress("UNUSED_PARAMETER")
    fun create(cache: CacheStore, json: Json): DemoBackend? = null
}
