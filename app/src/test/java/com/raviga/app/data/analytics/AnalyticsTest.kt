package com.raviga.app.data.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract v0.9 section 22: the funnel, gated on consent, enums and counts only. */
class AnalyticsTest {

    private class Recorder : AnalyticsSink {
        val enabledCalls = mutableListOf<Boolean>()
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        var resets = 0
        override fun setCollectionEnabled(enabled: Boolean) { enabledCalls += enabled }
        override fun log(name: String, params: Map<String, Any>) { events += name to params }
        override fun reset() { resets++ }
    }

    private val sink = Recorder()
    private val analytics = Analytics(sink)

    @Test
    fun `nothing is logged without consent, and the first state always reaches the SDK`() {
        analytics.setEnabled(false)
        analytics.onboardingComplete()
        analytics.quoteViewed("standard", 30)
        assertEquals(listOf(false), sink.enabledCalls)
        assertTrue(sink.events.isEmpty())
        analytics.setEnabled(false)
        assertEquals("no repeat call for the same state", listOf(false), sink.enabledCalls)
    }

    @Test
    fun `events and parameters are exactly the contract's`() {
        analytics.setEnabled(true)
        analytics.onboardingComplete()
        analytics.briefStarted(Analytics.InputKind.VOICE)
        analytics.briefGenerated(inputs = 2, sectionsFilled = 11)
        analytics.quoteViewed("standard", 34)
        analytics.projectSubmitted("standard", 34)
        analytics.creditsPurchased("credits_25", 27)
        analytics.deliveryAccepted("standard", 1)
        assertEquals(
            listOf(
                "onboarding_complete" to emptyMap<String, Any>(),
                "brief_started" to mapOf("input_kind" to "voice"),
                "brief_generated" to mapOf("inputs" to 2, "sections_filled" to 11),
                "quote_viewed" to mapOf("bracket" to "standard", "credits" to 34),
                "project_submitted" to mapOf("bracket" to "standard", "credits" to 34),
                "credits_purchased" to mapOf("product_id" to "credits_25", "credits" to 27),
                "delivery_accepted" to mapOf("bracket" to "standard", "revisions_used" to 1),
            ),
            sink.events,
        )
    }

    @Test
    fun `values outside the contract's enums are left out, counts are clamped`() {
        analytics.setEnabled(true)
        analytics.quoteViewed("Dental clinic app", 30)
        analytics.creditsPurchased("promo_pack", 5)
        analytics.deliveryAccepted(null, -1)
        analytics.briefGenerated(inputs = 1, sectionsFilled = 40)
        assertEquals(mapOf("credits" to 30), sink.events[0].second)
        assertEquals(mapOf("credits" to 5), sink.events[1].second)
        assertEquals(mapOf("revisions_used" to 0), sink.events[2].second)
        assertEquals(12, sink.events[3].second["sections_filled"])
    }

    @Test
    fun `deletion turns collection off and resets the SDK`() {
        analytics.setEnabled(true)
        analytics.clearAfterDeletion()
        assertFalse(analytics.enabled)
        assertEquals(listOf(true, false), sink.enabledCalls)
        assertEquals(1, sink.resets)
        analytics.onboardingComplete()
        assertTrue(sink.events.isEmpty())
    }
}
