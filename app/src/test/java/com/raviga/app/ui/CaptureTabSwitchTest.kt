package com.raviga.app.ui

import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_SPEAK
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_TYPE
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_UPLOAD
import com.raviga.app.ui.capture.CaptureViewModel.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Second review 2026-11-03 (Android): the autosave runs up to half a second after the last change and saves the tab on
 * screen then. Switching to Upload inside that half second left nothing to save (Upload has no live text), so the last
 * words typed or said were dropped. OnePlus: "RV2 first part a flower shop app", then " and TAILWORDS lost" and Upload
 * at once, Back: the note was only the first part. The tab being left is now saved at the switch.
 */
class CaptureTabSwitchTest {
    @Test fun whatTheDeferredSaveWouldSeeAfterTheSwitchIsNothing() {
        val typed = State(tab = TAB_TYPE, typed = "a flower shop app and the last words")
        assertNull("why the words were lost", CaptureViewModel.liveText(typed.copy(tab = TAB_UPLOAD), 0))
    }

    @Test fun leavingTheTypeTabSavesWhatWasTyped() {
        val typed = State(tab = TAB_TYPE, typed = "a flower shop app and the last words")
        assertEquals("a flower shop app and the last words", CaptureViewModel.leavingTabText(typed, 0, TAB_UPLOAD))
        assertEquals("to Speak too", "a flower shop app and the last words", CaptureViewModel.leavingTabText(typed, 0, TAB_SPEAK))
    }

    @Test fun leavingTheSpeakTabSavesWhatWasHeard() {
        // stopListening() has already turned the live partial into a committed utterance.
        val heard = State(tab = TAB_SPEAK, committed = listOf("A flower shop app.", "With delivery slots."))
        assertEquals("A flower shop app. With delivery slots.", CaptureViewModel.leavingTabText(heard, 0, TAB_UPLOAD))
        assertEquals("only what this note hasn't used", "With delivery slots.", CaptureViewModel.leavingTabText(heard, 1, TAB_UPLOAD))
    }

    @Test fun nothingToSaveForTheSameTabOrFromUpload() {
        val typed = State(tab = TAB_TYPE, typed = "words")
        assertNull("tapping the tab already open", CaptureViewModel.leavingTabText(typed, 0, TAB_TYPE))
        assertNull("Upload has no live text", CaptureViewModel.leavingTabText(State(tab = TAB_UPLOAD), 0, TAB_TYPE))
    }
}
