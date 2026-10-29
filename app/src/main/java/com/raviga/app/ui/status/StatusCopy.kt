package com.raviga.app.ui.status

import androidx.compose.ui.graphics.Color
import com.raviga.app.data.api.Grievance
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.ui.theme.Ink
import com.raviga.app.util.Time

/** The shared status vocabulary from DESIGN.md: same words on iOS, Android and in push copy. */
object StatusCopy {

    fun markColor(status: String): Color = when (status) {
        ProjectStatus.DRAFT, ProjectStatus.CANCELLED -> Ink.ash
        ProjectStatus.SUBMITTED, ProjectStatus.CHANGES_REQUESTED, ProjectStatus.REVISION_REQUESTED -> Ink.amber
        ProjectStatus.APPROVED -> Ink.teal
        ProjectStatus.DELIVERED, ProjectStatus.ACCEPTED -> Ink.moss
        ProjectStatus.REJECTED -> Ink.brick
        else -> Ink.ash
    }

    fun detailTitle(status: String): String = when (status) {
        ProjectStatus.DRAFT -> "Draft"
        ProjectStatus.SUBMITTED -> "Awaiting review"
        ProjectStatus.CHANGES_REQUESTED -> "Comments from the team"
        ProjectStatus.APPROVED -> "In build"
        ProjectStatus.DELIVERED -> "Delivered"
        ProjectStatus.REVISION_REQUESTED -> "Revision in progress"
        ProjectStatus.ACCEPTED -> "Accepted"
        ProjectStatus.REJECTED -> "Not accepted"
        ProjectStatus.CANCELLED -> "Cancelled"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    // Status dates come from statusChangedAt (contract v0.8.1); older shapes fall back to the last history entry, then updatedAt.
    fun rowLine(summary: ProjectSummary): String =
        rowLine(summary.status, summary.updatedAt, summary.estimatedDeliveryDate, summary.statusChangedAt ?: summary.updatedAt)

    fun rowLine(project: Project): String = rowLine(
        project.status, project.updatedAt, project.timeline?.estimatedDeliveryDate,
        project.statusChangedAt ?: project.history.lastOrNull()?.at ?: project.updatedAt,
    )

    fun rowLine(status: String, updatedAt: String?, estimatedDeliveryDate: String?, statusChangedAt: String? = updatedAt): String = when (status) {
        ProjectStatus.DRAFT -> "Draft, edited ${Time.relative(updatedAt)}"
        ProjectStatus.SUBMITTED -> "Submitted, awaiting review"
        ProjectStatus.CHANGES_REQUESTED -> "The team left comments"
        ProjectStatus.APPROVED -> if (estimatedDeliveryDate != null) "Approved, in build. Ready by ${Time.shortDate(estimatedDeliveryDate)}" else "Approved, in build"
        ProjectStatus.DELIVERED -> "Delivered, review the repo"
        ProjectStatus.REVISION_REQUESTED -> "Revision requested"
        ProjectStatus.ACCEPTED -> "Accepted on ${Time.shortDate(statusChangedAt)}"
        ProjectStatus.REJECTED -> "Not accepted, credits returned"
        ProjectStatus.CANCELLED -> "Cancelled, credits returned"
        else -> detailTitle(status)
    }

    fun pushLine(status: String): String = when (status) {
        ProjectStatus.CHANGES_REQUESTED -> "The team left comments on your brief."
        ProjectStatus.APPROVED -> "Approved. We have started building."
        ProjectStatus.DELIVERED -> "Your code is ready. Review the repo."
        ProjectStatus.REJECTED -> "Not accepted. Your credits have been returned."
        ProjectStatus.ACCEPTED -> "Accepted. Thank you."
        else -> "Your project was updated."
    }

    /** From `/config` legal.supportEmail; set by the container whenever config loads. */
    @Volatile var supportEmail: String = ""

    /** R-055: the grievance route in one line, email and days only (consent screen and Privacy and data). */
    fun grievanceLine(grievance: Grievance): String? =
        if (grievance.email.isBlank()) null
        else "Questions or complaints: write to ${grievance.email}. We reply within ${grievance.responseDays} days."

    fun mistakeSuffix(): String = if (supportEmail.isBlank()) "" else " If you think this is a mistake, write to $supportEmail."

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
        "insufficient_credits" -> fallback ?: "You need more credits to submit."
        "version_conflict" -> "The brief changed elsewhere. Reload and try again."
        "quote_stale" -> "The brief changed since this quote. Get a new quote."
        "document_locked" -> "This brief is locked because it has been submitted."
        "github_user_not_found" -> "No GitHub user with that name. Check the spelling."
        "invalid_argument" -> fallback ?: "Something in the form isn't right."
        "invalid_state" -> fallback ?: "That isn't possible at this stage."
        "revision_limit_reached" -> "Your included revision rounds are used up. Email us and we can add more."
        "active_projects" -> "Finish or cancel your active projects before deleting your data."
        "aws_role_not_ready" -> "AWS hasn't finished creating the role yet. Give it a minute and check again."
        "otp_invalid" -> "That recovery key doesn't match. Check it and try again."
        "upgrade_required" -> "Update Raviga from the Play Store to continue."
        "rate_limited" -> "Too many requests. Wait a moment and try again."
        "ai_unavailable" -> "The AI is busy right now. Try again in a moment."
        "not_found" -> "That no longer exists."
        "job_timeout" -> "This is taking longer than usual. Check back in a few minutes."
        "unauthenticated" -> "This phone is no longer linked. Move your projects here with a recovery key."
        com.raviga.app.data.api.ApiException.BEFORE_CONSENT -> "Agree to the terms and privacy policy first."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "Something went wrong. Try again."
    }
}
