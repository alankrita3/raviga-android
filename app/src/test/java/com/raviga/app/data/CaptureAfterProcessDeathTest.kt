package com.raviga.app.data

import com.raviga.app.data.drafts.LocalInput
import com.raviga.app.data.drafts.WorkingNotes
import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Phase
import com.raviga.app.ui.capture.CaptureViewModel.State
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Review 2026-11-03 (Android): Android ended the app in the background on Capture and brought it back (OnePlus,
 * `am kill`). The screen came back empty and the next words made a second draft ("PD check a flower shop..." and
 * "And a loyalty card" side by side on Home). Now the visit's draft and notes come back: the text is on screen and a
 * change edits the same note.
 */
class CaptureAfterProcessDeathTest {

    private class FakeDrafts : WorkingNotes.Sink {
        val notes = mutableMapOf<String, MutableList<Pair<String, String>>>()   // draft -> (input id, text)
        var created = 0
        private var ids = 0
        override suspend fun create(): String = "ld_${++created}".also { notes[it] = mutableListOf() }
        override suspend fun add(draftId: String, kind: String, text: String, meta: WorkingNotes.Meta): String =
            "in_${++ids}".also { notes.getValue(draftId) += it to text }
        override suspend fun edit(draftId: String, inputId: String, text: String) {
            val list = notes.getValue(draftId)
            list[list.indexOfFirst { it.first == inputId }] = inputId to text
        }
        override suspend fun remove(draftId: String, inputId: String) { notes.getValue(draftId).removeAll { it.first == inputId } }
    }

    private fun input(id: String, text: String, kind: String = "text", fileName: String? = null, pages: Int? = null) =
        LocalInput(id = id, kind = kind, text = text, fileName = fileName, pageCount = pages, createdAt = "2026-11-03T06:14:00Z")

    @Test fun theNextWordsEditTheSameNoteInTheSameDraft() = runTest {
        val fake = FakeDrafts()
        val before = WorkingNotes(fake, null)
        before.save(CaptureViewModel.TAB_TYPE, "text", "PD check a flower shop ordering app")
        val draft = before.draftId
        val slots = before.slots()

        // The process is gone; the screen's saved state names the draft and the note.
        val after = WorkingNotes(fake, draft, slots.mapValues { (_, id) -> id to "PD check a flower shop ordering app" })
        after.save(CaptureViewModel.TAB_TYPE, "text", "PD check a flower shop ordering app and a loyalty card")

        assertEquals(1, fake.created)
        assertEquals(listOf("PD check a flower shop ordering app and a loyalty card"), fake.notes.getValue(draft!!).map { it.second })
    }

    @Test fun typedTextComesBackInTheEditor() {
        val s = CaptureViewModel.restoredState(
            State(tab = CaptureViewModel.TAB_TYPE), Phase.CAPTURE.name, CaptureViewModel.TAB_TYPE,
            mapOf(CaptureViewModel.TAB_TYPE to input("in_1", "PD check a flower shop")), savedNotes = 0, lastSavedName = null,
        )
        assertEquals(Phase.CAPTURE, s.phase)
        assertEquals("PD check a flower shop", s.typed)
    }

    @Test fun speechComesBackUnderReview() {
        val s = CaptureViewModel.restoredState(
            State(), Phase.CAPTURE.name, CaptureViewModel.TAB_SPEAK,
            mapOf(CaptureViewModel.TAB_SPEAK to input("in_2", "a salon booking app", kind = "voice")), 0, null,
        )
        assertEquals(Phase.TRANSCRIPT, s.phase)
        assertEquals("a salon booking app", s.transcript)
        assertEquals(false, s.listening)
    }

    @Test fun aDocumentUnderReviewKeepsItsName() {
        val s = CaptureViewModel.restoredState(
            State(), Phase.TRANSCRIPT.name, CaptureViewModel.TAB_UPLOAD,
            mapOf(CaptureViewModel.TAB_UPLOAD to input("in_3", "Clinic handbook", kind = "file", fileName = "long.pdf", pages = 61)), 0, null,
        )
        assertEquals(Phase.TRANSCRIPT, s.phase)
        assertEquals("long.pdf", s.file?.fileName)
        assertEquals(61, s.file?.pageCount)
    }

    @Test fun theChoicesAfterASavedDocumentComeBack() {
        val s = CaptureViewModel.restoredState(State(), Phase.CHOOSE.name, CaptureViewModel.TAB_UPLOAD, emptyMap(), savedNotes = 1, lastSavedName = "spec.pdf")
        assertEquals(Phase.CHOOSE, s.phase)
        assertEquals(1, s.savedNotes)
        assertEquals("spec.pdf", s.lastSavedName)
    }

    @Test fun writingComesBackAsTheReviewItStartedFrom() {
        val s = CaptureViewModel.restoredState(
            State(), Phase.DRAFTING.name, CaptureViewModel.TAB_TYPE,
            mapOf(CaptureViewModel.TAB_TYPE to input("in_4", "a tiffin delivery app")), 0, null,
        )
        assertEquals(Phase.TRANSCRIPT, s.phase)
        assertEquals("a tiffin delivery app", s.transcript)
    }

    @Test fun nothingSavedYetIsAFreshScreen() {
        val s = CaptureViewModel.restoredState(State(tab = CaptureViewModel.TAB_TYPE), Phase.CAPTURE.name, CaptureViewModel.TAB_TYPE, emptyMap(), 0, null)
        assertEquals(Phase.CAPTURE, s.phase)
        assertEquals("", s.typed)
        assertNull(s.file)
    }
}
