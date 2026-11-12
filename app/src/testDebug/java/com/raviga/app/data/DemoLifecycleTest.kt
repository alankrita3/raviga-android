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
import com.raviga.app.data.api.SectionIds
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
        // v0.15.0: three plain sections; nothing here needs a backend (no sign in, payments or owner tools).
        val doc = DocumentBody("Salon", listOf(SectionBody("summary", "An iPhone and Android app that shows a salon's price list"), SectionBody("features", "- Shows the price list")))
        var job = api.createQuote(QuoteRequest(doc)).job
        while (job.status != "done") { delay(300); job = api.job(job.jobId) }
        val quote = json.decodeFromJsonElement(QuoteResult.serializer(), job.result!!)
        // v0.15.0 (R-118): one price for the client; the breakdown and drivers stay with the team.
        assertTrue(quote.quote.breakdown.isEmpty())
        assertTrue(quote.quote.complexity?.drivers.orEmpty().isEmpty())
        assertEquals(com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE, quote.quote.assumptions.last())
        var p: Project = api.submitProject("sub-1", SubmitProjectRequest(document = doc, quoteToken = quote.quoteToken))
        assertEquals("v0.15.0: no backend until the builder says", false, p.hasBackend)
        assertEquals(SectionIds.ordered, p.document?.sections?.map { it.id })
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
        // v0.15.0 (R-119): approval deletes nothing.
        assertNull(p.contentDeletedAt)
        assertEquals("Salon", p.title)
        assertNotNull(p.document)

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
        assertNotNull("live keeps the brief", api.project(p.id).document)
    }

    /** v0.15.0 (R-118): the builder says at the first demo whether there's a backend; Make it live follows handover.backend. */
    @Test
    fun `a backend is declared at the demo, then Make it live asks for AWS`() = runBlocking {
        val api = DemoApi(CacheStore(kotlin.io.path.createTempDirectory("demo").toFile(), json), json)
        val legal = DemoApi.demoConfig().legal
        api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion, aiProcessing = true))
        val doc = DocumentBody("Salon", listOf(
            SectionBody("summary", "A pair of native iPhone and Android apps for a salon"),
            SectionBody("features", "- Sign in with Apple or Google\n- Book an appointment"),
            // An older build's sections are dropped, as the server does.
            SectionBody("screens", "- Home"), SectionBody("deliverables", "- Your app"),
        ))
        var job = api.createQuote(QuoteRequest(doc)).job
        while (job.status != "done") { delay(300); job = api.job(job.jobId) }
        val quote = json.decodeFromJsonElement(QuoteResult.serializer(), job.result!!)
        var p: Project = api.submitProject("sub-2", SubmitProjectRequest(document = doc, quoteToken = quote.quoteToken))
        assertEquals(SectionIds.ordered, p.document?.sections?.map { it.id })
        assertEquals(false, p.hasBackend)
        api.demoAdvanceLatest()
        api.demoAddCredits(quote.quote.credits)
        p = api.pay(p.id, "pay-b", PayRequest())
        assertEquals(false, p.hasBackend)
        api.demoAdvanceLatest()
        p = api.project(p.id)
        assertEquals(ProjectStatus.DELIVERED, p.status)
        assertTrue("the builder said yes at the demo", p.hasBackend)
        p = api.accept(p.id)
        assertEquals("pending", p.delivery?.handover?.backend)
        assertTrue(com.raviga.app.ui.status.GoLiveLogic.hasBackendStep(p.delivery?.handover))
    }

    /** v0.15.0 (R-119): cancel still deletes the brief at once. */
    @Test
    fun `cancel still deletes the brief`() = runBlocking {
        val api = DemoApi(CacheStore(kotlin.io.path.createTempDirectory("demo").toFile(), json), json)
        val legal = DemoApi.demoConfig().legal
        api.putConsent(ConsentRequest(termsVersion = legal.termsVersion, privacyVersion = legal.privacyVersion, aiProcessing = true))
        val doc = DocumentBody("Bakery", listOf(SectionBody("summary", "A native Android app for a bakery's menu"), SectionBody("features", "- Shows the menu")))
        var job = api.createQuote(QuoteRequest(doc)).job
        while (job.status != "done") { delay(300); job = api.job(job.jobId) }
        val quote = json.decodeFromJsonElement(QuoteResult.serializer(), job.result!!)
        val p = api.submitProject("sub-3", SubmitProjectRequest(document = doc, quoteToken = quote.quoteToken))
        val cancelled = api.cancelProject(p.id, com.raviga.app.data.api.Empty())
        assertEquals(ProjectStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.document)
        assertNotNull(cancelled.contentDeletedAt)
    }
}
