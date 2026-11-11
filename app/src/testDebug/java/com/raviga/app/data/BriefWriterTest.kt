package com.raviga.app.data

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.SectionIds
import com.raviga.app.data.demo.BriefWriter
import com.raviga.app.data.demo.DemoApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefWriterTest {

    private val salon = "I want an app for my salon in Delhi called GlowBook. Customers should be able to book appointments, pay online with UPI, and get reminders. There should be an admin dashboard for the staff."

    @Test
    fun `writes the five sections in order`() {
        val draft = BriefWriter.write(listOf(salon))
        assertEquals(SectionIds.ordered, draft.sections.map { it.id })
        assertTrue(draft.sections.all { it.body.isNotBlank() })
        assertEquals("GlowBook", draft.title)
    }

    @Test
    fun `detects features in plain words`() {
        val draft = BriefWriter.write(listOf(salon))
        val features = draft.sections.first { it.id == SectionIds.FEATURES }.body
        assertTrue(features.contains("Booking", ignoreCase = true))
        assertTrue(features.contains("Takes UPI and card payments"))
        assertTrue(features.contains("An admin screen"))
        assertTrue(draft.sections.first { it.id == SectionIds.SUMMARY }.body.startsWith("GlowBook is an iPhone and Android app"))
    }

    @Test
    fun `quote lands in a bracket and respects the timeline rule`() {
        val draft = BriefWriter.write(listOf(salon))
        val doc = Document(version = 1, title = draft.title, sections = draft.sections)
        val q = BriefWriter.quote(doc, DemoApi.demoConfig())
        // Contract v0.7 pricing (x0.38, at least 5); v0.7.1 timelines come from the bracket.
        assertTrue(q.credits >= 5)
        assertTrue(q.bracketId != null)
        val bracket = DemoApi.demoConfig().quote.brackets.first { it.id == q.bracketId }
        assertEquals(bracket.timeline, q.timeline)
        assertEquals(q.timeline.maxWeeks * 5, q.workingDays)
        assertEquals(q.credits, q.breakdown.sumOf { it.second })
    }

    @Test
    fun `regenerate shorter halves the bullets`() {
        val draft = BriefWriter.write(listOf(salon))
        val section = draft.sections.first { it.id == SectionIds.FEATURES }
        val shorter = BriefWriter.regenerate(section, listOf(salon), "make it shorter")
        assertTrue(shorter.body.lines().size < section.body.lines().size)
    }

    @Test
    fun `append only adds new bullets`() {
        val first = BriefWriter.write(listOf(salon))
        val doc = Document(version = 1, title = first.title, sections = first.sections)
        val merged = BriefWriter.append(doc, listOf(salon, "Also add a loyalty points and rewards feature."))
        val features = merged.first { it.id == SectionIds.FEATURES }.body
        assertTrue("one list: a later wish joins Features", features.contains("rewards", ignoreCase = true))
        val screens = merged.first { it.id == SectionIds.SCREENS }.body
        assertTrue(screens.contains("Welcome"))
    }

    @Test
    fun `enterprise runs 4 to at least 6 weeks, longer for very large projects`() {
        val config = DemoApi.demoConfig()
        assertEquals(null, config.quote.brackets.first { it.id == "enterprise" }.timeline)
        assertEquals(com.raviga.app.data.api.QuoteTimeline(1, 2), config.quote.brackets.first { it.id == "standard" }.timeline)
    }
}

/** R-113 item 1 (contract v0.12.1 and v0.13.0, as the server's drafter): the client provides nothing; fixes are free until 7 days after launch. */
class BriefWriterV013Test {
    private val salon = "I want an app for my salon in Delhi called GlowBook. Customers should be able to book appointments, pay online with UPI, and get reminders."

    @Test
    fun `no open questions, nothing asked of the client, no technical names`() {
        val draft = BriefWriter.write(listOf(salon))
        assertEquals(listOf("Summary", "Who it's for", "Features", "Screens", "What you get"), draft.sections.map { it.heading })
        // v0.14.0: plain words a shop owner understands; "What you get" keeps its backend line.
        val text = draft.sections.filter { it.id != SectionIds.DELIVERABLES }.joinToString("\n") { it.body }
        listOf(
            "?", "you provide", "provided by you", "Two rounds", "revision", "GitHub", "source code", "logo", "brand",
            "Firebase", "AWS", "Amazon", "API", "SDK", "OTP", "GDPR", "HTTPS", "database", "server", "Android 8", "iOS 15", "Stripe", "Twilio",
        ).forEach { assertTrue("no \"$it\"", !text.contains(it, ignoreCase = it.length > 4)) }
    }

    @Test
    fun `a website asked for is said plainly in the summary`() {
        val draft = BriefWriter.write(listOf("An app for my bakery, and a website for orders too."))
        assertTrue(draft.sections.first { it.id == SectionIds.SUMMARY }.body.endsWith("We build the iPhone and Android apps; the website isn't part of this."))
    }

    @Test
    fun `deliverables and assumptions end with the free-fix rule`() {
        val draft = BriefWriter.write(listOf(salon))
        val deliverables = draft.sections.first { it.id == SectionIds.DELIVERABLES }.body.lines()
        assertEquals("- Your app, published in your App Store and Google Play accounts", deliverables.first())
        assertEquals("- " + com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE, deliverables.last())
        val doc = com.raviga.app.data.api.Document(title = draft.title, sections = draft.sections)
        val assumptions = BriefWriter.quote(doc, com.raviga.app.data.demo.DemoApi.demoConfig()).assumptions
        assertEquals(com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE, assumptions.last())
        assertTrue(assumptions.contains("Third-party services (payments, SMS, maps) are billed to your own accounts."))
    }
}
