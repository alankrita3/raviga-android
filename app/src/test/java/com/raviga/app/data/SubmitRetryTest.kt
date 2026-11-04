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
import com.raviga.app.ui.quote.QuoteViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * R-094 P2-6 (iOS review 2): a submit whose answer was lost is retried with the exact body it sent (a GitHub name
 * changed since, or an edit, doesn't apply), and past the quote's local expiry; the server decides.
 */
class SubmitRetryTest {
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

    private fun fixture(name: String): String = File(javaClass.classLoader!!.getResource("fixtures/$name")!!.toURI()).readText()

    private fun doc(v: Int, body: String) = Document(version = v, title = "Bakery site", sections = listOf(Section("summary", "Summary", body)))

    private suspend fun setUpDraft(): Pair<DraftRepository, DraftStore> {
        val api = retrofit2.Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java)
        val store = DraftStore(Files.createTempDirectory("drafts").toFile(), json, Sealer.None)
        val drafts = DraftRepository(store, AiRepository(api, json, JobRunner(api, json)), ProjectRepository(api, json, cache()))
        drafts.load()
        store.create(
            LocalDraft(
                id = "ld_1", title = "Bakery site", createdAt = "a", updatedAt = "a",
                versions = listOf(LocalVersion(1, doc(1, "A one-page site."), "a", "draft")),
                quote = Quote(id = "qt_1", documentVersion = 1, credits = 7, expiresAt = "2026-11-03T00:00:00Z"),
                quoteToken = "tok", quotedVersion = 1,
            ),
        )
        return drafts to store
    }

    @Test fun aLostAnswerIsRetriedWithTheBodyItSent() = runBlocking {
        val (drafts, store) = setUpDraft()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            drafts.submit("ld_1", "111111111111", "key-1")
            fail("expected the connection to drop")
        } catch (e: ApiException) {
            assertEquals(ApiException.NETWORK, e.code)
        }
        server.takeRequest(1, TimeUnit.SECONDS)   // dropped before its body was read
        val held = drafts.get("ld_1")?.sentSubmit?.submit
        assertNotNull("held", held)
        val first = json.encodeToString(com.raviga.app.data.api.SubmitProjectRequest.serializer(), held!!)
        // Meanwhile: a new GitHub name and an edit to the brief.
        store.update("ld_1") { it.copy(versions = it.versions + LocalVersion(2, doc(2, "Edited after."), "b", "edit")) }

        server.enqueue(MockResponse().setResponseCode(201).setBody(fixture("project-submitted.json")))
        drafts.submit("ld_1", "222222222222", "key-1")
        val retry = server.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("the same bytes as the first send", first, retry?.body?.readUtf8())
        assertEquals("key-1", retry?.getHeader("Idempotency-Key"))
        assertTrue(first.contains("111111111111") && !first.contains("Edited after"))

        val after = drafts.get("ld_1")
        assertNull("answer seen: nothing held", after?.sentSubmit)
        assertEquals("the version that was sent", 1, after?.submittedVersion)
    }

    @Test fun anExpiredQuoteWithASendOutIsRetriedNotRequoted() = runBlocking {
        val (drafts, _) = setUpDraft()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        runCatching { drafts.submit("ld_1", "111111111111", "key-1") }
        val held = drafts.get("ld_1")
        val afterExpiry = System.currentTimeMillis()   // the quote expired at 00:00 today
        assertFalse("held: Submit stays, no new quote", QuoteViewModel.State(draft = held, clock = afterExpiry).needsNewQuote)
        assertTrue("nothing held: the expiry asks for a new quote", QuoteViewModel.State(draft = held?.copy(sentSubmit = null), clock = afterExpiry).needsNewQuote)
    }

    /**
     * Second review 2026-11-03 (Android): consent (check 1) comes before the used-token check, so a 403 says nothing
     * about the first send. The hold stays, the expired quote still isn't re-quoted, and the retry is byte for byte.
     */
    @Test fun aRefusalBeforeTheUsedTokenCheckKeepsTheHold() = runBlocking {
        val (drafts, _) = setUpDraft()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        runCatching { drafts.submit("ld_1", "111111111111", "key-1") }
        server.takeRequest(1, TimeUnit.SECONDS)
        val first = json.encodeToString(com.raviga.app.data.api.SubmitProjectRequest.serializer(), drafts.get("ld_1")?.sentSubmit?.submit!!)

        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"code":"consent_required","message":"Please accept the current terms","details":{"missing":["terms"]}}}"""),
        )
        try {
            drafts.submit("ld_1", "222222222222", "key-1")
            fail("expected consent_required")
        } catch (e: ApiException) {
            assertEquals(ApiException.CONSENT_REQUIRED, e.code)
        }
        server.takeRequest(1, TimeUnit.SECONDS)
        val held = drafts.get("ld_1")
        assertNotNull("answered before check 1a: still held", held?.sentSubmit)
        assertFalse("no new quote offered", QuoteViewModel.State(draft = held, clock = System.currentTimeMillis()).needsNewQuote)

        server.enqueue(MockResponse().setResponseCode(201).setBody(fixture("project-submitted.json")))
        drafts.submit("ld_1", "222222222222", "key-1")
        assertEquals("after agreeing: the same bytes", first, server.takeRequest(1, TimeUnit.SECONDS)?.body?.readUtf8())
    }

    @Test fun onlyAnswersAfterTheUsedTokenCheckLetTheHoldGo() {
        fun e(code: String, status: Int, projectId: String? = null) = ApiException(
            code, status,
            details = projectId?.let { kotlinx.serialization.json.buildJsonObject { put("projectId", kotlinx.serialization.json.JsonPrimitive(it)) } },
        )
        val before = listOf(
            e(ApiException.CONSENT_REQUIRED, 403), e(ApiException.ACCOUNT_RESTRICTED, 403), e(ApiException.PERMISSION_DENIED, 403),
            e(ApiException.UNAUTHENTICATED, 401), e(ApiException.UPGRADE_REQUIRED, 426), e(ApiException.RATE_LIMITED, 429),
            e(ApiException.CONFLICT, 409), e(ApiException.NOT_FOUND, 404), e(ApiException.QUOTE_STALE, 409, projectId = "pr_1"),
            e(ApiException.NETWORK, 0), e(ApiException.INTERNAL, 500), e(ApiException.AI_UNAVAILABLE, 503),
        )
        before.forEach { assertFalse("${it.code} ${it.httpStatus} keeps the hold", com.raviga.app.data.drafts.SentSubmit.refusedForGood(it)) }
        val after = listOf(
            e(ApiException.INSUFFICIENT_CREDITS, 402), e(ApiException.INVALID_ARGUMENT, 400),
            e(ApiException.CONTENT_REJECTED, 422), e(ApiException.QUOTE_STALE, 409), e(ApiException.INVALID_STATE, 409),
        )
        after.forEach { assertTrue("${it.code} ${it.httpStatus} lets it go", com.raviga.app.data.drafts.SentSubmit.refusedForGood(it)) }
    }

    @Test fun aRefusalTheServerAnsweredLetsTheBodyChange() = runBlocking {
        val (drafts, _) = setUpDraft()
        server.enqueue(MockResponse().setResponseCode(402).setBody("""{"error":{"code":"insufficient_credits","message":"Not enough credits."}}"""))
        runCatching { drafts.submit("ld_1", "111111111111", "key-1") }
        assertNull("the server didn't take it: nothing held", drafts.get("ld_1")?.sentSubmit)
        server.takeRequest(1, TimeUnit.SECONDS)
        server.enqueue(MockResponse().setResponseCode(201).setBody(fixture("project-submitted.json")))
        drafts.submit("ld_1", "222222222222", "key-1")
        assertTrue(server.takeRequest(1, TimeUnit.SECONDS)?.body?.readUtf8().orEmpty().contains("222222222222"))
    }

    /** R-098 P3-4: while a send is held, no note, section, title, version or quote change is taken. */
    @Test fun whileHeldEditsAreRefused() = runBlocking {
        val (drafts, _) = setUpDraft()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        runCatching { drafts.submit("ld_1", "111111111111", "key-1") }
        val tries = listOf<suspend () -> Unit>(
            { drafts.rename("ld_1", "New name") },
            { drafts.addInput("ld_1", "text", "One more thing about the bakery app.") },
            { drafts.saveEdit("ld_1", null, listOf(com.raviga.app.data.api.SectionBody("summary", "Edited."))) },
            { drafts.restore("ld_1", 1) },
            { drafts.quote("ld_1") },
        )
        for (attempt in tries) {
            try {
                attempt()
                fail("expected the held brief to refuse it")
            } catch (e: ApiException) {
                assertEquals("Editing opens again once your Submit is confirmed.", e.message)
            }
        }
        assertEquals("nothing changed", 1, drafts.get("ld_1")?.versions?.size)
    }
}
