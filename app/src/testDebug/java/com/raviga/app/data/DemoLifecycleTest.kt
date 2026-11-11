package com.raviga.app.data

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.ChangeRequest
import com.raviga.app.data.api.ConsentRequest
import com.raviga.app.data.api.DocumentBody
import com.raviga.app.data.api.HandoverInvitedRequest
import com.raviga.app.data.api.PayRequest
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.QuoteRequest
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.data.api.ReportRequest
import com.raviga.app.data.api.SectionBody
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.demo.DemoApi
import com.raviga.app.data.local.CacheStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * R-113 (testing level Full: pay and the 7-day window): the demo backend from quote to live and round again, as
 * contract v0.13.0. The free-fix window starts at the first store published, never moves, takes a report from live
 * (which goes round the demo again) and refuses one after it with 409 free_fixes_ended.
 */
class DemoLifecycleTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test
    fun `pay, demo, launch, a free fix after launch, then the window ends`() = runBlocking {
        val api = DemoApi(CacheStore(kotlin.io.path.createTempDirectory("demo").toFile(), json), json)
        val legal = DemoApi.demoConfig().legal
        api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion, aiProcessing = true))

        // A native, no-backend brief; quoted and submitted.
        // v0.14.0: five plain sections; nothing here needs a backend (no sign in, payments or admin).
        val doc = DocumentBody("Salon", listOf(SectionBody("summary", "An iPhone and Android app that shows a salon's price list"), SectionBody("features", "- Shows the price list")))
        var job = api.createQuote(QuoteRequest(doc)).job
        while (job.status != "done") { delay(300); job = api.job(job.jobId) }
        val quote = json.decodeFromJsonElement(QuoteResult.serializer(), job.result!!)
        var p: Project = api.submitProject("sub-1", SubmitProjectRequest(document = doc, quoteToken = quote.quoteToken))
        api.demoAdvanceLatest()
        p = api.project(p.id)
        assertEquals(ProjectStatus.APPROVED, p.status)

        // Pay: short of credits first (402 with the numbers), then paid; a retry with the same key replays it.
        try {
            api.pay(p.id, "pay-1", PayRequest())
            fail("expected 402")
        } catch (e: ApiException) {
            assertEquals(ApiException.INSUFFICIENT_CREDITS, e.code)
            assertEquals(quote.quote.credits.toDouble(), e.detailDouble("required") ?: 0.0, 0.0)
        }
        api.demoAddCredits(quote.quote.credits + 5)
        p = api.pay(p.id, "pay-2", PayRequest())
        assertEquals(ProjectStatus.BUILDING, p.status)
        assertEquals("a retry with the same key gets the same answer", p.id, api.pay(p.id, "pay-2", PayRequest()).id)

        // The demo, then approval: fixes are free and the window hasn't started.
        api.demoAdvanceLatest()
        p = api.project(p.id)
        assertEquals(ProjectStatus.DELIVERED, p.status)
        assertNull(p.freeFixesUntil)
        p = api.accept(p.id)
        assertEquals(ProjectStatus.ACCEPTED, p.status)
        assertEquals("none", p.delivery?.handover?.backend)

        // Make it live: invites sent, both stores reviewed and published: live, and the 7 days start.
        p.delivery!!.handover!!.stores.keys.forEach { api.handoverInvited(p.id, HandoverInvitedRequest(it)) }
        repeat(3) { api.demoAdvanceLatest() }
        p = api.project(p.id)
        assertEquals(ProjectStatus.LIVE, p.status)
        val until = Instant.parse(assertNotNull(p.freeFixesUntil).let { p.freeFixesUntil!! })
        val days = Duration.between(Instant.now(), until).toHours()
        assertTrue("about 7 days after launch: $days h", days in 167..168)

        // A bug reported after launch, inside the window: fixed, a new demo, approved again, live again; the date stays.
        p = api.report(p.id, ReportRequest("bug", "The booking button does nothing."))
        assertEquals(ProjectStatus.REVISION_REQUESTED, p.status)
        api.demoAdvanceLatest()
        p = api.accept(api.project(p.id).also { assertEquals(ProjectStatus.DELIVERED, it.status) }.id)
        assertEquals(ProjectStatus.ACCEPTED, p.status)
        api.demoAdvanceLatest()
        p = api.project(p.id)
        assertEquals(ProjectStatus.LIVE, p.status)
        assertEquals("the window never moves", until.toString(), p.freeFixesUntil)

        // After the 7 days: a report is refused with the server's words; a change can still be asked.
        assertEquals(1, api.demoEndFreeFixes())
        try {
            api.report(p.id, ReportRequest("bug", "Still broken."))
            fail("expected 409 free_fixes_ended")
        } catch (e: ApiException) {
            assertEquals(ApiException.FREE_FIXES_ENDED, e.code)
            assertEquals(409, e.httpStatus)
            assertTrue(e.message.orEmpty().contains("Request a change"))
            assertEquals(ProjectStatus.LIVE, e.detailString("status"))
        }
        val change = api.requestChange(p.id, ChangeRequest("Add a loyalty card")).change
        assertEquals("pricing", change.status)
    }
}
