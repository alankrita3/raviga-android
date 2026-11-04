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
        assertEquals("In review", StatusCopy.detailTitle(ProjectStatus.SUBMITTED))
        assertEquals("Accepted", StatusCopy.detailTitle(ProjectStatus.APPROVED))
        assertEquals("In build", StatusCopy.detailTitle(ProjectStatus.BUILDING))
        assertEquals("Approved by you", StatusCopy.detailTitle(ProjectStatus.ACCEPTED))
        assertEquals("Not accepted", StatusCopy.detailTitle(ProjectStatus.REJECTED))
    }

    /** v0.10.0: past the fair-use cap, /ai/draft's 429 carries the line to show, as it comes (iOS: no retry). */
    @Test
    fun `the brief cap shows the servers line`() {
        val day = com.raviga.app.data.api.ApiException(
            com.raviga.app.data.api.ApiException.RATE_LIMITED, 429, "You've reached today's limit for new briefs. Try again tomorrow.",
            details = kotlinx.serialization.json.buildJsonObject { put("limit", kotlinx.serialization.json.JsonPrimitive("briefs_per_day")); put("max", kotlinx.serialization.json.JsonPrimitive(5)) },
        )
        assertEquals("You've reached today's limit for new briefs. Try again tomorrow.", day.userLine())
        val plain = com.raviga.app.data.api.ApiException(com.raviga.app.data.api.ApiException.RATE_LIMITED, 429, "slow down")
        assertEquals("Too many requests. Wait a moment and try again.", plain.userLine())
    }

    /** The server's words, with a date kept on one line. */
    @Test
    fun `dates in a status line never split`() {
        assertEquals("Building and testing. Estimated delivery by 17\u00A0Nov.", StatusCopy.keepDatesTogether("Building and testing. Estimated delivery by 17 Nov."))
        assertEquals("by 10\u00A0Nov\u00A02027.", StatusCopy.keepDatesTogether("by 10 Nov 2027."))
        assertEquals("Accepted. Pay 30 credits to start building.", StatusCopy.keepDatesTogether("Accepted. Pay 30 credits to start building."))
    }

    /** Contract v0.10.0: the server's statusLine as it comes; the chip's words only for a cache from before it. */
    @Test
    fun `row lines are the servers status line`() {
        val summary = com.raviga.app.data.api.ProjectSummary(id = "pr_1", status = ProjectStatus.APPROVED, statusLine = "Accepted. Pay 30 credits to start building.")
        assertEquals("Accepted. Pay 30 credits to start building.", StatusCopy.rowLine(summary))
        assertEquals("In build", StatusCopy.rowLine(summary.copy(status = ProjectStatus.BUILDING, statusLine = "")))
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
            "invalid_state", "aws_required", "quote_stale", "insufficient_credits",
            "active_projects", "aws_role_not_ready", "link_code_invalid", "otp_invalid", "upgrade_required",
            "rate_limited", "content_rejected", "account_restricted", "ai_unavailable", "conflict", "internal",
        )
        val generic = StatusCopy.errorLine("no_such_code", null)
        for (code in codes) assertNotEquals(code, generic, StatusCopy.errorLine(code, null))
        assertEquals("Something went wrong on our side. Try again in a moment.", StatusCopy.errorLine("internal", "Internal Server Error"))
        assertEquals("That isn't on this account any more. Go back and refresh.", StatusCopy.errorLine("not_found", null))
        assertEquals("That was already sent. Refresh to see the latest.", StatusCopy.errorLine("conflict", null))
        assertEquals("That isn't allowed for this account. Write to support if you think this is a mistake.", StatusCopy.errorLine("permission_denied", null))
    }

    /** R-097 (the PM's copy, as iOS): next steps for four errors. */
    @Test fun fourErrorsSayWhatToDoNext() {
        assertEquals("This brief is submitted and locked. Open the project to follow it.", StatusCopy.errorLine("document_locked", null))
        assertEquals(
            "That step isn't available any more. The project has been refreshed.",
            com.raviga.app.data.api.ApiException("invalid_state", 409, "Only a submitted project can be cancelled").userLine(),
        )
        assertEquals("That isn't allowed for this account. Write to support if you think this is a mistake.", StatusCopy.errorLine("permission_denied", null))
    }

    /** invalid_state, as iOS: the server's team-facing text is not shown; the app's own words are. */
    @Test fun invalidStateFromTheServerReadsPlainly() {
        val server = com.raviga.app.data.api.ApiException("invalid_state", 409, "Only a submitted project can be approved")
        assertEquals("That step isn't available any more. The project has been refreshed.", server.userLine())
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

    /** R-092 #37, v0.10.0: cancel is only before payment, so nothing comes back; the copy only when there is one. */
    @Test
    fun `cancel and closed lines say what really happens`() {
        assertEquals("Nothing was charged. The brief is deleted from our servers. Your copy stays on this phone.", StatusCopy.cancelConfirmLine(hasCopy = true))
        assertEquals("Nothing was charged. The brief is deleted from our servers.", StatusCopy.cancelConfirmLine(hasCopy = false))
        assertEquals("We deleted this brief from our servers on 3 Nov.", StatusCopy.deletedLine("3 Nov", hasCopy = false))
        assertTrue(StatusCopy.deletedLine("3 Nov", hasCopy = true).endsWith(" Your copy stays on this phone."))
        assertTrue(listOf(StatusCopy.cancelConfirmLine(true), StatusCopy.deletedLine("3 Nov", true)).none { "draft" in it || "comments" in it })
    }

    /** As iOS a04dc61: a single credit is never "1 credits". */
    @Test
    fun `one credit reads as one`() {
        assertEquals("1 credit", com.raviga.app.util.Money.credits(1))
        assertEquals("15 credits", com.raviga.app.util.Money.credits(15))
        assertEquals("0 credits", com.raviga.app.util.Money.credits(0))
    }

    @Test
    fun exportCaptionIsHerWordsAsOnIos() {
        assertEquals(
            "The download adds the drafts kept on this phone, in a drafts folder. Our servers don't keep your drafts.",
            StatusCopy.EXPORT_DRAFTS_CAPTION,
        )
    }
}
