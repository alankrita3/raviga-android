package com.raviga.app.data

import com.raviga.app.data.drafts.WorkingNotes
import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Phase
import com.raviga.app.ui.capture.CaptureViewModel.State
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Contract v0.6 section 8 (the founder's bug, 2026-11-02): what's typed, spoken or read is saved in the local draft
 * as it changes, so leaving Capture at any point loses nothing.
 */
class WorkingNotesTest {

    /** The draft repository's note calls, in memory. */
    private class FakeDrafts : WorkingNotes.Sink {
        val drafts = mutableMapOf<String, MutableList<Triple<String, String, String>>>()   // draft -> (input id, kind, text)
        var created = 0
        private var inputs = 0

        override suspend fun create(): String {
            delay(10)   // a real write takes a moment; overlapping saves must still make one draft
            created++
            return "ld_$created".also { drafts[it] = mutableListOf() }
        }

        override suspend fun add(draftId: String, kind: String, text: String, meta: WorkingNotes.Meta): String {
            val id = "in_${++inputs}"
            drafts.getValue(draftId) += Triple(id, kind, text)
            return id
        }

        override suspend fun edit(draftId: String, inputId: String, text: String) {
            val notes = drafts.getValue(draftId)
            val i = notes.indexOfFirst { it.first == inputId }
            notes[i] = notes[i].copy(third = text)
        }

        override suspend fun remove(draftId: String, inputId: String) {
            drafts.getValue(draftId).removeAll { it.first == inputId }
        }

        fun notes(draftId: String) = drafts.getValue(draftId).map { it.second to it.third }
    }

    @Test fun nothingIsMadeUntilThereAreWords() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        notes.save(CaptureViewModel.TAB_TYPE, "text", "   ")
        assertEquals(0, fake.created)
        assertNull(notes.draftId)
    }

    @Test fun typingMakesTheDraftOnceAndEditsTheSameNote() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        notes.save(CaptureViewModel.TAB_TYPE, "text", "A bakery")
        notes.save(CaptureViewModel.TAB_TYPE, "text", "A bakery order app")
        notes.save(CaptureViewModel.TAB_TYPE, "text", "A bakery order app ")   // same words: no write
        assertEquals(1, fake.created)
        assertEquals(listOf("text" to "A bakery order app"), fake.notes(notes.draftId!!))
    }

    @Test fun eachWayOfDescribingIsItsOwnNote() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        notes.save(CaptureViewModel.TAB_TYPE, "text", "Typed part")
        notes.save(CaptureViewModel.TAB_SPEAK, "voice", "Spoken part")
        notes.save(CaptureViewModel.TAB_SPEAK, "voice", "Spoken part, longer")
        assertEquals(listOf("text" to "Typed part", "voice" to "Spoken part, longer"), fake.notes(notes.draftId!!))
    }

    @Test fun clearingTheTextTakesTheNoteOut() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        notes.save(CaptureViewModel.TAB_TYPE, "text", "Oops")
        notes.save(CaptureViewModel.TAB_TYPE, "text", "")
        assertEquals(emptyList<Pair<String, String>>(), fake.notes(notes.draftId!!))
        notes.save(CaptureViewModel.TAB_TYPE, "text", "Again")
        assertEquals(listOf("text" to "Again"), fake.notes(notes.draftId!!))
    }

    @Test fun theNextDocumentIsANewNoteAndTheFirstStays() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        notes.save(CaptureViewModel.TAB_UPLOAD, "file", "Spec from the PDF")
        notes.forget(CaptureViewModel.TAB_UPLOAD)
        notes.save(CaptureViewModel.TAB_UPLOAD, "file", "Notes from the Word file")
        assertEquals(listOf("file" to "Spec from the PDF", "file" to "Notes from the Word file"), fake.notes(notes.draftId!!))
    }

    @Test fun anOpenDraftGetsTheNotesAndNoNewDraftIsMade() = runTest {
        val fake = FakeDrafts()
        fake.drafts["ld_open"] = mutableListOf(Triple("in_old", "text", "Earlier note"))
        val notes = WorkingNotes(fake, "ld_open")
        notes.save(CaptureViewModel.TAB_SPEAK, "voice", "More, spoken")
        assertEquals(0, fake.created)
        assertEquals(listOf("text" to "Earlier note", "voice" to "More, spoken"), fake.notes("ld_open"))
    }

    @Test fun overlappingSavesMakeOneDraftAndOneNote() = runTest {
        val fake = FakeDrafts()
        val notes = WorkingNotes(fake, null)
        listOf("A", "A bakery", "A bakery app").map { text -> async { notes.save(CaptureViewModel.TAB_TYPE, "text", text) } }.awaitAll()
        assertEquals(1, fake.created)
        assertEquals(1, fake.notes(notes.draftId!!).size)
    }

    // ----- What's on screen is what gets saved -----

    @Test fun liveTextFollowsTheScreen() {
        val consumed = 1
        // Typing.
        assertEquals("Half a sentence", CaptureViewModel.liveText(State(tab = CaptureViewModel.TAB_TYPE, typed = "Half a sentence"), 0))
        // Speaking: only the utterances not yet under review, plus the one still being heard.
        val speaking = State(tab = CaptureViewModel.TAB_SPEAK, committed = listOf("old", "new words"), partial = "and more")
        assertEquals("new words and more", CaptureViewModel.liveText(speaking, consumed))
        // Recording more after a review: joined to what was reviewed.
        assertEquals(
            "Reviewed text new words",
            CaptureViewModel.liveText(speaking.copy(continuing = true, transcript = "Reviewed text", partial = ""), consumed),
        )
        // Review: the text being edited.
        assertEquals("Edited on review", CaptureViewModel.liveText(State(phase = Phase.TRANSCRIPT, transcript = "Edited on review"), 0))
        // Nothing to save: a document still being read, the upload picker, the choices, writing.
        assertNull(CaptureViewModel.liveText(State(phase = Phase.TRANSCRIPT, tab = CaptureViewModel.TAB_UPLOAD, reading = true), 0))
        assertNull(CaptureViewModel.liveText(State(tab = CaptureViewModel.TAB_UPLOAD), 0))
        assertNull(CaptureViewModel.liveText(State(phase = Phase.CHOOSE), 0))
        assertNull(CaptureViewModel.liveText(State(phase = Phase.DRAFTING, transcript = "x"), 0))
    }

    /** QA D4: a note over limits.textInputMaxChars is refused at "write the brief" with iOS's words; not cut. */
    @Test fun aNoteOverTheLimitIsRefusedNotCut() {
        assertNull(CaptureViewModel.overLimit("a".repeat(20_000), 20_000))
        assertEquals(
            "That's over the 20,000 character limit. Shorten it, or save and add the rest as another note.",
            CaptureViewModel.overLimit("a".repeat(20_001), 20_000),
        )
        assertNull("no limit from config: nothing to check", CaptureViewModel.overLimit("a".repeat(50_000), 0))
    }

    /** QA H9: a section or the whole brief over its limit is caught before an AI call, in iOS's words. */
    @Test fun aBriefOverItsLimitsIsCaughtBeforeTheCall() {
        fun doc(vararg bodies: String) = com.raviga.app.data.api.Document(
            title = "T",
            sections = bodies.mapIndexed { i, b -> com.raviga.app.data.api.Section(if (i == 0) "summary" else "screens", if (i == 0) "Summary" else "Screens", b) },
        )
        val limits = com.raviga.app.ui.document.BriefLimits
        assertNull(limits.problem(doc("a".repeat(20_000), "b".repeat(20_000)), 20_000, 60_000))
        assertEquals(
            "The Screens section is over the 20,000 character limit. Shorten it, then try again.",
            limits.problem(doc("ok", "b".repeat(20_001)), 20_000, 60_000),
        )
        assertEquals(
            "The brief is over the 60,000 character limit. Shorten a section, then try again.",
            limits.problem(doc("a".repeat(20_000), "b".repeat(20_000), "c".repeat(20_000)), 20_000, 60_000),
        )
    }

    /** QA F5: a draft holds at most limits.inputsPerProject notes; the 11th is refused with iOS's words. */
    @Test fun aDraftTakesNoMoreThanTheNoteLimit() {
        val limit = com.raviga.app.data.repo.DraftRepository
        assertNull(limit.noteLimitProblem(9, 10))
        assertEquals("A project can have up to 10 notes. Add the rest to an existing one.", limit.noteLimitProblem(10, 10))
        assertNull("no limit from config", limit.noteLimitProblem(50, 0))
    }

    /** QA G2: half a second after the last change, but typing without a pause still saves every second. */
    @Test fun savesAtLeastEverySecondWhileTyping() {
        assertEquals(500L, CaptureViewModel.saveDelay(now = 1_000, firstUnsavedAt = 1_000))
        assertEquals(300L, CaptureViewModel.saveDelay(now = 1_700, firstUnsavedAt = 1_000))
        assertEquals(0L, CaptureViewModel.saveDelay(now = 2_200, firstUnsavedAt = 1_000))
    }
}
