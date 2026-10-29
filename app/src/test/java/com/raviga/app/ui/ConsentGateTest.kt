package com.raviga.app.ui

import com.raviga.app.data.api.ConsentGuard
import com.raviga.app.ui.nav.Routes
import com.raviga.app.ui.onboarding.ConsentGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** R-056: Type, Speak, Upload, Restore and Buy credits show the first consent once, then never again. */
class ConsentGateTest {

    private val entryPoints = listOf(
        Routes.capture(tab = "type"),
        Routes.capture(tab = "speak"),
        Routes.capture(tab = "upload"),
        Routes.RECOVERY,
        Routes.creditsBuy("credits_25"),
    )

    @Test
    fun `each entry point gates before Agree and carries on to its own action after`() {
        for (target in entryPoints) {
            val route = ConsentGate.route(agreed = false, target = target)
            assertTrue(route, route.startsWith("consent?next="))
            assertEquals(target, URLDecoder.decode(route.substringAfter("consent?next="), "UTF-8"))
        }
    }

    @Test
    fun `once agreed no entry point gates again`() {
        val guard = ConsentGuard()   // what SessionRepository.agree() flips, and what the screens read
        assertTrue(entryPoints.all { ConsentGate.route(guard.agreed, it) != it })
        guard.agreed = true
        for (target in entryPoints) assertEquals(target, ConsentGate.route(guard.agreed, target))
    }
}
