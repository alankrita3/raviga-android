package com.raviga.app.ui

import com.raviga.app.ui.nav.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-092 #41 (iOS review) and review #16 (Android): a project's brief, status and delivery go back to each other
 * when the one asked for is right underneath, rather than stacking.
 */
class BriefRouteTest {
    @Test fun theSameProjectsBriefUnderneathIsReused() {
        assertTrue(Routes.sameProjectScreen(Routes.DOCUMENT, "p_1", Routes.document("p_1")))
    }

    @Test fun anythingElseOpensTheBrief() {
        assertFalse("another project's brief", Routes.sameProjectScreen(Routes.DOCUMENT, "p_2", Routes.document("p_1")))
        assertFalse("from Home", Routes.sameProjectScreen(Routes.HOME, null, Routes.document("p_1")))
        assertFalse("nothing underneath (opened from a notification)", Routes.sameProjectScreen(null, null, Routes.document("p_1")))
    }

    /** Review 2026-11-03 (Android): a push for another project never takes over the screen on top (and its state). */
    @Test fun aPushReusesTheScreenOnTopOnlyForTheSameProject() {
        assertTrue("the same project's screen", Routes.reuseTopFor("pr_a", "pr_a"))
        assertFalse("another project's status on top", Routes.reuseTopFor("pr_a", "pr_b"))
        assertFalse("Home on top", Routes.reuseTopFor(null, "pr_b"))
    }

    @Test fun theBriefsCommentsLinkGoesBackToTheStatusItCameFrom() {
        assertTrue(Routes.sameProjectScreen(Routes.STATUS, "p_1", Routes.status("p_1")))
        assertFalse(Routes.sameProjectScreen(Routes.STATUS, "p_2", Routes.status("p_1")))
        assertFalse(Routes.sameProjectScreen(Routes.DOCUMENT, "p_1", Routes.status("p_1")))
    }

    /** Review #16: Status > View delivery > Status > View delivery... no longer adds two screens a round. */
    @Test fun statusAndDeliveryGoBackToEachOther() {
        assertTrue("Status from Delivery, opened from Status", Routes.sameProjectScreen(Routes.STATUS, "p_1", Routes.status("p_1")))
        assertTrue("View delivery from Status, opened from Delivery", Routes.sameProjectScreen(Routes.DELIVERY, "p_1", Routes.delivery("p_1")))
        assertTrue("View status on the brief, from a delivered project", Routes.sameProjectScreen(Routes.DELIVERY, "p_1", Routes.forProject("p_1", "delivered")))
        assertFalse("a different screen underneath", Routes.sameProjectScreen(Routes.STATUS, "p_1", Routes.delivery("p_1")))
        assertFalse("not a project screen", Routes.sameProjectScreen(Routes.STATUS, "p_1", Routes.HOME))
    }
}
