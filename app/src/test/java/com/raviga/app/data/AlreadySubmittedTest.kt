package com.raviga.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.api.Section
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.data.local.CacheStore
import com.raviga.app.data.repo.AiRepository
import com.raviga.app.data.repo.DraftRepository
import com.raviga.app.data.repo.ProjectRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * R-092 #19 / backend R-091 (as iOS c0b7594): a submit whose answer was lost, retried, is told which project the
 * server already has; the draft becomes that project instead of being re-quoted and charged twice.
 */
class AlreadySubmittedTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    /** A CacheStore without a Context: its folder and Json set by hand (the project refresh writes one file). */
    private fun unusedCache(): CacheStore {
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

    private fun fixture(name: String): String = File(javaClass.classLoader!!.getResource("fixtures/$name")!!.toURI()).readText()

    @Test fun theDraftBecomesTheProjectTheServerNames() = runBlocking {
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java)
        val store = DraftStore(Files.createTempDirectory("drafts").toFile(), json, Sealer.None)
        val drafts = DraftRepository(store, AiRepository(api, json, JobRunner(api, json)), ProjectRepository(api, json, unusedCache()))
        drafts.load()
        val doc = Document(version = 1, title = "Bakery site", sections = listOf(Section("summary", "Summary", "A one-page site.")))
        store.create(
            LocalDraft(
                id = "ld_1", title = "Bakery site", createdAt = "a", updatedAt = "a",
                versions = listOf(LocalVersion(1, doc, "a", "draft")),
                quote = Quote(id = "qt_1", documentVersion = 1, credits = 7), quoteToken = "tok", quotedVersion = 1,
            ),
        )
        val project = json.parseToJsonElement(fixture("project-submitted.json"))
        val projectId = project.let { (it as kotlinx.serialization.json.JsonObject)["id"].toString().trim('"') }
        server.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """{"error":{"code":"quote_stale","message":"You already submitted this brief as RA-TEST01.","details":{"projectId":"$projectId","ref":"RA-TEST01"}}}""",
            ),
        )
        try {
            drafts.submit("ld_1", "octocat", null, "key-1")
            fail("expected quote_stale")
        } catch (e: ApiException) {
            assertEquals(ApiException.QUOTE_STALE, e.code)
            assertEquals(projectId, e.detailString("projectId"))
        }
        server.enqueue(MockResponse().setBody(project.toString()))
        val adopted = drafts.adoptSubmitted("ld_1", projectId)
        assertEquals(projectId, adopted.id)
        val after = drafts.get("ld_1")
        assertEquals(projectId, after?.serverProjectId)
        assertTrue(after?.isSubmitted == true)
        assertNull("the used token is gone, so nothing re-quotes it", after?.quoteToken)
        assertEquals(1, after?.submittedVersion)
    }
}
