package com.raviga.app.di

import com.raviga.app.data.api.DemoBackend
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.demo.DemoApi
import com.raviga.app.data.local.CacheStore
import kotlinx.serialization.json.Json

/** Debug builds: the in-app demo backend ([DemoApi]). The release source set's version gives null. */
object DemoBackends {
    fun create(cache: CacheStore, json: Json): DemoBackend? = DemoApi(cache, json).let { demo ->
        object : DemoBackend {
            override val api: RavigaApi = demo
            override suspend fun advanceLatest() = demo.demoAdvanceLatest()
            override suspend fun addCredits(credits: Int) { demo.demoAddCredits(credits) }
            override suspend fun endFreeFixes() = demo.demoEndFreeFixes()
            override suspend fun reset() { demo.demoReset() }
            override suspend fun purchase(productId: String) { demo.demoPurchase(productId) }
        }
    }
}
