package com.raviga.app.ui

import com.raviga.app.ui.nav.NavGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Review #17/#18 (Android): a screen navigates only while it is the top of the back stack. */
class NavGuardTest {
    @Test fun theTopScreenMayNavigate() = assertTrue(NavGuard.isOnTop("e1", "e1"))

    @Test fun aSecondTapOrALateAnswerAfterLeavingDoesNothing() {
        assertFalse("the first tap already opened the next screen", NavGuard.isOnTop("e1", "e2"))
        assertFalse("Back already popped this screen", NavGuard.isOnTop("e1", "e0"))
    }

    @Test fun outsideANavHostNothingIsGuarded() = assertTrue(NavGuard.isOnTop(null, "e2"))
}
