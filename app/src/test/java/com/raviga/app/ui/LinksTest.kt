package com.raviga.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R-092 #23 (as iOS): only web addresses open in the in-app browser. */
class LinksTest {
    @Test fun onlyWebAddressesOpen() {
        assertEquals("https://legal.ravigaapps.com/terms-of-use", webUrl("https://legal.ravigaapps.com/terms-of-use"))
        assertEquals("http://example.com/a", webUrl(" http://example.com/a "))
        assertEquals("https://legal.ravigaapps.com/privacy-policy", webUrl("legal.ravigaapps.com/privacy-policy"))
        assertNull(webUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(webUrl("javascript:alert(1)"))
        assertNull(webUrl("file:///sdcard/x"))
        assertNull(webUrl("https://"))
        assertNull(webUrl(""))
        assertNull(webUrl("not a url"))
    }

    /** Review 2026-11-03 (Android): the toolbar was black (0xFF000000) on the OnePlus; it is the paper #F6F3EE. */
    @Test fun theBrowserToolbarIsPaper() {
        assertEquals(0xFFF6F3EE.toInt(), TAB_TOOLBAR)
    }
}
