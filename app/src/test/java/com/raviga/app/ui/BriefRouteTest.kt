package com.raviga.app.ui

import com.raviga.app.ui.nav.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-092 #41 (iOS review): Edit and resubmit goes back to the brief it came from rather than stacking a second one. */
class BriefRouteTest {
    @Test fun theSameProjectsBriefUnderneathIsReused() {
        assertTrue(Routes.isBriefOf(Routes.DOCUMENT, "p_1", "p_1"))
    }

    @Test fun anythingElseOpensTheBrief() {
        assertFalse("another project's brief", Routes.isBriefOf(Routes.DOCUMENT, "p_2", "p_1"))
        assertFalse("from Home", Routes.isBriefOf(Routes.HOME, null, "p_1"))
        assertFalse("nothing underneath (opened from a notification)", Routes.isBriefOf(null, null, "p_1"))
    }

    @Test fun theBriefsCommentsLinkGoesBackToTheStatusItCameFrom() {
        assertTrue(Routes.isStatusOf(Routes.STATUS, "p_1", "p_1"))
        assertFalse(Routes.isStatusOf(Routes.STATUS, "p_2", "p_1"))
        assertFalse(Routes.isStatusOf(Routes.DOCUMENT, "p_1", "p_1"))
    }
}
