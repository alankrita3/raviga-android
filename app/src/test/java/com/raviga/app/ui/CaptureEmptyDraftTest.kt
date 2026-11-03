package com.raviga.app.ui

import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.ui.capture.CaptureViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review #4 (Android): Back from Capture drops an empty draft only if this visit made it. */
class CaptureEmptyDraftTest {
    private val empty = LocalDraft(id = "ld_a", title = "Bakery app", createdAt = "a", updatedAt = "a")

    @Test fun anEmptyDraftThisVisitMadeIsDropped() {
        assertTrue(CaptureViewModel.shouldDropEmpty(createdThisVisit = true, mode = "new", draft = empty.copy(title = "")))
    }

    @Test fun plusOnAnExistingEmptyDraftNeverDeletesIt() {
        assertFalse("named, notes all deleted, then + and Back", CaptureViewModel.shouldDropEmpty(createdThisVisit = false, mode = "new", draft = empty))
    }

    @Test fun aDraftWithANoteOrABriefStays() {
        val withNote = empty.copy(inputs = listOf(LocalInput("in_1", "text", "hello", createdAt = "a")))
        assertFalse(CaptureViewModel.shouldDropEmpty(createdThisVisit = true, mode = "new", draft = withNote))
        assertFalse("append never drops", CaptureViewModel.shouldDropEmpty(createdThisVisit = true, mode = "append", draft = empty))
        assertFalse("already gone", CaptureViewModel.shouldDropEmpty(createdThisVisit = true, mode = "new", draft = null))
    }
}
