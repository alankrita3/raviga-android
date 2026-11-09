package com.raviga.app.ui

import com.raviga.app.ui.quote.complexitySentence
import com.raviga.app.ui.quote.timelineText
import com.raviga.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteCopyTest {

    @Test fun namesKeepTheirCapitals() {
        assertEquals(
            "Sized for Android app, iOS app, web app and admin panel.",
            complexitySentence(listOf("Android app", "iOS app", "Web app", "Admin panel")),
        )
        assertEquals("Sized for payments and WhatsApp alerts.", complexitySentence(listOf("Payments.", "WhatsApp alerts")))
        assertEquals("", complexitySentence(listOf(" ")))
        // Same case as iOS 6c457ef.
        assertEquals(
            "Sized for Android app, iOS app, web app and Stripe payments.",
            complexitySentence(listOf("Android app", "iOS app", "Web app", "Stripe payments")),
        )
        assertEquals("Sized for Zapier hooks and iPhone widgets.", complexitySentence(listOf("Zapier hooks", "iPhone widgets")))
    }

    /** R-103 (the PM's final copy, as iOS): submitting is free; paying comes once it's accepted. */
    @Test fun submitIsFreeAndSaysSo() {
        assertEquals("Free to submit. You pay only once we accept it.", com.raviga.app.ui.quote.FREE_TO_SUBMIT)
        val steps = com.raviga.app.ui.quote.nextSteps()
        // R-109 (B, PM-approved as iOS): the demo on the phone, then we publish it; no code, no AWS before paying.
        assertEquals(5, steps.size)
        assertEquals(
            "You get the app on your phone to try, through TestFlight and Google Play. Approve it, tell us what's wrong, or ask for a change.",
            steps[3],
        )
        assertEquals("Once you approve it, we publish it to your own App Store and Google Play accounts.", steps[4])
        org.junit.Assert.assertTrue(steps.none { "revision" in it || "comments" in it || "code" in it || "AWS" in it })
    }

    @Test fun timelineReadsNaturally() {
        assertEquals("1 to 2 weeks", timelineText(1, 2, 8))
        assertEquals("about a week", timelineText(1, 1, 3))
        assertEquals("about 3 weeks", timelineText(3, 3, 15))
        assertEquals("about a week", timelineText(0, 0, 4))
        assertEquals("about 2 weeks", timelineText(0, 0, 8))
    }

    @Test fun dollarsShowCentsOnlyWhenThereAreSome() {
        assertEquals("$320", Money.usd(320.0))
        assertEquals("$1,250", Money.usd(1250.0))
        assertEquals("$99.99", Money.usd(99.99))
        assertEquals("$10", Money.usd(10.0))
    }

    /** QA I3, as iOS: why a new quote is needed, from expiresAt before any submit, and the server's own words after. */
    @Test fun anExpiredOrRefusedQuoteSaysWhy() {
        val now = 1_800_000_000_000L
        fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
        val quote = com.raviga.app.data.api.Quote(id = "qt_1", documentVersion = 1, credits = 7, expiresAt = iso(now + 60_000))
        val doc = com.raviga.app.data.api.Document(version = 1, title = "T", sections = emptyList())
        val draft = com.raviga.app.data.drafts.LocalDraft(
            id = "ld_1", createdAt = "a", updatedAt = "a",
            versions = listOf(com.raviga.app.data.drafts.LocalVersion(1, doc, "a", "draft")),
            quote = quote, quoteToken = "tok", quotedVersion = 1,
        )
        val fresh = com.raviga.app.ui.quote.QuoteViewModel.State(draft = draft, clock = now)
        assertEquals(false, fresh.needsNewQuote)
        assertEquals(null, fresh.staleLine)

        val later = fresh.copy(clock = now + 60_000)
        assertEquals(true, later.needsNewQuote)
        assertEquals("This quote has expired. Get a new one before submitting.", later.staleLine)

        val edited = fresh.copy(draft = draft.copy(quotedVersion = 0))
        assertEquals("The brief changed since this quote.", edited.staleLine)

        // R-100 (iOS a5f8fc9): the date while it holds, why once it ran out, nothing after an edit.
        assertEquals("Price valid until ${com.raviga.app.util.Time.shortDate(iso(now + 60_000))}.", fresh.priceLine)
        assertEquals("Prices are updated every three months.", later.priceLine)
        assertEquals(null, edited.priceLine)

        val refused = fresh.copy(staleMessage = "That quote has already been used. Get a fresh quote and try again.")
        assertEquals(true, refused.needsNewQuote)
        assertEquals("That quote has already been used. Get a fresh quote and try again.", refused.staleLine)
    }

    /** R-092 #18 (iOS review): a retried submit carries the same key, even after a relaunch; a new quote a new one. */
    @Test fun theSubmitKeyFollowsTheQuoteToken() {
        val first = com.raviga.app.ui.quote.idempotencyKeyFor("tok-1")
        assertEquals(first, com.raviga.app.ui.quote.idempotencyKeyFor("tok-1"))
        org.junit.Assert.assertNotEquals(first, com.raviga.app.ui.quote.idempotencyKeyFor("tok-2"))
        assertEquals(36, first.length)
    }
}
