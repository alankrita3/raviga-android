package com.raviga.app.ui

import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.onboarding.NotificationAsk
import com.raviga.app.ui.status.StatusCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** R-074: "Stay in the loop" shows once, at the end of onboarding, only where Android asks at all. */
class NotificationAskTest {

    @Test
    fun `asks once on Android 13 and later, never when it is settled`() {
        assertTrue(NotificationAsk.shouldAsk(sdk = 33, granted = false, askedBefore = false))
        assertTrue(NotificationAsk.shouldAsk(sdk = 35, granted = false, askedBefore = false))
        assertFalse("granted already", NotificationAsk.shouldAsk(sdk = 34, granted = true, askedBefore = false))
        assertFalse("asked before, here or in Settings", NotificationAsk.shouldAsk(sdk = 34, granted = false, askedBefore = true))
        // Android 12 and earlier: notifications are on without a prompt (the OnePlus 7).
        assertFalse(NotificationAsk.shouldAsk(sdk = 31, granted = true, askedBefore = false))
        assertFalse(NotificationAsk.shouldAsk(sdk = 32, granted = false, askedBefore = false))
    }

    @Test
    fun `the prompt carries on to where onboarding was going`() {
        for (next in listOf(Routes.HOME, Routes.TERMS)) {
            val route = Routes.notifyPrompt(next)
            assertTrue(route, route.startsWith("notify?next="))
            assertEquals(next, URLDecoder.decode(route.substringAfter("notify?next="), "UTF-8"))
        }
    }

    @Test
    fun `the AI busy line says a few minutes`() {
        assertEquals("The AI is busy right now. Try again in a few minutes.", StatusCopy.errorLine("ai_unavailable", "anything"))
    }
}
