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
    fun `writes the three sections in order`() {
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
        // v0.15.0: a salon's services are paid in person; what the owner does is a feature, never an admin panel.
        assertTrue(features.contains("Customers pay in person"))
        assertTrue(features.contains("A place for the owner to manage"))
        assertTrue(!features.contains("UPI") && !features.contains("admin", ignoreCase = true) && !features.contains("dashboard", ignoreCase = true))
        assertTrue(draft.sections.first { it.id == SectionIds.SUMMARY }.body.startsWith("GlowBook is a pair of native iPhone and Android apps"))
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
        // The team's own split (DemoApi sends the client none, v0.15.0).
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
        assertEquals(SectionIds.ordered, merged.map { it.id })
    }

    @Test
    fun `enterprise runs 4 to at least 6 weeks, longer for very large projects`() {
        val config = DemoApi.demoConfig()
        assertEquals(null, config.quote.brackets.first { it.id == "enterprise" }.timeline)
        assertEquals(com.raviga.app.data.api.QuoteTimeline(1, 2), config.quote.brackets.first { it.id == "standard" }.timeline)
    }
}

/**
 * R-113 item 1 (contract v0.12.1 and v0.13.0, as the server's drafter): the client provides nothing. R-121 (v0.15.0,
 * R-118): three sections that decide nothing technical, no section lists what the client gets.
 */
class BriefWriterV013Test {
    private val salon = "I want an app for my salon in Delhi called GlowBook. Customers should be able to book appointments, pay online with UPI, and get reminders."

    @Test
    fun `no open questions, nothing asked of the client, no technical names`() {
        val draft = BriefWriter.write(listOf(salon))
        assertEquals(listOf("Summary", "Who it's for", "Features"), draft.sections.map { it.heading })
        // v0.14.0: plain words a shop owner understands; v0.15.0: nothing technical decided, no admin panel.
        val text = draft.sections.joinToString("\n") { it.body }
        listOf(
            "?", "you provide", "provided by you", "Two rounds", "revision", "GitHub", "source code", "logo", "brand",
            "Firebase", "AWS", "Amazon", "API", "SDK", "OTP", "GDPR", "HTTPS", "database", "server", "Android 8", "iOS 15", "Stripe", "Twilio",
            "backend", "admin", "dashboard", "UPI", "one-time code", "Screens", "What you get",
        ).forEach { assertTrue("no \"$it\"", !text.contains(it, ignoreCase = it.length > 4)) }
    }

    @Test
    fun `what isn't offered is said plainly in the summary`() {
        val draft = BriefWriter.write(listOf("An app for my bakery, and a website for orders too."))
        assertTrue(draft.sections.first { it.id == SectionIds.SUMMARY }.body.endsWith("We build native iPhone and Android apps; the website isn't part of this."))
        // v0.15.0: services that need a paid or separate account aren't offered: Summary says so, Features leave them out.
        val sms = BriefWriter.write(listOf("A delivery app for my shop. Log in with OTP, live tracking on a map and WhatsApp updates."))
        assertTrue(sms.sections.first { it.id == SectionIds.SUMMARY }.body.endsWith(
            "We build native iPhone and Android apps; text messages, WhatsApp messages and a map inside the app aren't part of this.",
        ))
        val features = sms.sections.first { it.id == SectionIds.FEATURES }.body
        assertTrue(features.contains("- Sign in with Apple or Google"))
        listOf("OTP", "map", "WhatsApp", "one-time").forEach { assertTrue("no \"$it\" in Features", !features.contains(it, ignoreCase = true)) }
    }

    @Test
    fun `digital things are paid through the stores`() {
        val draft = BriefWriter.write(listOf("A meditation app with premium audio lessons people can buy."))
        assertTrue(draft.sections.first { it.id == SectionIds.FEATURES }.body.contains("- Takes payments through the App Store and Google Play"))
    }

    @Test
    fun `assumptions end with the free-fix rule and bill nothing to the client`() {
        val draft = BriefWriter.write(listOf(salon))
        assertTrue("no section carries the free-fix rule (v0.15.0)", draft.sections.none { com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE in it.body })
        val doc = com.raviga.app.data.api.Document(title = draft.title, sections = draft.sections)
        val assumptions = BriefWriter.quote(doc, com.raviga.app.data.demo.DemoApi.demoConfig()).assumptions
        assertEquals(com.raviga.app.data.api.ReportConfig.FREE_FIXES_LINE, assumptions.last())
        assertTrue(assumptions.none { "Third-party" in it || "billed" in it })
    }
}
