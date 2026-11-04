package com.raviga.app.ui

import com.raviga.app.data.drafts.WorkingNotes
import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_SPEAK
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_TYPE
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_UPLOAD
import com.raviga.app.ui.capture.CaptureViewModel.Phase
import com.raviga.app.ui.capture.CaptureViewModel.State
import com.raviga.app.ui.capture.partSource
import com.raviga.app.util.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * R-108 (her decision via the PM, 2026-11-04): a description takes more by any way at any time. Started by speaking,
 * typing or uploading, "Add more" on the review saves the part and offers Speak, Type or Upload a document; each part is
 * its own note at the end, the same way twice included. Driven through the real [WorkingNotes] and the screen's own
 * state steps, in the order CaptureViewModel calls them.
 */
class AddMoreTest {

    /** The draft's notes, in memory: (kind, text) in order. */
    private class Drafts : WorkingNotes.Sink {
        val notes = mutableListOf<Triple<String, String, String>>()   // id, kind, text
        override suspend fun create() = "ld_1"
        override suspend fun add(draftId: String, kind: String, text: String, meta: WorkingNotes.Meta) =
            "in_${notes.size + 1}".also { notes += Triple(it, kind, text) }
        override suspend fun edit(draftId: String, inputId: String, text: String) {
            val i = notes.indexOfFirst { it.first == inputId }
            notes[i] = notes[i].copy(third = text)
        }
        override suspend fun remove(draftId: String, inputId: String) { notes.removeAll { it.first == inputId } }
        fun parts() = notes.map { it.second to it.third }
    }

    /** One visit to Capture: what the screen does for each tap, through the real notes and state steps. */
    private class Visit(first: Int) {
        val drafts = Drafts()
        val notes = WorkingNotes(drafts, null)
        var s = State(tab = first)

        private fun kind(tab: Int) = when (tab) { TAB_SPEAK -> "voice"; TAB_UPLOAD -> "file"; else -> "text" }
        private suspend fun autosave() { CaptureViewModel.liveText(s)?.let { notes.save(s.tab, kind(s.tab), it) } }

        /** Makes a part the [s.tab] way and brings it to review (speaking: utterances then Done; typing; a document). */
        suspend fun make(words: String) {
            when (s.tab) {
                TAB_SPEAK -> {
                    s = CaptureViewModel.heard(s, com.raviga.app.data.audio.DictationEngine.Event.Utterance(words))
                    autosave()
                    s = s.copy(phase = Phase.TRANSCRIPT, consumed = s.committed.size, transcript = CaptureViewModel.spokenReview(s.transcript, s.committed, s.consumed))
                }
                TAB_TYPE -> {
                    s = s.copy(typed = words); autosave()
                    s = s.copy(phase = Phase.TRANSCRIPT, transcript = s.typed.trim())
                }
                else -> {
                    notes.forget(TAB_UPLOAD)   // pickedFile: a new document is a new note
                    s = s.copy(phase = Phase.TRANSCRIPT, transcript = words)
                }
            }
            autosave()
        }

        /** "Add more" on the review, then one of the three ways. */
        suspend fun addMore(then: Int) {
            notes.close(s.tab, kind(s.tab), s.transcript.trim())
            s = CaptureViewModel.afterAddMore(s, null)
            s = s.copy(phase = Phase.CAPTURE, tab = then)   // choose(tab)
        }

        /** A tab switch before Done: the part on the tab left is finished. */
        suspend fun switchTo(tab: Int) {
            CaptureViewModel.leavingTabText(s, tab)?.let { notes.close(s.tab, kind(s.tab), it) }
            s = CaptureViewModel.leftTab(s).copy(tab = tab)
        }
    }

    private val ways = listOf(TAB_SPEAK to "voice", TAB_TYPE to "text", TAB_UPLOAD to "file")

    @Test fun allNineOrdersAddANewPartAtTheEnd() = runTest {
        for ((first, firstKind) in ways) for ((second, secondKind) in ways) {
            val visit = Visit(first)
            visit.make("First part.")
            visit.addMore(then = second)
            visit.make("Second part.")
            val order = "$firstKind then $secondKind"
            assertEquals(order, listOf(firstKind to "First part.", secondKind to "Second part."), visit.drafts.parts())
        }
    }

    @Test fun anEditOnReviewIsSavedWithItsPart() = runTest {
        val visit = Visit(TAB_SPEAK)
        visit.make("A booking app for a saloon.")
        visit.s = visit.s.copy(transcript = "A booking app for a salon.")   // fixed on review
        visit.addMore(then = TAB_TYPE)
        visit.make("Clients pay a deposit.")
        assertEquals(listOf("voice" to "A booking app for a salon.", "text" to "Clients pay a deposit."), visit.drafts.parts())
    }

    @Test fun chainsGoOnInAnyOrder() = runTest {
        val visit = Visit(TAB_UPLOAD)
        visit.make("The menu.")
        visit.addMore(then = TAB_SPEAK); visit.make("Customers order ahead.")
        visit.addMore(then = TAB_TYPE); visit.make("Pickup only.")
        visit.addMore(then = TAB_SPEAK); visit.make("And a loyalty card.")
        assertEquals(
            listOf("file" to "The menu.", "voice" to "Customers order ahead.", "text" to "Pickup only.", "voice" to "And a loyalty card."),
            visit.drafts.parts(),
        )
    }

    @Test fun aTabSwitchKeepsTheOrder() = runTest {
        // Before R-108: Speak "A", Type "B", Speak "C" saved "A C" then "B".
        val visit = Visit(TAB_SPEAK)
        visit.s = CaptureViewModel.heard(visit.s, com.raviga.app.data.audio.DictationEngine.Event.Utterance("A"))
        visit.switchTo(TAB_TYPE)
        visit.s = visit.s.copy(typed = "B")
        visit.switchTo(TAB_SPEAK)
        assertEquals("the Type tab starts empty next time", "", CaptureViewModel.leftTab(visit.s.copy(tab = TAB_TYPE, typed = "B")).typed)
        visit.make("C")
        assertEquals(listOf("voice" to "A", "text" to "B", "voice" to "C"), visit.drafts.parts())
    }

    @Test fun nothingCarriesIntoTheNextPart() {
        val reviewed = State(
            phase = Phase.TRANSCRIPT, tab = TAB_SPEAK, committed = listOf("One.", "Two."), consumed = 2,
            transcript = "One. Two.", typed = "typed before", elapsedSec = 40, savedNotes = 1,
        )
        val next = CaptureViewModel.afterAddMore(reviewed, null)
        assertEquals(Phase.CHOOSE, next.phase)
        assertEquals("", next.transcript)
        assertEquals("", next.typed)
        assertEquals(0, next.elapsedSec)
        assertEquals(2, next.savedNotes)
        assertFalse("no speech left for the next part", next.copy(phase = Phase.CAPTURE).hasSpeech)
        assertEquals(emptyList<String>() to null, CaptureViewModel.speakLines(next))
    }

    @Test fun eachPartSaysWhereItCameFrom() {
        assertEquals("Spoken", partSource("voice", null))
        assertEquals("Typed", partSource("text", null))
        assertEquals("From menu.pdf,", partSource("file", "menu.pdf"))
    }

    /** iOS's L2 run: 7 due with 0.8333 left read "You need 6.16 more", which leaves her short. Owed rounds up. */
    @Test fun whatIsOwedRoundsUp() {
        assertEquals("6.17", Money.creditsOwed(7 - 0.8333))
        assertEquals("20", Money.creditsOwed(20.0))
        assertEquals("6.16", Money.creditsOwed(6.16))
        assertEquals("float noise reads whole", "7", Money.creditsOwed(7.0000001))
    }
}
