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
 * R-097 #4 (review 2): a held retry sends the body it first sent, so neither the current brief's length nor a blank
 * GitHub field stops it; without a held send both still do.
 */
class HeldRetryTest {
    private val limits = LimitsConfig(textInputMaxChars = 20, documentMaxChars = 40)
    private fun doc(v: Int, body: String) = Document(version = v, title = "Bakery site", sections = listOf(Section("summary", "Summary", body)))
    private val sent = SubmitProjectRequest(
        com.raviga.app.data.api.DocumentBody(title = "Bakery site", sections = emptyList()), "tok", githubUsername = "old-name",
    )
    private val tooLong = "An edit made after the send that runs well past every limit."
    private val draft = LocalDraft(
        id = "ld_1", createdAt = "a", updatedAt = "a",
        versions = listOf(LocalVersion(1, doc(1, "Short."), "a", "draft"), LocalVersion(2, doc(2, tooLong), "b", "edit")),
        quote = Quote(id = "q1", credits = 7), quoteToken = "tok", quotedVersion = 1,
        sentSubmit = SentSubmit("tok", 1, submit = sent),
    )

    @Test fun aHeldRetryIsNeverStoppedByTheCurrentBriefOrField() {
        val s = QuoteViewModel.State(draft = draft, github = "")
        assertNull(QuoteViewModel.submitProblem(s, limits))
        assertEquals("the field shows the name that goes", "old-name", s.heldName)
    }

    @Test fun withoutAHeldSendBothChecksStand() {
        val fresh = draft.copy(sentSubmit = null)
        assertEquals(true, QuoteViewModel.submitProblem(QuoteViewModel.State(draft = fresh, github = "octocat"), limits)?.contains("character limit"))
        val short = fresh.copy(versions = listOf(LocalVersion(1, doc(1, "Short."), "a", "draft")))
        assertEquals(QuoteViewModel.NO_GITHUB, QuoteViewModel.submitProblem(QuoteViewModel.State(draft = short, github = " "), limits))
        assertNull(QuoteViewModel.submitProblem(QuoteViewModel.State(draft = short, github = "octocat"), limits))
    }

    /** R-097 #5 (the PM's words): while held, the quote screen says what goes. */
    @Test fun whileHeldTheScreenSaysTheFirstSubmittedBriefGoes() {
        assertEquals(
            "This sends the brief as you first submitted it. Your later edits stay on this phone as a newer version.",
            QuoteViewModel.State(draft = draft).heldLine,
        )
        assertNull("nothing held: no line", QuoteViewModel.State(draft = draft.copy(sentSubmit = null)).heldLine)
        assertNull(QuoteViewModel.State(draft = draft.copy(sentSubmit = null)).heldName)
    }
}
