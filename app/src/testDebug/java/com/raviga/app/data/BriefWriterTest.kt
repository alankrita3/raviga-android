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
    fun `writes all twelve sections in order`() {
        val draft = BriefWriter.write(listOf(salon))
        assertEquals(SectionIds.ordered, draft.sections.map { it.id })
        assertTrue(draft.sections.all { it.body.isNotBlank() })
        assertEquals("GlowBook", draft.title)
    }

    @Test
    fun `detects features and platforms`() {
        val draft = BriefWriter.write(listOf(salon))
        val must = draft.sections.first { it.id == SectionIds.FEATURES_MUST }.body
        assertTrue(must.contains("Booking", ignoreCase = true))
        assertTrue(must.contains("Payments", ignoreCase = true))
        val platforms = draft.sections.first { it.id == SectionIds.PLATFORMS }.body
        assertTrue(platforms.contains("Admin panel"))
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
        val section = draft.sections.first { it.id == SectionIds.NON_FUNCTIONAL }
        val shorter = BriefWriter.regenerate(section, listOf(salon), "make it shorter")
        assertTrue(shorter.body.lines().size < section.body.lines().size)
    }

    @Test
    fun `append only adds new bullets`() {
        val first = BriefWriter.write(listOf(salon))
        val doc = Document(version = 1, title = first.title, sections = first.sections)
        val merged = BriefWriter.append(doc, listOf(salon, "Also add a loyalty points and rewards feature."))
        val nice = merged.first { it.id == SectionIds.FEATURES_NICE }.body
        assertTrue(nice.contains("rewards", ignoreCase = true))
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
