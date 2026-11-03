package com.raviga.app.data.repo

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.local.CacheStore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Review #23 (Android): a project list fetched for the previous client never lands after a switch. */
class ClientSwitchListTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun cache(): CacheStore {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val cache = unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CacheStore::class.java) as CacheStore
        CacheStore::class.java.declaredFields.forEach { f ->
            f.isAccessible = true
            when (f.type) {
                Json::class.java -> f.set(cache, json)
                File::class.java -> f.set(cache, Files.createTempDirectory("cache").toFile())
                kotlinx.coroutines.sync.Mutex::class.java -> f.set(cache, kotlinx.coroutines.sync.Mutex())
            }
        }
        return cache
    }

    private fun repo(): ProjectRepository {
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java)
        return ProjectRepository(api, json, cache())
    }

    private val oldClientsList = """{"projects":[{"id":"pr_old","ref":"RA-OLD","status":"submitted","title":"Old client's app"}],"nextCursor":null}"""

    @Test fun aListThatLandsAfterTheSwitchIsDropped() = runBlocking {
        val projects = repo()
        server.enqueue(MockResponse().setBody(oldClientsList).setHeadersDelay(400, TimeUnit.MILLISECONDS))
        val fetch = async { projects.refreshAll() }
        delay(100)          // the request is out; the phone moves to another client
        projects.reset()
        fetch.await()
        assertTrue("nothing of the old client's list", projects.summaries.value.isEmpty())
    }

    @Test fun withoutASwitchTheListLands() = runBlocking {
        val projects = repo()
        server.enqueue(MockResponse().setBody(oldClientsList))
        projects.refreshAll()
        assertEquals(listOf("RA-OLD"), projects.summaries.value.map { it.ref })
    }
}
