package com.raviga.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.PendingJob
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.data.local.CacheStore
import com.raviga.app.data.repo.AiRepository
import com.raviga.app.data.repo.DraftRepository
import com.raviga.app.data.repo.ProjectRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * R-083 (as iOS's JobResumeTests): an AI job's id is kept on the draft while it runs, kept when the call is cancelled
 * or the app closes, collected on the next open, and given up past 20 minutes or when the server no longer has it.
 */
class JobResumeTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repo(): DraftRepository {
        val api = Retrofit()
        val store = DraftStore(Files.createTempDirectory("drafts").toFile(), json, Sealer.None)
        return DraftRepository(store, AiRepository(api, json, JobRunner(api, json)), ProjectRepository(api, json, unusedCache()))
    }

    @Suppress("FunctionName")
    private fun Retrofit(): RavigaApi = retrofit2.Retrofit.Builder()
        .baseUrl(server.url("/v1/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RavigaApi::class.java)

    /** Submitting isn't tested here, so the projects' cache is never touched: a placeholder without a Context. */
    private fun unusedCache(): CacheStore {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CacheStore::class.java) as CacheStore
    }

    private val document = """{"document":{"title":"Dog Walking Booking App","sections":[{"id":"summary","heading":"Summary","body":"Owners book walkers."}]}}"""
    private fun started(id: String = "j1") = MockResponse().setResponseCode(202).setBody("""{"job":{"jobId":"$id","type":"ai_draft","status":"queued"}}""")
    private fun done(id: String = "j1", result: String = document) = MockResponse().setBody("""{"jobId":"$id","status":"done","progress":1,"result":$result}""")

    private suspend fun draftWithNote(drafts: DraftRepository): String {
        drafts.load()
        val draft = drafts.create()
        drafts.addInput(draft.id, "text", "A booking app for a dog walking service. Owners book a walker and pay by card.")
        return draft.id
    }

    @Test fun aCallClosedMidwayKeepsItsJobAndTheNextOpenCollectsIt() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        server.enqueue(started())
        // No poll answer yet: the call waits 2 s before its first poll, and the screen closes during that wait.
        val call = launch { runCatching { drafts.writeBrief(id) } }
        repeat(50) { if (drafts.get(id)?.pendingJob == null) delay(50) }
        assertEquals("kept from the moment the server accepted it", "j1", drafts.get(id)?.pendingJob?.jobId)
        call.cancel()
        call.join()
        val pending = drafts.get(id)?.pendingJob
        assertNotNull("a cancelled call keeps it", pending)
        assertEquals(PendingJob.DRAFT, pending?.kind)

        server.enqueue(done())
        val landed = drafts.resumePending(id)
        assertEquals("Dog Walking Booking App", landed?.displayTitle)
        assertNull("cleared once collected", drafts.get(id)?.pendingJob)
        assertEquals("the notes count as in the brief", drafts.get(id)?.inputs?.map { it.id }, drafts.get(id)?.inputsInBrief)
    }

    /**
     * R-130 (live on dev): Add more from an open brief ran its job in Capture while the brief screen underneath saw it
     * on the draft and collected it too, so it landed twice. While this app waits on a job it says so, and only then.
     */
    @Test fun aJobThisAppIsWaitingOnIsMarkedRunningUntilItLands() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        assertFalse(drafts.isRunning(id))
        server.enqueue(started())
        server.enqueue(done())
        val call = launch { drafts.writeBrief(id) }
        repeat(50) { if (drafts.get(id)?.pendingJob == null) delay(20) }
        assertNotNull("the job is on the draft, where another screen sees it", drafts.get(id)?.pendingJob)
        assertTrue("and this app is already waiting on it", drafts.isRunning(id))
        call.join()
        assertFalse(drafts.isRunning(id))
        assertNull(drafts.get(id)?.pendingJob)
        assertEquals("landed once", 1, drafts.get(id)?.versions?.size)

        // A call cancelled midway (the screen closed) isn't running any more: the next open collects it.
        val other = draftWithNote(drafts)
        server.enqueue(started("j2"))
        val closed = launch { runCatching { drafts.writeBrief(other) } }
        repeat(50) { if (drafts.get(other)?.pendingJob == null) delay(20) }
        closed.cancel()
        closed.join()
        assertNotNull(drafts.get(other)?.pendingJob)
        assertFalse(drafts.isRunning(other))
    }

    @Test fun aFinishedCallLeavesNothingPending() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        server.enqueue(started())
        server.enqueue(done())
        drafts.writeBrief(id)
        assertNull(drafts.get(id)?.pendingJob)
    }

    @Test fun pastTwentyMinutesItStoppedWhenTheAppClosed() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        server.enqueue(started())
        val call = launch { runCatching { drafts.writeBrief(id) } }
        repeat(50) { if (drafts.get(id)?.pendingJob == null) delay(50) }
        call.cancel()
        call.join()
        try {
            drafts.resumePending(id, now = System.currentTimeMillis() + PendingJob.WINDOW_MS + 1_000)
            fail("expected job_lost")
        } catch (e: ApiException) {
            assertEquals(ApiException.JOB_LOST, e.code)
        }
        assertNull(drafts.get(id)?.pendingJob)
    }

    @Test fun aJobTheServerNoLongerHasStoppedToo() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        server.enqueue(started())
        val call = launch { runCatching { drafts.writeBrief(id) } }
        repeat(50) { if (drafts.get(id)?.pendingJob == null) delay(50) }
        call.cancel()
        call.join()
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":"not_found","message":"No such job"}}"""))
        try {
            drafts.resumePending(id)
            fail("expected job_lost")
        } catch (e: ApiException) {
            assertEquals(ApiException.JOB_LOST, e.code)
            assertEquals("That stopped when the app closed. Try again.", com.raviga.app.ui.status.StatusCopy.errorLine(e.code, e.message))
        }
        assertNull(drafts.get(id)?.pendingJob)
    }

    @Test fun writingTheBriefAgainCollectsThePendingJobInsteadOfPayingTwice() = runBlocking {
        val drafts = repo()
        val id = draftWithNote(drafts)
        server.enqueue(started())
        val call = launch { runCatching { drafts.writeBrief(id) } }
        repeat(50) { if (drafts.get(id)?.pendingJob == null) delay(50) }
        call.cancel()
        call.join()
        repeat(server.requestCount) { server.takeRequest(1, TimeUnit.SECONDS) }   // the first call's requests
        val before = server.requestCount
        server.enqueue(done())
        drafts.writeBrief(id)
        val paths = (before until server.requestCount).map { server.takeRequest(1, TimeUnit.SECONDS) }
        assertTrue("no second POST", paths.none { it == null || it.method == "POST" && it.path?.contains("/ai/draft") == true })
        assertEquals("Dog Walking Booking App", drafts.get(id)?.displayTitle)
    }
}
