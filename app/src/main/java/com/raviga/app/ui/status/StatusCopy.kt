package com.raviga.app.ui.status

import androidx.compose.ui.graphics.Color
import com.raviga.app.data.api.Grievance
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.ui.theme.Ink

/** The shared status vocabulary from DESIGN.md: same words on iOS, Android and in push copy. */
object StatusCopy {

    fun markColor(status: String): Color = when (status) {
        ProjectStatus.DRAFT, ProjectStatus.CANCELLED -> Ink.ash
        ProjectStatus.SUBMITTED, ProjectStatus.REVISION_REQUESTED -> Ink.amber
        ProjectStatus.APPROVED, ProjectStatus.BUILDING -> Ink.blue
        ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED -> Ink.moss
        ProjectStatus.REJECTED -> Ink.brick
        else -> Ink.ash
    }

    /** The chip on a project (contract v0.10.0 statuses; the line beside it is the server's statusLine). */
    fun detailTitle(status: String): String = when (status) {
        ProjectStatus.DRAFT -> "Draft"
        ProjectStatus.SUBMITTED -> "In review"
        ProjectStatus.APPROVED -> "Accepted"
        ProjectStatus.BUILDING -> "In build"
        ProjectStatus.DELIVERED -> "Delivered"
        ProjectStatus.REVISION_REQUESTED -> "Being fixed"
        ProjectStatus.ACCEPTED -> "Approved by you"
        ProjectStatus.REJECTED -> "Not accepted"
        ProjectStatus.CANCELLED -> "Cancelled"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /**
     * Contract v0.10.0 (R-103): a project's line is the server's statusLine, shown as it comes (it fills in credits and
     * dates); never composed here. Only a cache from before v0.10.0 lacks it, then the chip's words stand in.
     */
    fun rowLine(summary: ProjectSummary): String = summary.statusLine.ifBlank { detailTitle(summary.status) }

    fun rowLine(project: Project): String = project.statusLine.ifBlank { detailTitle(project.status) }

    /**
     * The statusLine as it comes, with a date kept on one line ("17\u00A0Nov", "10\u00A0Nov\u00A02027"): the words are the
     * server's, only the spaces inside a date stop it splitting across lines.
     */
    fun keepDatesTogether(line: String): String =
        line.replace(Regex("""\b(\d{1,2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b( \d{4})?""")) { m ->
            m.groupValues[1] + "\u00A0" + m.groupValues[2] + m.groupValues[3].replace(' ', '\u00A0')
        }

    /** Only when a push carries no body of its own (the server always sends one). */
    fun pushLine(status: String): String = when (status) {
        ProjectStatus.APPROVED -> "Accepted. Pay to start building."
        ProjectStatus.BUILDING -> "Paid. We're building it."
        ProjectStatus.DELIVERED -> "Delivered. Check it, then approve it or tell us what's wrong."
        ProjectStatus.REVISION_REQUESTED -> "We're fixing what you reported."
        ProjectStatus.REJECTED -> "Not accepted."
        ProjectStatus.CANCELLED -> "Cancelled."
        else -> "Your project was updated."
    }

    /** From `/config` legal.supportEmail; set by the container whenever config loads. */
    @Volatile var supportEmail: String = ""

    /**
     * The local-first promise in one paragraph (R-066 wording, founder 2026-10-30): "What we don't keep" on the consent
     * screens and on Privacy and data. Same words on iOS (`ServiceConfig.Retention.promise`).
     */
    const val LOCAL_FIRST_PROMISE =
        "Recordings and files never leave your phone. Drafts are stored only on your phone. To write or price a brief, " +
            "its text is sent to an AI service, used once, and not stored by us. When you submit, we keep the brief to " +
            "review and build it, and delete it when you accept the delivery."

    /** Under "Download my data" (her copy, 2026-11-03; same words as iOS). The zip carries drafts/ from [ExportBuilder]. */
    const val EXPORT_DRAFTS_CAPTION =
        "The download adds the drafts kept on this phone, in a drafts folder. Our servers don't keep your drafts."

    /** On a written brief: notes saved on the phone that the brief doesn't reflect yet (PM spec, 2026-11-02). */
    fun notesNotInBrief(count: Int): String = if (count == 1) "1 note not in the brief yet" else "$count notes not in the brief yet"

    fun addNotesToBrief(count: Int): String = if (count == 1) "Add it to the brief" else "Add them to the brief"

    /** R-055: the grievance route in one line, email and days only (consent screen and Privacy and data). */
    fun grievanceLine(grievance: Grievance): String? =
        if (grievance.email.isBlank()) null
        else "Questions or complaints: write to ${grievance.email}. We reply within ${grievance.responseDays} days."

    fun mistakeSuffix(): String = if (supportEmail.isBlank()) "" else " If you think this is a mistake, write to $supportEmail."

    /** Screening paused new descriptions (429 rate_limited, reason "paused"), as iOS: for how long, in plain words. */
    /**
     * R-092 #37 (iOS review, iOS 26900b8): cancel makes no draft, and "Your copy stays on this phone" only when there is
     * one (after a restore there may be none). The confirm says beforehand what the closed screen says after.
     */
    fun cancelConfirmLine(hasCopy: Boolean): String =
        // v0.10.0: cancel is only before payment, so nothing comes back; the brief goes.
        "Nothing was charged. The brief is deleted from our servers." + copyLine(hasCopy)

    /** The line under a closed project whose brief the server deleted. */
    fun deletedLine(day: String, hasCopy: Boolean): String =
        "We deleted this brief from our servers on $day." + copyLine(hasCopy)

    private fun copyLine(hasCopy: Boolean) = if (hasCopy) " Your copy stays on this phone." else ""

    fun pausedLine(retryAfterSec: Int?): String =
        "Several descriptions were turned down, so new ones are paused" +
            (retryAfterSec?.let { " for about ${hours(it)}" } ?: " for a day") +
            ". Write to support if you think this is a mistake."

    /** "40 minutes", "5 hours": iOS's DurationFormat.hours, rounded up to the hour past one hour. */
    fun hours(seconds: Int): String {
        if (seconds < 3600) {
            val minutes = (maxOf(60, seconds) / 60.0).let { Math.round(it).toInt() }
            return if (minutes == 1) "1 minute" else "$minutes minutes"
        }
        val whole = Math.ceil(seconds / 3600.0).toInt()
        return if (whole == 1) "1 hour" else "$whole hours"
    }

    /** The server's wording sometimes carries the appeal already; never say it twice. */
    fun hasAppeal(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return text.contains("think this is a mistake", ignoreCase = true) ||
            (supportEmail.isNotBlank() && text.contains(supportEmail, ignoreCase = true))
    }

    /**
     * A project's name. The server deletes the title when a project closes (contract v0.6),
     * so this phone's copy of the brief names it, else its reference does.
     */
    fun projectTitle(project: Project, localTitle: String?): String =
        project.title.ifBlank { localTitle.orEmpty() }.ifBlank { if (project.ref.isNotBlank()) "Project ${project.ref}" else "Untitled project" }

    /** Maps an API error code to the plain copy the design asks for. */
    fun errorLine(code: String, fallback: String?): String = when (code) {
        // The server's message is final copy, including where to appeal (backend owns that sentence).
        "content_rejected" -> fallback ?: "We can't take this on."
        "file_unsupported" -> fallback ?: "Raviga can read PDF, Word (.docx), text, Markdown and RTF files."
        "file_unreadable" -> fallback ?: "Couldn't read that file. If it's a scan or locked with a password, paste the important parts instead."
        "account_restricted" -> fallback ?: "Your account is on hold.${mistakeSuffix()}"
        "network" -> "Couldn't reach Raviga. Check your connection and try again."
        "decode" -> "Raviga sent something this version can't read. Try again later."
        "consent_required" -> "Allow AI processing before continuing."
        "insufficient_credits" -> fallback ?: "You need more credits to pay for this project."
        "version_conflict" -> "The brief changed elsewhere. Reload and try again."
        // The server's message says which case (expired, used, brief changed; contract 11 step 3), as on iOS.
        "quote_stale" -> fallback ?: "The brief changed since this quote. Get a new quote."
        // R-097 (the PM's copy, as iOS): each with its next step.
        "document_locked" -> "This brief is submitted and locked. Open the project to follow it."
        "github_user_not_found" -> "That GitHub username doesn't exist. Check the spelling and try again."
        "invalid_argument" -> fallback ?: "Something in the form isn't right."
        // R-097: the server's invalid_state (the screens that can meet it reload the project on it); the app's own
        // invalid_state lines (httpStatus 0, "Write the brief first.") keep their words.
        "invalid_state" -> fallback ?: "That step isn't available any more. The project has been refreshed."
        "active_projects" -> "Finish or cancel your active projects before deleting your data."
        "aws_role_not_ready" -> "AWS hasn't finished creating the role yet. Give it a minute and check again."
        "otp_invalid" -> "That recovery key doesn't match. Check it and try again."
        "link_code_invalid" -> "That link code is wrong or has expired."
        "upgrade_required" -> "Update Raviga from Google Play to continue."
        "rate_limited" -> "Too many requests. Wait a moment and try again."
        "ai_unavailable" -> "The AI is busy right now. Try again in a few minutes."   // R-074: also when its usage limit is hit
        // Gate line "every contract section 2 error has a plain message and a next step": iOS's words (11-03).
        "not_found" -> "That isn't on this account any more. Go back and refresh."
        "internal" -> "Something went wrong on our side. Try again in a moment."
        "conflict" -> "That was already sent. Refresh to see the latest."
        "permission_denied" -> "That isn't allowed for this account. Write to support if you think this is a mistake."
        "job_timeout" -> "This is taking longer than usual. Check back in a few minutes."
        // QA O6: a job whose result ran out (after the one automatic rerun): iOS's line, with the next step.
        "job_expired" -> "This is taking longer than usual. Try again."
        // R-083, iOS's line: a job the app was closed during, past its window or no longer on the server.
        "job_lost" -> "That stopped when the app closed. Try again."
        "unauthenticated" -> "This phone is no longer linked. Move your projects here with a recovery key."
        com.raviga.app.data.api.ApiException.BEFORE_CONSENT -> "Agree to the terms and privacy policy first."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}
