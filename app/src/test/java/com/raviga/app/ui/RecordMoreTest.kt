package com.raviga.app.ui

import com.raviga.app.ui.capture.CaptureViewModel
import com.raviga.app.ui.capture.CaptureViewModel.Companion.TAB_SPEAK
import com.raviga.app.ui.capture.CaptureViewModel.Phase
import com.raviga.app.ui.capture.CaptureViewModel.State
import com.raviga.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Her bug on the iPhone 16 (iOS de0be16, 2026-11-04): after Stop, review, then Record more, what she had said showed
 * twice. Android saved it once already; the screen showed every take's raw words, not the reviewed text. Now as iOS.
 */
class RecordMoreTest {
    // One take, reviewed and edited ("salon" fixed), then Record more with a new take under way.
    private val more = State(
        tab = TAB_SPEAK, phase = Phase.CAPTURE, continuing = true, consumed = 2,
        transcript = "A booking app for a salon. Clients pick a stylist.",
        committed = listOf("A booking app for a saloon.", "Clients pick a stylist.", "And pay a deposit."),
        partial = "by card",
    )

    @Test fun recordingMoreShowsTheReviewedTextThenOnlyTheNewTake() {
        val (earlier, latest) = CaptureViewModel.speakLines(more)
        assertEquals(listOf("A booking app for a salon. Clients pick a stylist.", "And pay a deposit."), earlier)
        assertEquals("by card", latest)
    }

    @Test fun beforeTheNewTakeSaysAnythingOnlyTheReviewedTextShows() {
        val (earlier, latest) = CaptureViewModel.speakLines(more.copy(committed = more.committed.take(2), partial = ""))
        assertEquals(listOf("A booking app for a salon. Clients pick a stylist."), earlier)
        assertEquals(null, latest)
    }

    @Test fun theFirstTakeShowsAsBefore() {
        val first = State(tab = TAB_SPEAK, phase = Phase.CAPTURE, committed = listOf("One.", "Two."))
        assertEquals(listOf("One.") to "Two.", CaptureViewModel.speakLines(first))
    }

    @Test fun whatIsSavedHoldsTheFirstTakeOnce() {
        assertEquals(
            "A booking app for a salon. Clients pick a stylist. And pay a deposit. by card",
            CaptureViewModel.liveText(more, consumedUtterances = 2),
        )
    }

    /** iOS's L2 run: 7 due with 0.8333 left read "You need 6.16 more", which leaves her short. Owed rounds up. */
    @Test fun whatIsOwedRoundsUp() {
        assertEquals("6.17", Money.creditsOwed(7 - 0.8333))
        assertEquals("20", Money.creditsOwed(20.0))
        assertEquals("6.16", Money.creditsOwed(6.16))
        assertEquals("float noise reads whole", "7", Money.creditsOwed(7.0000001))
    }
}
