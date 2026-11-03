package com.raviga.app.ui.account

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.DeviceSummary
import com.raviga.app.data.api.Me

/** R-079 rules shared by Move to another phone, Recovery email and the Protect your credits sheet (iOS's words). */
object RecoveryEmailLogic {
    /** Something@somewhere.tld with no spaces: enough to send a code; the code proves the rest. */
    fun looksLikeEmail(text: String): Boolean {
        val trimmed = text.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0) return false
        return '.' in trimmed.substring(at + 1) && ' ' !in trimmed
    }

    /** The 6 digits typed, whatever else came with them (a pasted "123 456"). */
    fun digits(text: String): String = text.filter { it.isDigit() }
}

/**
 * Wrong 6-digit codes: the server allows 5 tries per code (contract 3.3), so after the fifth the screen asks for a new
 * one. The same rule for adding an email and for getting back in with one.
 */
data class OtpAttempts(val wrong: Int = 0) {

    sealed interface Outcome {
        data class TryAgain(val message: String) : Outcome
        data class NewCode(val message: String) : Outcome
        data object Other : Outcome
    }

    /** What a failed confirm means, and the count after it. */
    fun failed(error: Throwable): Pair<OtpAttempts, Outcome> = when ((error as? ApiException)?.code) {
        ApiException.OTP_INVALID -> {
            val next = OtpAttempts(wrong + 1)
            next to if (next.wrong >= LIMIT) Outcome.NewCode(NEW_CODE) else Outcome.TryAgain(TRY_AGAIN)
        }
        ApiException.RATE_LIMITED -> OtpAttempts(LIMIT) to Outcome.NewCode(NEW_CODE)
        else -> this to Outcome.Other
    }

    companion object {
        const val LIMIT = 5
        const val TRY_AGAIN = "That code didn't match. Try again."
        const val NEW_CODE = "Too many tries. Ask for a new code."
    }
}

/** A recovery key for display: the prefix stays readable, every character after it a dot, dashes kept. */
object RecoveryKeyFormat {
    fun mask(key: String): String {
        val underscore = key.indexOf('_')
        val prefix = if (underscore >= 0) key.substring(0, underscore + 1) else ""
        return prefix + key.substring(prefix.length).map { if (it == '-') '-' else '•' }.joinToString("")
    }
}

/** "Protect your credits": after a purchase that added credits, once per install, only with no confirmed email. */
object RecoveryEmailPrompt {
    fun shouldOffer(me: Me?, alreadyShown: Boolean): Boolean = !alreadyShown && me?.recoveryEmail?.verified != true
}

object PhoneNames {
    /** "This phone", else the model the phone reported, else its platform ("Ios" never: "iPhone"). */
    fun of(device: DeviceSummary): String = when {
        device.isCurrent -> "This phone"
        !device.deviceModel.isNullOrBlank() -> device.deviceModel
        device.platform.equals("ios", ignoreCase = true) -> "iPhone"
        device.platform.equals("android", ignoreCase = true) -> "Android phone"
        else -> device.platform.replaceFirstChar { it.uppercase() }
    }
}

/** R-092 #16 (iOS review), iOS's words (37bb4f5): what a restore over this phone's own account costs. */
object RestoreWarning {
    fun text(hasAccount: Boolean): String =
        "This phone will show the other phone's projects and credits instead of its own." +
            if (hasAccount) " This phone's current account stays reachable only with its recovery key or email." else ""
}
