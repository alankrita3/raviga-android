package com.raviga.app.ui

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.LimitsConfig
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.Section
import com.raviga.app.data.api.SubmitProjectRequest
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.data.drafts.SentSubmit
import com.raviga.app.ui.quote.QuoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-097 #4 (review 2): a held retry sends the body it first sent, so the current brief's length doesn't stop it;
 * without a held send it still does. (v0.11.0: no GitHub field any more.)
 */
class HeldRetryTest {
    private val limits = LimitsConfig(textInputMaxChars = 20, documentMaxChars = 40)
    private fun doc(v: Int, body: String) = Document(version = v, title = "Bakery site", sections = listOf(Section("summary", "Summary", body)))
    private val sent = SubmitProjectRequest(
        com.raviga.app.data.api.DocumentBody(title = "Bakery site", sections = emptyList()), "tok",
    )
    private val tooLong = "An edit made after the send that runs well past every limit."
    private val draft = LocalDraft(
        id = "ld_1", createdAt = "a", updatedAt = "a",
        versions = listOf(LocalVersion(1, doc(1, "Short."), "a", "draft"), LocalVersion(2, doc(2, tooLong), "b", "edit")),
        quote = Quote(id = "q1", credits = 7), quoteToken = "tok", quotedVersion = 1,
        sentSubmit = SentSubmit("tok", 1, submit = sent),
    )

    @Test fun aHeldRetryIsNeverStoppedByTheCurrentBrief() {
        assertNull(QuoteViewModel.submitProblem(QuoteViewModel.State(draft = draft), limits))
    }

    @Test fun withoutAHeldSendTheLimitsStand() {
        val fresh = draft.copy(sentSubmit = null)
        assertEquals(true, QuoteViewModel.submitProblem(QuoteViewModel.State(draft = fresh), limits)?.contains("character limit"))
        val short = fresh.copy(versions = listOf(LocalVersion(1, doc(1, "Short."), "a", "draft")))
        assertNull("no GitHub name needed any more", QuoteViewModel.submitProblem(QuoteViewModel.State(draft = short), limits))
    }

    /** R-097 #5, R-098 (the PM's final words): while held, the quote screen says what goes. */
    @Test fun whileHeldTheScreenSaysTheFirstSubmittedBriefGoes() {
        assertEquals(
            "This sends the brief as you first submitted it. Editing opens again once that's confirmed.",
            QuoteViewModel.State(draft = draft).heldLine,
        )
        assertNull("nothing held: no line", QuoteViewModel.State(draft = draft.copy(sentSubmit = null)).heldLine)
    }

    /** R-098 P3-4: while held the brief is read-only; it opens again once the answer is seen. */
    @Test fun whileHeldTheBriefIsReadOnly() {
        assertEquals(false, com.raviga.app.ui.document.DocumentViewModel.State(draft = draft).editable)
        assertEquals(true, com.raviga.app.ui.document.DocumentViewModel.State(draft = draft).held)
        assertEquals(true, com.raviga.app.ui.document.DocumentViewModel.State(draft = draft.copy(sentSubmit = null)).editable)
    }
}
