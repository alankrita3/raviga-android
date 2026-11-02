package com.raviga.app.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA O7: /config's minAppVersion above this build shows the update screen (as iOS). */
class AppVersionTest {
    @Test fun comparesNumbersNotText() {
        assertTrue(AppVersion.isBelow("1.0.0", "1.0.1"))
        assertTrue(AppVersion.isBelow("1.9.0", "1.10.0"))
        assertTrue(AppVersion.isBelow("1.0", "1.0.1"))
        assertFalse(AppVersion.isBelow("1.10.0", "1.9.9"))
        assertFalse(AppVersion.isBelow("1.0.0", "1.0.0"))
        assertFalse(AppVersion.isBelow("1.0.0", "1.0"))
    }

    @Test fun ignoresSuffixesAndAMissingMinimum() {
        assertFalse(AppVersion.isBelow("1.0.0-debug", "1.0.0"))
        assertTrue(AppVersion.isBelow("1.0.0-debug", "1.0.1"))
        assertFalse(AppVersion.isBelow("1.0.0", null))
        assertFalse(AppVersion.isBelow("1.0.0", ""))
    }
}
