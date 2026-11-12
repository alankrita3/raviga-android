package com.raviga.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.data.api.AiEdits
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.JobRunner
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * R-124 (contract v0.16.0): the brief's server id is kept from the draft's 202, sent on every AI edit, fetched with
 * POST /briefs for a draft from before (or one the server forgot), and the pack is bought only with the flag.
 */
class AiEditsTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }
    private lateinit var store: DraftStore

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repo(): DraftRepository {
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java)
        store = DraftStore(Files.createTempDirectory("drafts").toFile(), json, Sealer.None)
        return DraftRepository(store, AiRepository(api, json, JobRunner(api, json)), ProjectRepository(api, json, unusedCache()))
    }

    /** Submitting isn't tested here, so the projects' cache is never touched: a placeholder without a Context. */
    private fun unusedCache(): CacheStore {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CacheStore::class.java) as CacheStore
    }

    /** A draft with a written brief, as this phone keeps it ([briefId] null: from before v0.16.0). */
    private suspend fun briefDraft(drafts: DraftRepository, briefId: String?, edits: AiEdits? = null): String {
        drafts.load()
        val now = "2026-11-12T10:00:00Z"
        val doc = Document(1, "Salon", listOf(Section("summary", "Summary", "A salon app."), Section("features", "Features", "- Booking")))
        return store.create(
            LocalDraft(DraftStore.newId(), "Salon", now, now, versions = listOf(LocalVersion(1, doc, now, "draft")), briefId = briefId, aiEdits = edits),
        ).id
    }

    private val result = """{"document":{"title":"Salon","sections":[{"id":"features","heading":"Features","body":"- Booking, shorter"}]}"""
    private fun started(edits: String, id: String = "br_1") =
        MockResponse().setResponseCode(202).setBody("""{"job":{"jobId":"j1","type":"regenerate","status":"queued"},"briefId":"$id","aiEdits":$edits}""")
    private fun done(edits: String, id: String = "br_1") =
        MockResponse().setBody("""{"jobId":"j1","status":"done","progress":1,"result":$result,"briefId":"$id","aiEdits":$edits}}""")
    private fun error(status: Int, code: String, message: String = "") =
        MockResponse().setResponseCode(status).setBody("""{"error":{"code":"$code","message":"$message"}}""")
    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
    private fun RecordedRequest.json() = json.parseToJsonElement(body.readUtf8()).jsonObject

    @Test fun aFirstDraftKeepsItsIdFromThe202() = runBlocking {
        val drafts = repo()
        drafts.load()
        val id = drafts.create().id
        drafts.addInput(id, "text", "A booking app for a salon. Customers book a haircut and get a reminder.")
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"job":{"jobId":"j1","type":"draft","status":"queued"},"briefId":"br_9","aiEdits":{"left":5,"free":true}}"""))
        server.enqueue(MockResponse().setBody("""{"jobId":"j1","status":"done","progress":1,"result":{"document":{"title":"Salon","sections":[]},"briefId":"br_9","aiEdits":{"left":5,"free":true}}}"""))
        drafts.writeBrief(id)
        assertEquals("br_9", drafts.get(id)?.briefId)
        assertEquals(AiEdits(5, true), drafts.get(id)?.aiEdits)
    }

    @Test fun regenerateSendsTheBriefsIdAndKeepsWhatsLeft() = runBlocking {
        val drafts = repo()
        val id = briefDraft(drafts, "br_1", AiEdits(5, true))
        server.enqueue(started("""{"left":4,"free":true}"""))
        server.enqueue(done("""{"left":4,"free":true}"""))
        drafts.regenerate(id, "features", "shorter")
        val sent = take()
        assertEquals("/v1/ai/regenerate", sent.path)
        val body = sent.json()
        assertEquals("br_1", body["briefId"]?.jsonPrimitive?.content)
        assertFalse("no pack unless the person said yes", body.containsKey("buyEditPack"))
        assertEquals(AiEdits(4, true), drafts.get(id)?.aiEdits)
        assertEquals("- Booking, shorter", drafts.get(id)?.document?.section("features")?.body)
    }

    @Test fun anOldDraftGetsAnIdFirstThenThePackIsAskedAndBoughtOnlyWithTheFlag() = runBlocking {
        val drafts = repo()
        val id = briefDraft(drafts, briefId = null)
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"briefId":"br_new","aiEdits":{"left":0,"free":false}}"""))
        server.enqueue(error(402, "ai_edit_pack_required", "This brief has no free AI edits. 5 AI edits cost a twelfth of a credit."))
        try {
            drafts.regenerate(id, "features", "shorter")
            fail("expected 402")
        } catch (e: ApiException) {
            assertEquals(ApiException.AI_EDIT_PACK_REQUIRED, e.code)
            assertEquals("This brief has no free AI edits. 5 AI edits cost a twelfth of a credit.", e.message)
        }
        assertEquals("/v1/briefs", take().path)
        assertEquals("br_new", take().json()["briefId"]?.jsonPrimitive?.content)
        assertEquals("br_new", drafts.get(id)?.briefId)
        assertEquals(AiEdits(0, false), drafts.get(id)?.aiEdits)
        assertEquals("nothing changed in the brief", 1, drafts.get(id)?.versions?.size)

        // The person said yes: the same call with buyEditPack, and the id it now has.
        server.enqueue(started("""{"left":4,"free":false}""", "br_new"))
        server.enqueue(done("""{"left":4,"free":false}""", "br_new"))
        drafts.regenerate(id, "features", "shorter", buyEditPack = true)
        val body = take().json()
        assertEquals("br_new", body["briefId"]?.jsonPrimitive?.content)
        assertEquals(true, body["buyEditPack"]?.jsonPrimitive?.boolean)
        assertEquals(AiEdits(4, false), drafts.get(id)?.aiEdits)
    }

    @Test fun anIdTheServerForgotIsReplacedOnceAndAddMoreUsesIt() = runBlocking {
        val drafts = repo()
        val id = briefDraft(drafts, "br_old", AiEdits(3, true))
        drafts.addInput(id, "text", "Also a loyalty card for the regulars who come every month.")
        server.enqueue(error(404, "brief_not_found"))
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"briefId":"br_new","aiEdits":{"left":0,"free":false}}"""))
        server.enqueue(error(402, "ai_edit_pack_required", "This brief has no free AI edits. 5 AI edits cost a twelfth of a credit."))
        try {
            drafts.writeBrief(id)
            fail("expected 402")
        } catch (e: ApiException) {
            assertEquals(ApiException.AI_EDIT_PACK_REQUIRED, e.code)
        }
        val first = take()
        assertEquals("/v1/ai/append", first.path)
        assertEquals("br_old", first.json()["briefId"]?.jsonPrimitive?.content)
        assertEquals("/v1/briefs", take().path)
        val again = take()
        assertEquals("/v1/ai/append", again.path)
        assertEquals("br_new", again.json()["briefId"]?.jsonPrimitive?.content)
        assertEquals(3, server.requestCount)
    }

    @Test fun reopeningReadsTheCountAndForgetsAnIdTheServerDoesnt() = runBlocking {
        val drafts = repo()
        val id = briefDraft(drafts, "br_1", AiEdits(5, true))
        server.enqueue(MockResponse().setBody("""{"briefId":"br_1","left":2,"used":3,"packs":0}"""))
        drafts.refreshAiEdits(id)
        assertEquals("/v1/briefs/br_1/ai-edits", take().path)
        assertEquals(AiEdits(2, true), drafts.get(id)?.aiEdits)
        server.enqueue(MockResponse().setBody("""{"briefId":"br_1","left":4,"used":6,"packs":1}"""))
        drafts.refreshAiEdits(id)
        assertEquals("a bought pack isn't free", AiEdits(4, false), drafts.get(id)?.aiEdits)
        server.enqueue(error(404, "brief_not_found"))
        drafts.refreshAiEdits(id)
        assertNull(drafts.get(id)?.briefId)
        assertNull("and the line hidden", drafts.get(id)?.aiEdits)
    }

    @Test fun aFailureOnOurSideReadsTheCountAgain() = runBlocking {
        val drafts = repo()
        val id = briefDraft(drafts, "br_1", AiEdits(4, true))
        server.enqueue(started("""{"left":3,"free":true}"""))
        server.enqueue(MockResponse().setBody("""{"jobId":"j1","status":"failed","error":{"code":"ai_unavailable","message":"Busy"}}"""))
        server.enqueue(MockResponse().setBody("""{"briefId":"br_1","left":4,"used":1,"packs":0}"""))
        try {
            drafts.regenerate(id, "features", "")
            fail("expected ai_unavailable")
        } catch (e: ApiException) {
            assertEquals(ApiException.AI_UNAVAILABLE, e.code)
        }
        assertEquals("the edit came back", AiEdits(4, true), drafts.get(id)?.aiEdits)
    }
}
