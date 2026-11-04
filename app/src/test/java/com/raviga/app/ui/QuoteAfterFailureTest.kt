package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.Section
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.SentSubmit
import com.raviga.app.ui.quote.QuoteViewModel
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Second review 2026-11-03 (Android): the server named the project this quote already made (R-091), but reading it
 * failed, so the draft couldn't be turned into it. That must never offer "Get a new quote" (a new token: a second
 * project and charge); it used to, because any quote_stale set the stale line.
 */
class QuoteAfterFailureTest {
    private val doc = Document(version = 1, title = "Bakery site", sections = listOf(Section("summary", "Summary", "A one-page site.")))
    private val expired = Quote(id = "qt_1", documentVersion = 1, credits = 7, expiresAt = "2026-11-01T00:00:00Z")
    private val draft = LocalDraft(
        id = "ld_1", title = "Bakery site", createdAt = "a", updatedAt = "a",
        versions = listOf(LocalVersion(1, doc, "a", "draft")),
        quote = expired, quoteToken = "tok", quotedVersion = 1,
    )
    private val held = draft.copy(sentSubmit = SentSubmit("tok", 1, submit = SubmitProjectRequest(doc.body(), "tok", null)))
    private val now = System.currentTimeMillis()

    private val namedProject = ApiException(
        ApiException.QUOTE_STALE, 409, "You already submitted this brief as RA-ABC123.",
        details = buildJsonObject { put("projectId", JsonPrimitive("pr_1")); put("ref", JsonPrimitive("RA-ABC123")) },
    )
    private val offline = ApiException(ApiException.NETWORK)

    @Test fun aNamedProjectThatCouldntBeReadIsNeverReQuoted() {
        val after = QuoteViewModel.afterFailedSubmit(QuoteViewModel.State(draft = held, submitting = true, clock = now), namedProject, offline, now)
        assertNull(after.staleMessage)
        assertFalse("Submit stays: the retry sends the same request", after.needsNewQuote)
        assertFalse(after.submitting)
        assertEquals("Couldn't reach Raviga. Check your connection and try again.", after.error)
    }

    @Test fun aCurrentQuoteThatNamedAProjectStaysToo() {
        val fresh = draft.copy(quote = expired.copy(expiresAt = "2099-01-01T00:00:00Z"))
        val after = QuoteViewModel.afterFailedSubmit(QuoteViewModel.State(draft = fresh, clock = now), namedProject, offline, now)
        assertFalse(after.needsNewQuote)
    }

    @Test fun anyOtherStaleQuoteStillAsksForANewOne() {
        val stale = ApiException(ApiException.QUOTE_STALE, 409, "This quote has expired.")
        val after = QuoteViewModel.afterFailedSubmit(QuoteViewModel.State(draft = draft, clock = 0), stale, null, now)
        assertEquals("This quote has expired.", after.staleMessage)
        assertTrue(after.needsNewQuote)
        assertNull(after.error)
        assertEquals(now, after.clock)
    }

    @Test fun otherFailuresShowTheirLine() {
        val after = QuoteViewModel.afterFailedSubmit(QuoteViewModel.State(draft = held, submitting = true), offline, null, now)
        assertEquals("Couldn't reach Raviga. Check your connection and try again.", after.error)
        assertFalse(after.submitting)
    }
}
