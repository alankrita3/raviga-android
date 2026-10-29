package com.raviga.app.ui.onboarding

import androidx.navigation.NavController
import com.raviga.app.ui.nav.Routes

/**
 * R-056 (founder, 2026-10-29): the "Before you start" consent shows once, the first time the client taps Type, Speak,
 * Upload, Restore or Buy credits, before they enter anything; after Agree the app carries on into that action.
 */
object ConsentGate {
    /** Where an entry point goes: [target] once the person has agreed, the consent screen (then [target]) before. */
    fun route(agreed: Boolean, target: String): String = if (agreed) target else Routes.consentFirst(target)
}

fun NavController.navigateAfterConsent(agreed: Boolean, target: String) = navigate(ConsentGate.route(agreed, target))
