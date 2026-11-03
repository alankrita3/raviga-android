package com.raviga.app.ui

import com.raviga.app.data.api.Project
import com.raviga.app.data.api.Quote
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.ui.quote.QuoteViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-094 P2-12 (iOS review 2): a draft turned into the project the server already has (R-091) is counted as a submit,
 * like one whose answer arrived; a resubmit isn't (contract v0.9).
 */
class SubmitFollowUpTest {
    private val draft = LocalDraft(id = "ld_a", createdAt = "a", updatedAt = "a", quote = Quote(id = "q1", credits = 27, bracketId = "standard"))

    @Test fun anAdoptedSubmitCountsWithTheProjectsQuote() {
        val adopted = Project(id = "pr_1", quote = Quote(id = "q1", credits = 27, bracketId = "standard"))
        assertEquals("standard" to 27, QuoteViewModel.submittedEvent(adopted, draft, isResubmit = false))
    }

    @Test fun withoutTheProjectsQuoteTheDraftsIsUsed() {
        assertEquals("standard" to 27, QuoteViewModel.submittedEvent(Project(id = "pr_1"), draft, isResubmit = false))
    }

    @Test fun aResubmitIsNotCounted() {
        assertNull(QuoteViewModel.submittedEvent(Project(id = "pr_1"), draft, isResubmit = true))
    }
}
