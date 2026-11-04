package com.raviga.app.ui

import com.raviga.app.data.api.AwsDelivery
import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.Transfer
import com.raviga.app.ui.status.GoLiveLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as iOS GoLiveTests. */
class GoLiveLogicTest {

    private fun delivery(transfer: String = "initiated", aws: String = "not_requested", handover: String? = null) = Delivery(
        repoUrl = "https://github.com/raviga-builds/clinic/",
        handoverUrl = handover,
        transfer = Transfer(status = transfer),
        aws = AwsDelivery(status = aws),
    )

    @Test fun guideIsTheServerLinkElseHandoverInTheRepo() {
        assertEquals("https://github.com/raviga-builds/clinic/blob/HEAD/HANDOVER.md", delivery().handover)
        assertEquals("https://example.com/kt.pdf", delivery(handover = "https://example.com/kt.pdf").handover)
        assertNull(Delivery().handover)
    }

    @Test fun platformsFromTheBrief() {
        assertEquals(GoLiveLogic.Platforms(mobile = true, web = true), GoLiveLogic.platforms("- iPhone and Android apps\n- Admin web dashboard"))
        assertEquals(GoLiveLogic.Platforms(mobile = true, web = false), GoLiveLogic.platforms("- Android app"))
        assertEquals("nothing said: a web app", GoLiveLogic.Platforms(mobile = false, web = true), GoLiveLogic.platforms(""))
    }

    @Test fun stepsFollowTheDelivery() {
        var steps = GoLiveLogic.steps(
            delivery(), ProjectStatus.DELIVERED, "octocat", GoLiveLogic.Platforms(mobile = true, web = false),
            hasPaidServices = true,
        )
        assertEquals(listOf("github", "guide", "aws", "keys", "stores", "accept"), steps.map { it.id })
        assertFalse(steps[0].done)
        assertEquals("Transfer sent to @octocat. Accept it from the email GitHub sent you.", steps[0].detail)
        // R-105: the app runs in the client's own AWS from the start; the step opens the console.
        assertEquals("Your app is running in your AWS account", steps[2].title)
        assertEquals("no console link before it's deployed", null, steps[2].action)
        // R-103: no rounds; approve it or report what's wrong.
        assertEquals("Approve the delivery", steps.last().title)
        assertEquals("Or tell us what's wrong and we'll fix it and deliver again.", steps.last().detail)

        steps = GoLiveLogic.steps(
            delivery(transfer = "accepted", aws = "deployed"), ProjectStatus.ACCEPTED, null, GoLiveLogic.Platforms(mobile = false, web = true),
            hasPaidServices = false,
        )
        assertEquals(listOf("github", "guide", "aws", "domain", "accept"), steps.map { it.id })
        assertTrue(steps[0].done && steps[2].done && steps.last().done)
        // The delivered screen's Backend line: the server's statusLine as sent (v0.10.2); no team note.
        assertEquals("Running in your AWS account.", GoLiveLogic.awsLine(com.raviga.app.data.api.AwsDelivery("deployed", "Deployed with RavigaDeployRole", statusLine = "Running in your AWS account.")))
        assertEquals("Setting up in your AWS account.", GoLiveLogic.awsLine(com.raviga.app.data.api.AwsDelivery("pending")))
        val live = GoLiveLogic.steps(
            delivery(transfer = "accepted", aws = "deployed").let { it.copy(aws = it.aws?.copy(consoleUrl = "https://console.example/x")) },
            ProjectStatus.DELIVERED, null, GoLiveLogic.Platforms(mobile = false, web = true), hasPaidServices = false,
        )
        assertEquals("Open it in the AWS console.", live[2].detail)
        assertEquals(GoLiveLogic.Action.OpenConsole, live[2].action)
        assertEquals("Transferred to your GitHub account.", steps[0].detail)
    }

    /** R-103: a report's category in config's words; an unknown id reads as "Something else". */
    @Test fun reportCategoriesUseConfigLabels() {
        val categories = com.raviga.app.data.api.ReportConfig().categories
        assertEquals("Something doesn't work (a bug)", GoLiveLogic.reportLabel("bug", categories))
        assertEquals("It doesn't match the brief", GoLiveLogic.reportLabel("not_as_brief", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("new_kind", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("bug", emptyList()))
    }

    /** R-092 #28/#29 (iOS review, PM): no bare "@", and a failed transfer points to support (no "Try again" yet). */
    @Test fun transferLinesNeverReadBrokenAndAFailureGoesToSupport() {
        val pending = com.raviga.app.data.api.Transfer(status = "pending")
        assertEquals("We're sending the repository to your GitHub account. GitHub will email you to accept it.", GoLiveLogic.transferLine(pending, ""))
        assertEquals("We're sending the repository to @octocat. GitHub will email you to accept it.", GoLiveLogic.transferLine(pending, "octocat"))
        assertEquals(
            "The transfer didn't go through. Write to support and we'll sort it out.",
            GoLiveLogic.transferLine(com.raviga.app.data.api.Transfer(status = "failed", error = "GitHub said 422"), "octocat"),
        )
    }

    /** R-094 P2-11 (contract v0.9.7): the line names the account the transfer went to, not today's target. */
    @Test fun aTransferNamesTheAccountItWasSentTo() {
        val sent = com.raviga.app.data.api.Transfer(status = "initiated", githubUsername = "old-name")
        assertEquals("Transfer sent to @old-name. Accept it from the email GitHub sent you.", GoLiveLogic.transferLine(sent, "new-name"))
        val older = com.raviga.app.data.api.Transfer(status = "accepted", githubUsername = null)
        assertEquals("before v0.9.7: today's target", "Transferred to @new-name.", GoLiveLogic.transferLine(older, "new-name"))
    }

    @Test fun theNewFieldDecodes() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val t = json.decodeFromString(com.raviga.app.data.api.Transfer.serializer(), """{"status":"accepted","githubUsername":"octocat"}""")
        assertEquals("octocat", t.githubUsername)
        assertEquals(null, json.decodeFromString(com.raviga.app.data.api.Transfer.serializer(), """{"status":"awaiting_target","githubUsername":null}""").githubUsername)
    }
}
