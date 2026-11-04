package com.raviga.app.data.audio

import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Phase
import com.raviga.app.ui.capture.CaptureViewModel.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-075 (iOS lost words across pauses): the recogniser ends a session at every pause and the next one starts; every
 * utterance must stay in the note, in order, across pauses and "Record more". Only the live partial is replaced.
 */
class SpeechAcrossPausesTest {

    /** Feeds recogniser callbacks the way DictationEngine.listen does, into the Capture screen's state. */
    private class Mic {
        val session = DictationEngine.SessionText()
        var state = State(tab = CaptureViewModel.TAB_SPEAK)
        private fun take(event: DictationEngine.Event?) { if (event != null) state = CaptureViewModel.heard(state, event) }
        fun partial(text: String) = take(session.partial(text))
        fun result(text: String?) = take(session.result(text))
        fun error() = take(session.ended())
    }

    @Test fun everyUtteranceIsKeptInOrderAcrossPauses() {
        val mic = Mic()
        // Session 1, then a pause.
        mic.partial("I want"); mic.partial("I want a bakery app")
        assertEquals("I want a bakery app", mic.state.partial)
        mic.result("I want a bakery app")
        // Session 2: its partials replace only the live line, never session 1's text.
        mic.partial("customers"); mic.partial("customers pre-order cakes")
        assertEquals(listOf("I want a bakery app"), mic.state.committed)
        mic.result("customers pre-order cakes")
        // Session 3 ends on an error after words were heard: they're kept.
        mic.partial("and pay"); mic.error()
        // Session 4 gives an empty result after a partial: the partial is kept.
        mic.partial("at the counter"); mic.result("")
        // Session 5: silence only, nothing added.
        mic.error(); mic.result(null)
        assertEquals(listOf("I want a bakery app", "customers pre-order cakes", "and pay", "at the counter"), mic.state.committed)
        assertEquals("", mic.state.partial)
    }

    @Test fun recordMoreAddsAfterTheReviewedText() {
        val mic = Mic()
        mic.partial("A salon booking app"); mic.result("A salon booking app")
        mic.partial("with reminders"); mic.result("with reminders")
        // Done: everything so far goes under review.
        var consumed = 0
        var review = CaptureViewModel.spokenReview("", mic.state.committed, consumed)
        consumed = mic.state.committed.size
        assertEquals("A salon booking app with reminders", review)
        // R-108: "Add more", Speak again: two more sessions with a pause between make the next part on their own.
        mic.state = mic.state.copy(consumed = consumed, transcript = "")
        mic.partial("and a loyalty card"); mic.result("and a loyalty card")
        // While speaking, what's saved is this part's words only (autosave).
        assertEquals("and a loyalty card", CaptureViewModel.liveText(mic.state))
        mic.partial("for regulars"); mic.error()
        review = CaptureViewModel.spokenReview("", mic.state.committed, consumed)
        assertEquals("and a loyalty card for regulars", review)
    }

    @Test fun aSessionWithNoWordsAddsNothing() {
        val session = DictationEngine.SessionText()
        assertNull(session.ended())
        assertNull(session.result("  "))
        assertNull(session.partial(""))
        // The words a session showed are kept once, not again by the next session's end.
        session.partial("hello")
        assertEquals("hello", session.result(null)?.text)
        assertNull(session.ended())
    }

    @Test fun theReviewShowsWhatWasSpoken() {
        val s = State(phase = Phase.TRANSCRIPT, tab = CaptureViewModel.TAB_SPEAK, transcript = "One. Two.")
        assertEquals("One. Two.", CaptureViewModel.liveText(s))
    }
}
