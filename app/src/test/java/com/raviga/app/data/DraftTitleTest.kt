package com.raviga.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.raviga.app.data.api.JobRunner
import com.raviga.app.data.api.RavigaApi
import com.raviga.app.data.drafts.DraftStore
import com.raviga.app.data.drafts.DraftTitle
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.Sealer
import com.raviga.app.data.local.CacheStore
import com.raviga.app.data.repo.AiRepository
import com.raviga.app.data.repo.DraftRepository
import com.raviga.app.data.repo.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.nio.file.Files

/**
 * R-082: drafts are named from their first note on the phone (iOS's DraftTitleTests, case for case; the on-device model
 * is stubbed), never over the client's own name, and the AI never gets the app's name as if it were the client's.
 */
class DraftTitleTest {

    @Test fun fallbackTakesTheFirstMeaningfulWords() {
        val cases = listOf(
            "I want a booking app for my bakery customers. They pay at pickup." to "Booking app for my bakery customers",
            "i want to booking app for my bakery customers pay when they pick up" to "Booking app for my bakery customers",
            "Please build me a tiffin delivery service for office workers" to "Tiffin delivery service for office workers",
            "A gym app for members.\nMembers book classes and pay monthly." to "Gym app for members",
            "We need an app for Smile Point Dental in Pune" to "App for Smile Point Dental",
            "So basically I'd like to have a UPI wallet for students" to "Have a UPI wallet for students",
            "I want a simple app I want to simple app that tracks water" to "Simple app",
            "app" to "App",
            "   \n  " to null,
            "12345 !!!" to null,
        )
        for ((note, title) in cases) assertEquals(note, title, DraftTitle.fallback(note))
    }

    @Test fun modelRepliesAreMadeToTheSameRules() {
        assertEquals("Bakery Pickup Orders", DraftTitle.tidy("\"Bakery Pickup Orders.\""))
        assertEquals("Clinic booking app", DraftTitle.tidy("clinic booking app\nThis title fits."))
        assertNull("a sentence, not a title", DraftTitle.tidy("A very long title with far too many words in it to be one"))
        assertNull(DraftTitle.tidy("  ...  "))
    }

    @Test fun namesKeepTheirMarksAndStayShort() {
        assertEquals("E-commerce site for B&B owners", DraftTitle.fallback("I need an e-commerce site for B&B owners. Quickly!"))
        assertEquals("O'Brien's café app", DraftTitle.fallback("“O'Brien's café app”"))
        val long = DraftTitle.fallback("Supercalifragilisticexpialidocious extraordinarily comprehensive internationalization localization platform")
        assertEquals(true, long != null && long.length <= DraftTitle.MAX_CHARACTERS)
    }

    // ----- The draft repository -----

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown(); scope.cancel() }

    private fun repo(model: (suspend (String) -> String?)? = null): DraftRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RavigaApi::class.java)
        val store = DraftStore(Files.createTempDirectory("drafts").toFile(), json, Sealer.None)
        val projects = ProjectRepository(api, json, unusedCache())
        return DraftRepository(store, AiRepository(api, json, JobRunner(api, json)), projects, titleModel = model, scope = scope, titleDelayMs = 20)
    }

    /** Submitting isn't tested here, so the projects' cache is never touched: a placeholder without a Context. */
    private fun unusedCache(): CacheStore {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, CacheStore::class.java) as CacheStore
    }

    private fun aiReturns(title: String) = server.enqueue(
        MockResponse().setResponseCode(202).setBody(
            """{"job":{"jobId":"j1","type":"ai_draft","status":"done","result":{"document":{"title":"$title",
            "sections":[{"id":"summary","heading":"Summary","body":"A bakery app."}]}}}}""",
        ),
    )

    @Test fun theFirstNoteNamesTheDraftUntilTheClientRenamesIt() = runBlocking {
        val drafts = repo()
        drafts.load()
        val draft = drafts.create()
        val note = drafts.addInput(draft.id, "text", "I want a booking app for my bakery customers")
        assertEquals("named at once", "Booking app for my bakery customers", drafts.get(draft.id)?.title)
        assertEquals("the AI isn't told the app's name", "", drafts.get(draft.id)?.titleForTheAi)

        drafts.editInput(draft.id, note.id, "I want a pickup app for my cake shop")
        assertEquals("follows the first note", "Pickup app for my cake shop", drafts.get(draft.id)?.title)
        drafts.addInput(draft.id, "text", "Second note about payments")
        assertEquals("only the first note names it", "Pickup app for my cake shop", drafts.get(draft.id)?.title)

        drafts.rename(draft.id, "Sweet Treats")
        drafts.editInput(draft.id, note.id, "I want something else")
        assertEquals("the client's name stays", "Sweet Treats", drafts.get(draft.id)?.title)
        assertEquals("Sweet Treats", drafts.get(draft.id)?.titleForTheAi)

        drafts.rename(draft.id, "  ")
        assertEquals("cleared: the first note names it again", "Something else", drafts.get(draft.id)?.title)
        assertEquals(LocalDraft.TITLE_AUTO, drafts.get(draft.id)?.titleSource)
    }

    @Test fun theBriefsTitleReplacesTheAppsNameAndTheAiIsNotToldIt() = runBlocking {
        val drafts = repo()
        drafts.load()
        val draft = drafts.create()
        drafts.addInput(draft.id, "text", "I want a booking app for my bakery customers")
        aiReturns("Bakery Booking")
        val written = drafts.writeBrief(draft.id)
        val sent = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse("no title for an app-made name", "title" in sent)
        assertEquals("Bakery Booking", written.displayTitle)
        assertNull(written.titleSource)
    }

    @Test fun theClientsOwnNameSurvivesTheFirstBrief() = runBlocking {
        val drafts = repo()
        drafts.load()
        val draft = drafts.create()
        drafts.addInput(draft.id, "text", "I want a booking app for my bakery customers")
        drafts.rename(draft.id, "Sweet Treats")
        aiReturns("Bakery Booking")
        val written = drafts.writeBrief(draft.id)
        val sent = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("\"Sweet Treats\"", sent["title"].toString())
        assertEquals("Sweet Treats", written.displayTitle)
    }

    @Test fun theOnDeviceModelNamesItWhenThePhoneHasOne() = runBlocking {
        val asked = mutableListOf<String>()
        val drafts = repo { note -> asked += note; "Bakery pickup orders" }
        drafts.load()
        val draft = drafts.create()
        val note = drafts.addInput(draft.id, "text", "I want a booking app")
        drafts.editInput(draft.id, note.id, "I want a booking app for my bakery customers")
        assertEquals("the fallback first, at once", "Booking app for my bakery customers", drafts.get(draft.id)?.title)
        repeat(100) { if (drafts.get(draft.id)?.title != "Bakery pickup orders") delay(20) }
        assertEquals("Bakery pickup orders", drafts.get(draft.id)?.title)
        assertEquals("asked once, for the note as it settled", listOf("I want a booking app for my bakery customers"), asked)
        assertEquals(LocalDraft.TITLE_AUTO, drafts.get(draft.id)?.titleSource)
    }

    @Test fun theModelNeverRenamesWhatTheClientNamed() = runBlocking {
        val drafts = repo { delay(100); "Bakery pickup orders" }
        drafts.load()
        val draft = drafts.create()
        drafts.addInput(draft.id, "text", "I want a booking app for my bakery customers")
        drafts.rename(draft.id, "Sweet Treats")
        delay(400)
        assertEquals("Sweet Treats", drafts.get(draft.id)?.title)
    }
}
