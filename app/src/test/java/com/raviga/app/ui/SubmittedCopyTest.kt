package com.raviga.app.ui

import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.Section
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.ui.document.DocumentViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/** R-092 #34 (iOS review): a cancelled project's brief is the one submitted, not the edits made after. */
class SubmittedCopyTest {
    private fun doc(v: Int, body: String) = Document(version = v, title = "T", sections = listOf(Section("summary", "Summary", body)))

    private val draft = LocalDraft(
        id = "ld_1", createdAt = "a", updatedAt = "a", serverProjectId = "pr_1", submittedVersion = 1,
        versions = listOf(LocalVersion(1, doc(1, "As submitted"), "a", "draft"), LocalVersion(2, doc(2, "Edited after"), "a", "edit")),
    )

    @Test fun aClosedProjectShowsTheSubmittedVersion() {
        val cancelled = DocumentViewModel.State(draft = draft, project = Project(id = "pr_1", status = ProjectStatus.CANCELLED))
        assertEquals("As submitted", cancelled.document?.sections?.first()?.body)
    }

    @Test fun aChangesRoundStillEditsTheNewestVersion() {
        val changes = DocumentViewModel.State(draft = draft, project = Project(id = "pr_1", status = ProjectStatus.CHANGES_REQUESTED))
        assertEquals("Edited after", changes.document?.sections?.first()?.body)
    }
}
