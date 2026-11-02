package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
import com.raviga.app.ui.status.StatusCopy

/** The one line of copy a failure gets, by error code. */
fun Throwable.userLine(): String = when (this) {
    is ApiException -> {
        // The server's message, when it sent one (otherwise the exception carries the bare code).
        val server = message?.takeIf { it.isNotBlank() && it != code }
        when {
            // The terms changed: the app shows them again; the line just says why the action stopped.
            code == ApiException.CONSENT_REQUIRED && detailStrings("missing").any { it == "terms" || it == "privacy" } ->
                "Agree to the updated terms to continue."
            // A paused account (QA O4, iOS's words): how long, from details.retryAfterSec.
            code == ApiException.RATE_LIMITED && detailString("reason") == "paused" -> StatusCopy.pausedLine(detailInt("retryAfterSec"))
            // The server's invalid_state text is written for the team ("Only a submitted project can be approved"):
            // the plain line instead, as iOS. The app's own (httpStatus 0, "Write the brief first.") keep their words.
            code == ApiException.INVALID_STATE && httpStatus != 0 -> StatusCopy.errorLine(code, null)
            else -> StatusCopy.errorLine(code, server)
        }
    }
    // Not from the server: the app's own words when it wrote some ("Couldn't save the draft"), else the plain line.
    else -> message?.takeIf { it.isNotBlank() } ?: StatusCopy.errorLine("unknown", null)
}

fun Throwable.apiCode(): String? = (this as? ApiException)?.code
