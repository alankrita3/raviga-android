package com.raviga.app.ui

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.Section
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.SentSubmit
import com.raviga.app.ui.quote.QuoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Second review 2026-11-03 (Android): a submit whose answer was lost may have charged. With 10 credits and a 7-credit
 * quote the balance then reads 3, and the screen said "You have 3 credits. You need 4 more." with only Buy credits,
 * so the retry that turns the draft into the paid project needed a purchase first.
 */
class HeldSubmitBalanceTest {
    private val doc = Document(version = 1, title = "Bakery site", sections = listOf(Section("summary", "Summary", "A one-page site.")))
    private val draft = LocalDraft(
        id = "ld_1", title = "Bakery site", createdAt = "a", updatedAt = "a",
        versions = listOf(LocalVersion(1, doc, "a", "draft")),
        quote = Quote(id = "qt_1", documentVersion = 1, credits = 7, expiresAt = "2099-01-01T00:00:00Z"),
        quoteToken = "tok", quotedVersion = 1,
    )
    private val held = draft.copy(sentSubmit = SentSubmit("tok", 1, submit = SubmitProjectRequest(doc.body(), "tok", "octocat", null)))

    @Test fun aHeldSendIsRetriedWhateverTheBalanceReads() {
        assertEquals(0, QuoteViewModel.State(draft = held, balance = 3).shortBy)
    }

    @Test fun withNothingHeldAShortBalanceStillAsksForCredits() {
        assertEquals(4, QuoteViewModel.State(draft = draft, balance = 3).shortBy)
        assertEquals("a hold for an older token doesn't count", 4, QuoteViewModel.State(draft = held.copy(quoteToken = "tok2"), balance = 3).shortBy)
        assertEquals(0, QuoteViewModel.State(draft = draft, balance = 7).shortBy)
    }
}
