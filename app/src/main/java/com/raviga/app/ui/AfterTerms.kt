package com.raviga.app.ui

import com.raviga.app.data.api.ApiException

/** The line a screen shows when its action was refused because the terms changed (the sheet comes up over it). */
const val TERMS_REFUSED_LINE = "Agree to the updated terms to continue."

/** `403 consent_required` naming the terms or privacy policy: the versions moved on while the app was open. */
fun Throwable.isTermsRefusal(): Boolean =
    this is ApiException && code == ApiException.CONSENT_REQUIRED && detailStrings("missing").any { it == "terms" || it == "privacy" }

/**
 * R-149 (the PM, after the stale-terms fix): an action refused for out-of-date terms carries on once they're agreed,
 * without a manual Try again. A screen hands its action over with [refused]; [agreed] (on
 * SessionRepository.legalAccepted) runs it once. A refusal of anything else isn't kept.
 */
class AfterTerms {
    private var pending: (() -> Unit)? = null

    /** True when [e] is the terms refusal; [again] then runs on [agreed]. */
    fun refused(e: Throwable, again: () -> Unit): Boolean = e.isTermsRefusal().also { if (it) pending = again }

    fun agreed() {
        val run = pending
        pending = null
        run?.invoke()
    }

    companion object {
        /** A screen's error line once the terms are agreed: the terms line goes, anything else stays. */
        fun clearedLine(error: String?): String? = error?.takeIf { it != TERMS_REFUSED_LINE }
    }
}
