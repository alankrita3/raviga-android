package com.raviga.app.ui

import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ProjectSummary
import com.raviga.app.ui.status.StatusCopy
import com.raviga.app.ui.theme.Ink
import org.junit.Assert.assertEquals
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
        assertEquals(Ink.teal, StatusCopy.markColor(ProjectStatus.APPROVED))
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
}
