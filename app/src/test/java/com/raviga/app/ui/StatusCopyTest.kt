package com.raviga.app.ui

import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.theme.Ink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusCopyTest {

    private fun policy(message: String) = com.raviga.app.data.api.ApiException(
        "content_rejected", 422, message,
        kotlinx.serialization.json.buildJsonObject { put("kind", kotlinx.serialization.json.JsonPrimitive("policy")) },
    )

    @Test
    fun `policy refusals show the server message as is`() {
        StatusCopy.supportEmail = "support@example.com"
        assertEquals("Outside our policy.", policy("Outside our policy.").userLine())
        val withAppeal = "Outside our policy. If you think this is a mistake, write to support@example.com."
        assertEquals(withAppeal, policy(withAppeal).userLine())
        StatusCopy.supportEmail = ""
    }

    @Test
    fun `detail titles match the shared vocabulary`() {
        assertEquals("Awaiting review", StatusCopy.detailTitle(ProjectStatus.SUBMITTED))
        assertEquals("Comments from the team", StatusCopy.detailTitle(ProjectStatus.CHANGES_REQUESTED))
        assertEquals("In build", StatusCopy.detailTitle(ProjectStatus.APPROVED))
        assertEquals("Not accepted", StatusCopy.detailTitle(ProjectStatus.REJECTED))
    }

    @Test
    fun `mark colours follow the design table`() {
        assertEquals(Ink.ash, StatusCopy.markColor(ProjectStatus.DRAFT))
        assertEquals(Ink.amber, StatusCopy.markColor(ProjectStatus.SUBMITTED))
        assertEquals(Ink.blue, StatusCopy.markColor(ProjectStatus.APPROVED))
        assertEquals(Ink.moss, StatusCopy.markColor(ProjectStatus.DELIVERED))
        assertEquals(Ink.brick, StatusCopy.markColor(ProjectStatus.REJECTED))
    }

    @Test
    fun `row lines use the shared wording`() {
        val approved = ProjectSummary(id = "p", status = ProjectStatus.APPROVED, estimatedDeliveryDate = "2026-11-21")
        assertTrue(StatusCopy.rowLine(approved).startsWith("Approved, in build. Ready by "))
        assertEquals("Submitted, awaiting review", StatusCopy.rowLine(ProjectSummary(id = "p", status = ProjectStatus.SUBMITTED)))
        assertEquals("Cancelled, credits returned", StatusCopy.rowLine(ProjectSummary(id = "p", status = ProjectStatus.CANCELLED)))
    }

    /** Contract v0.8.1: the hourly job once moved updatedAt on RA-Q88NBG, so "Accepted on" read 17 Oct instead of 9 Oct. */
    @Test
    fun `accepted date comes from statusChangedAt, then history, then updatedAt`() {
        val accepted = "2026-10-09T11:45:06Z"
        val bumped = "2026-10-17T08:15:28Z"
        val expected = "Accepted on ${com.raviga.app.util.Time.shortDate(accepted)}"
        val summary = ProjectSummary(id = "p", status = ProjectStatus.ACCEPTED, updatedAt = bumped, statusChangedAt = accepted)
        assertEquals(expected, StatusCopy.rowLine(summary))
        // An older summary without the field falls back to updatedAt.
        assertEquals(expected, StatusCopy.rowLine(summary.copy(statusChangedAt = null, updatedAt = accepted)))
        // A full project without the field uses its last history entry.
        val project = com.raviga.app.data.api.Project(
            id = "p", status = ProjectStatus.ACCEPTED, updatedAt = bumped,
            history = listOf(
                com.raviga.app.data.api.HistoryEntry(ProjectStatus.DELIVERED, at = "2026-10-09T11:44:25Z"),
                com.raviga.app.data.api.HistoryEntry(ProjectStatus.ACCEPTED, at = accepted, by = "client"),
            ),
        )
        assertEquals(expected, StatusCopy.rowLine(project))
        assertEquals(expected, StatusCopy.rowLine(project.copy(statusChangedAt = accepted, history = emptyList())))
    }

    @Test
    fun `unknown error codes fall back to the server message`() {
        assertEquals("GitHub is unavailable", StatusCopy.errorLine("github_unavailable", "GitHub is unavailable"))
        assertEquals("Something went wrong. Try again.", StatusCopy.errorLine("github_unavailable", null))
        assertTrue(StatusCopy.errorLine("network", "ignored").startsWith("Couldn't reach Raviga"))
    }

    /** A written brief with notes it doesn't reflect yet (PM spec, 2026-11-02). */
    @Test
    fun `notes not in the brief read naturally for one and many`() {
        assertEquals("1 note not in the brief yet", StatusCopy.notesNotInBrief(1))
        assertEquals("3 notes not in the brief yet", StatusCopy.notesNotInBrief(3))
        assertEquals("Add it to the brief", StatusCopy.addNotesToBrief(1))
        assertEquals("Add them to the brief", StatusCopy.addNotesToBrief(2))
    }

    /** Gate: every contract section 2 error code has its own plain line (none falls to the generic one); iOS's words. */
    @Test fun everyContractErrorHasItsOwnLine() {
        val codes = listOf(
            "unauthenticated", "permission_denied", "consent_required", "not_found", "invalid_argument",
            "github_user_not_found", "invalid_state", "quote_stale", "insufficient_credits", "revision_limit_reached",
            "active_projects", "aws_role_not_ready", "link_code_invalid", "otp_invalid", "upgrade_required",
            "rate_limited", "content_rejected", "account_restricted", "ai_unavailable", "conflict", "internal",
        )
        val generic = StatusCopy.errorLine("no_such_code", null)
        for (code in codes) assertNotEquals(code, generic, StatusCopy.errorLine(code, null))
        assertEquals("Something went wrong on our side. Try again in a moment.", StatusCopy.errorLine("internal", "Internal Server Error"))
        assertEquals("That isn't on this account any more. Go back and refresh.", StatusCopy.errorLine("not_found", null))
        assertEquals("That was already sent. Refresh to see the latest.", StatusCopy.errorLine("conflict", null))
        assertEquals("That isn't allowed for this account.", StatusCopy.errorLine("permission_denied", null))
    }

    /** invalid_state, as iOS: the server's team-facing text is not shown; the app's own words are. */
    @Test fun invalidStateFromTheServerReadsPlainly() {
        val server = com.raviga.app.data.api.ApiException("invalid_state", 409, "Only a submitted project can be approved")
        assertEquals("That isn't possible at this stage.", server.userLine())
        val local = com.raviga.app.data.api.ApiException("invalid_state", message = "Write the brief first.")
        assertEquals("Write the brief first.", local.userLine())
        assertEquals("Couldn't save the draft", com.raviga.app.data.UserFacingException("Couldn't save the draft").userLine())
        assertEquals("Something went wrong. Try again.", IllegalStateException().userLine())
        // R-092 #27: a runtime's own text is never shown.
        assertEquals("Something went wrong. Try again.", IndexOutOfBoundsException("Index 3 out of bounds for length 2").userLine())
        assertEquals("Something went wrong. Try again.", java.io.IOException("No content provider: content://x").userLine())
    }

    /** QA O4 (as iOS): a screening pause says for how long. */
    @Test fun aPauseSaysForHowLong() {
        assertEquals(
            "Several descriptions were turned down, so new ones are paused for about 23 hours. Write to support if you think this is a mistake.",
            StatusCopy.pausedLine(82_000),
        )
        assertEquals("1 minute", StatusCopy.hours(20))
        assertEquals("40 minutes", StatusCopy.hours(2_400))
        assertEquals("1 hour", StatusCopy.hours(3_600))
        assertEquals("2 hours", StatusCopy.hours(3_601))
        assertEquals(
            "Several descriptions were turned down, so new ones are paused for a day. Write to support if you think this is a mistake.",
            StatusCopy.pausedLine(null),
        )
    }

    /** QA O6 (as iOS): an expired job says what to do, not that "the result expired before it was read". */
    @Test fun anExpiredJobSaysTryAgain() {
        val expired = com.raviga.app.data.api.ApiException("job_expired", message = "The result expired before it was read.")
        assertEquals("This is taking longer than usual. Try again.", expired.userLine())
    }

    /** R-092 #37: iOS's words (26900b8); no draft is promised, and the copy only when there is one. */
    @Test
    fun `cancel and closed lines say what really happens`() {
        assertEquals(
            "Your 48 credits come back to your balance and the brief is deleted from our servers. Your copy stays on this phone.",
            StatusCopy.cancelConfirmLine(48, hasCopy = true),
        )
        assertEquals("Your 1 credit comes back to your balance and the brief is deleted from our servers.", StatusCopy.cancelConfirmLine(1, hasCopy = false))
        assertEquals("The brief is deleted from our servers.", StatusCopy.cancelConfirmLine(0, hasCopy = false))
        assertEquals("We deleted this brief and its comments from our servers on 3 Nov.", StatusCopy.deletedLine("3 Nov", hasCopy = false))
        assertTrue(StatusCopy.deletedLine("3 Nov", hasCopy = true).endsWith(" Your copy stays on this phone."))
        assertTrue(listOf(StatusCopy.cancelConfirmLine(48, true), StatusCopy.deletedLine("3 Nov", true)).none { "draft" in it || "same brief" in it })
    }

    /** As iOS a04dc61: a single credit is never "1 credits". */
    @Test
    fun `one credit reads as one`() {
        assertEquals("1 credit", StatusCopy.credits(1))
        assertEquals("15 credits", StatusCopy.credits(15))
        assertEquals("0 credits", StatusCopy.credits(0))
    }
}
