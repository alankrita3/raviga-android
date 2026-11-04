package com.raviga.app.ui

import com.raviga.app.data.api.AwsDelivery
import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.status.GoLiveLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as iOS GoLiveTests; contract v0.11.0 (R-106): no GitHub, the code and the guide are in the client's AWS. */
class GoLiveLogicTest {

    private fun delivery(aws: String = "pending", source: String? = SOURCE, handover: String? = GUIDE, console: String? = null) = Delivery(
        sourceUrl = source,
        handoverUrl = handover,
        aws = AwsDelivery(status = aws, consoleUrl = console),
    )

    @Test fun sourceAndGuideAreTheServersLinks() {
        assertEquals(SOURCE, delivery().source)
        assertEquals(GUIDE, delivery().handover)
        assertNull("no repository fallback any more", delivery(handover = null).handover)
        assertNull(Delivery().source)
    }

    @Test fun platformsFromTheBrief() {
        assertEquals(GoLiveLogic.Platforms(mobile = true, web = true), GoLiveLogic.platforms("- iPhone and Android apps\n- Admin web dashboard"))
        assertEquals(GoLiveLogic.Platforms(mobile = true, web = false), GoLiveLogic.platforms("- Android app"))
        assertEquals("nothing said: a web app", GoLiveLogic.Platforms(mobile = false, web = true), GoLiveLogic.platforms(""))
    }

    @Test fun stepsFollowTheDelivery() {
        var steps = GoLiveLogic.steps(delivery(), ProjectStatus.DELIVERED, GoLiveLogic.Platforms(mobile = true, web = false), hasPaidServices = true)
        assertEquals(listOf("source", "guide", "aws", "keys", "stores", "accept"), steps.map { it.id })
        // R-106 (PM-approved copy as iOS): the code and the guide are in the client's AWS; "Open in AWS".
        assertEquals("Your code and handover guide are in your AWS account", steps[0].title)
        assertEquals(
            "Sign in to your AWS console to open them. The guide shows how to put the code on GitHub or GitLab if you want it there.",
            steps[0].detail,
        )
        assertEquals(GoLiveLogic.Action.OpenSource, steps[0].action)
        assertEquals(GoLiveLogic.Action.OpenGuide, steps[1].action)
        // R-105: the app runs in the client's AWS; no console link until deployed.
        assertEquals("Your app is running in your AWS account", steps[2].title)
        assertEquals("Setting up in your AWS account.", steps[2].detail)
        assertNull(steps[2].action)
        assertFalse(steps[2].done)
        // R-103: no rounds; approve it or report what's wrong.
        assertEquals("Approve the delivery", steps.last().title)
        assertEquals("Or tell us what's wrong and we'll fix it and deliver again.", steps.last().detail)

        steps = GoLiveLogic.steps(
            delivery(aws = "deployed", console = "https://console.example/x"), ProjectStatus.ACCEPTED,
            GoLiveLogic.Platforms(mobile = false, web = true), hasPaidServices = false,
        )
        assertEquals(listOf("source", "guide", "aws", "domain", "accept"), steps.map { it.id })
        assertTrue(steps[2].done && steps.last().done)
        assertEquals("Open it in the AWS console.", steps[2].detail)
        assertEquals(GoLiveLogic.Action.OpenConsole, steps[2].action)

        // A delivery without a source link (not expected) offers no button rather than a dead one.
        assertNull(GoLiveLogic.steps(delivery(source = null), ProjectStatus.DELIVERED, GoLiveLogic.Platforms(false, true), false)[0].action)
    }

    @Test fun theBackendLineIsTheServers() {
        assertEquals("Running in your AWS account.", GoLiveLogic.awsLine(AwsDelivery("deployed", "Deployed with RavigaDeployRole", statusLine = "Running in your AWS account.")))
        assertEquals("Setting up in your AWS account.", GoLiveLogic.awsLine(AwsDelivery("pending")))
    }

    /** R-103: a report's category in config's words; an unknown id reads as "Something else". */
    @Test fun reportCategoriesUseConfigLabels() {
        val categories = com.raviga.app.data.api.ReportConfig().categories
        assertEquals("Something doesn't work (a bug)", GoLiveLogic.reportLabel("bug", categories))
        assertEquals("It doesn't match the brief", GoLiveLogic.reportLabel("not_as_brief", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("new_kind", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("bug", emptyList()))
    }

    /** v0.11.0: a delivery decodes without GitHub fields; old ones (repoUrl, transfer) are ignored. */
    @Test fun aV011DeliveryDecodes() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val d = json.decodeFromString(
            Delivery.serializer(),
            """{"sourceUrl":"$SOURCE","handoverUrl":"$GUIDE","deliveredAt":"a","note":"","aws":{"status":"deployed","statusLine":"Running in your AWS account.","consoleUrl":"https://c"},"acceptBy":"b",
               "repoUrl":"https://github.com/x/y","transfer":{"status":"accepted"}}""",
        )
        assertEquals(SOURCE, d.source)
        assertEquals("https://c", d.aws?.consoleUrl)
    }

    private companion object {
        const val SOURCE = "https://s3.console.aws.amazon.com/s3/buckets/raviga-delivery-clinic?region=ap-south-1"
        const val GUIDE = "https://s3.console.aws.amazon.com/s3/object/raviga-delivery-clinic?region=ap-south-1&prefix=HANDOVER.md"
    }
}
