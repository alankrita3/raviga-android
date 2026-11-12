package com.raviga.app.data

import com.raviga.app.data.api.AiAppendRequest
import com.raviga.app.data.api.AiDocumentResult
import com.raviga.app.data.api.AiDraftRequest
import com.raviga.app.data.api.AiEdits
import com.raviga.app.data.api.AiInput
import com.raviga.app.data.api.AiRegenerateRequest
import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.ConsentRequest
import com.raviga.app.data.api.DocumentBody
import com.raviga.app.data.api.Empty
import com.raviga.app.data.api.JobEnvelope
import com.raviga.app.data.api.SectionBody
import com.raviga.app.data.demo.DemoApi
import com.raviga.app.data.local.CacheStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R-124 (contract v0.16.0, testing level Full for the money part): the demo backend counts AI edits as the server
 * does. A draft gets an id and 5 free edits; the sixth asks for a pack and charges nothing; buying needs the flag and a
 * twelfth of a credit; POST /briefs gives an id with none free.
 */
class DemoAiEditsTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private suspend fun api(): DemoApi {
        val api = DemoApi(CacheStore(kotlin.io.path.createTempDirectory("demo").toFile(), json), json)
        val legal = DemoApi.demoConfig().legal
        api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion, aiProcessing = true))
        return api
    }

    private val doc = DocumentBody("Salon", listOf(SectionBody("summary", "An app that shows a salon's price list"), SectionBody("features", "- Shows the price list")))

    private suspend fun DemoApi.finish(envelope: JobEnvelope): AiDocumentResult {
        var job = envelope.job
        while (job.status != "done" && job.status != "failed") { delay(200); job = job(job.jobId) }
        assertEquals(job.error?.message, "done", job.status)
        return json.decodeFromJsonElement(AiDocumentResult.serializer(), job.result!!)
    }

    private fun DemoApi.regenerate(briefId: String?, buy: Boolean? = null) =
        suspend { aiRegenerate(AiRegenerateRequest(briefId ?: "", doc, "features", "shorter", buyEditPack = buy)) }

    @Test
    fun `five free edits, then a pack only with the flag and a twelfth of a credit`() = runBlocking {
        val api = api()
        val draft = api.aiDraft(AiDraftRequest(listOf(AiInput("text", "An app for my salon to show the price list."))))
        val briefId = draft.briefId
        assertNotNull("the 202 names the brief", briefId)
        assertEquals(AiEdits(5, true), draft.aiEdits)
        val written = api.finish(draft)
        assertEquals("and so does the result", briefId, written.briefId)

        // Five free: Regenerate and Add more each use one; the 202 says what's left.
        for (left in 4 downTo 1) assertEquals(AiEdits(left, true), api.regenerate(briefId)().aiEdits)
        val fifth = api.aiAppend(AiAppendRequest(briefId!!, doc, listOf(AiInput("text", "Also a loyalty card for regulars."))))
        assertEquals(AiEdits(0, true), fifth.aiEdits)
        assertEquals(AiEdits(0, true), api.finish(fifth).aiEdits)

        // The sixth without the flag: 402, nothing run, nothing charged.
        val before = api.credits(null, 50)
        try {
            api.regenerate(briefId)()
            fail("expected 402 ai_edit_pack_required")
        } catch (e: ApiException) {
            assertEquals(ApiException.AI_EDIT_PACK_REQUIRED, e.code)
            assertEquals("You've used this brief's 5 free AI edits. 5 more cost a twelfth of a credit.", e.message)
            assertEquals(5.0, e.detailDouble("packSize") ?: 0.0, 0.0)
            assertEquals(0.0833, e.detailDouble("packPriceCredits") ?: 0.0, 0.0)
        }
        // With the flag but short of the twelfth (the free brief cost nothing): 402 insufficient_credits, its words.
        try {
            api.regenerate(briefId, buy = true)()
            fail("expected 402 insufficient_credits")
        } catch (e: ApiException) {
            assertEquals(ApiException.INSUFFICIENT_CREDITS, e.code)
            assertEquals("You need a twelfth of a credit for 5 more AI edits. Buy credits to continue.", e.message)
        }
        assertEquals("nothing was booked", before.ledger, api.credits(null, 50).ledger)

        // Credits, then the pack: one ai_edit_pack line of a twelfth, 5 more edits with this call's already taken.
        api.demoPurchase("credits_quarter")
        val bought = api.regenerate(briefId, buy = true)()
        assertEquals(AiEdits(4, false), bought.aiEdits)
        val line = api.credits(null, 50).ledger.first()
        assertEquals("ai_edit_pack", line.type)
        assertEquals(-0.0833, line.credits, 1e-9)
        assertEquals(AiEdits(3, false), api.regenerate(briefId)().aiEdits)
        val count = api.aiEdits(briefId)
        assertEquals(listOf(3, 7, 1), listOf(count.left, count.used, count.packs))
        assertEquals(AiEdits(3, false), count.edits(5))
    }

    @Test
    fun `an old draft's id has no free edits, and an edit needs its id`() = runBlocking {
        val api = api()
        val registered = api.registerBrief(Empty())
        assertTrue(registered.briefId.startsWith("br_"))
        assertEquals(AiEdits(0, false), registered.aiEdits)
        try {
            api.regenerate(registered.briefId)()
            fail("expected 402")
        } catch (e: ApiException) {
            assertEquals(ApiException.AI_EDIT_PACK_REQUIRED, e.code)
            assertEquals("This brief has no free AI edits. 5 AI edits cost a twelfth of a credit.", e.message)
        }
        for ((id, code) in listOf(null to ApiException.BRIEF_ID_REQUIRED, "br_unknown" to ApiException.BRIEF_NOT_FOUND)) {
            try {
                api.regenerate(id)()
                fail("expected $code")
            } catch (e: ApiException) {
                assertEquals(code, e.code)
            }
        }
        try {
            api.aiEdits("br_unknown")
            fail("expected 404")
        } catch (e: ApiException) {
            assertEquals(ApiException.BRIEF_NOT_FOUND, e.code)
        }
    }
}
