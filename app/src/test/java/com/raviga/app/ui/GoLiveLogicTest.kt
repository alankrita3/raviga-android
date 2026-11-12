package com.raviga.app.ui

import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.Handover
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.ui.status.DeliveryCopy
import com.raviga.app.ui.status.GoLiveLogic
import com.raviga.app.ui.status.GoLiveLogic.StepId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-109 (contract v0.12.0; PM-approved copy as iOS, F): Make it live, into the client's own stores. */
class GoLiveLogicTest {

    private fun project(
        status: String = ProjectStatus.ACCEPTED,
        apps: List<String> = listOf("ios", "android"),
        backend: Boolean = false,
        stores: Map<String, String> = mapOf("apple" to "pending", "play" to "pending"),
        backendState: String = if (backend) "pending" else "none",
    ) = Project(
        id = "pr_1", status = status, apps = apps, hasBackend = backend,
        delivery = Delivery(handover = Handover(stores = stores, backend = backendState)),
    )

    @Test fun theStepsAppearOnceApproved() {
        assertNull(GoLiveLogic.steps(project(status = ProjectStatus.DELIVERED), awsVerified = false))
        assertEquals("These steps appear once you approve your app.", DeliveryCopy.NOTHING_YET)
        val steps = GoLiveLogic.steps(project(), awsVerified = false)!!
        assertEquals(listOf(StepId.ACCOUNTS, StepId.INVITE, StepId.PUBLISH, StepId.LIVE), steps.map { it.id })
        assertEquals(
            listOf("Create your store accounts", "Invite Raviga", "We publish", "Live"),
            steps.map { it.title },
        )
        assertTrue(steps.none { it.done })
    }

    @Test fun awsOnlyWithABackend() {
        val steps = GoLiveLogic.steps(project(backend = true), awsVerified = false)!!
        assertEquals(listOf(StepId.ACCOUNTS, StepId.INVITE, StepId.AWS, StepId.PUBLISH, StepId.LIVE), steps.map { it.id })
        assertEquals("Connect your AWS", steps[2].title)
        assertFalse(steps[2].done)
        assertTrue(GoLiveLogic.steps(project(backend = true), awsVerified = true)!![2].done)
        assertTrue("deployed by the team", GoLiveLogic.steps(project(backend = true, backendState = "deployed"), awsVerified = false)!![2].done)
    }

    @Test fun theStepsFollowTheHandover() {
        val invited = GoLiveLogic.steps(project(stores = mapOf("apple" to "invited", "play" to "in_review")), awsVerified = false)!!
        assertTrue(invited[0].done && invited[1].done)
        assertFalse(invited[2].done)
        val published = GoLiveLogic.steps(project(stores = mapOf("apple" to "published", "play" to "published")), awsVerified = false)!!
        assertTrue(published[2].done)
        assertFalse("live is the server's word", published[3].done)
        val live = GoLiveLogic.steps(project(status = ProjectStatus.LIVE, stores = mapOf("apple" to "published", "play" to "published")), awsVerified = false)!!
        assertTrue(live.all { it.done })
    }

    @Test fun onlyTheProjectsStores() {
        assertEquals(listOf("apple"), GoLiveLogic.stores(project(apps = listOf("ios"), stores = mapOf("apple" to "pending"))).list)
        assertEquals("the apps when the handover has no stores yet", listOf("play"), GoLiveLogic.stores(project(apps = listOf("android"), stores = emptyMap())).list)
        assertEquals(listOf("apple", "play"), GoLiveLogic.stores(project(apps = emptyList(), stores = emptyMap())).list)
        assertEquals(listOf("play"), GoLiveLogic.waitingForInvite(project(stores = mapOf("apple" to "invited", "play" to "pending"))))
    }

    @Test fun eachStoreSaysWhereItIs() {
        val sent = Handover(stores = mapOf("apple" to "in_review"), storeLines = mapOf("apple" to "In Apple's review"))
        assertEquals("the server's line, as sent", "In Apple's review", GoLiveLogic.storeLine(sent, "apple"))
        assertEquals("Waiting for your invite", GoLiveLogic.storeLine(Handover(stores = mapOf("play" to "pending")), "play"))
        assertEquals("Publishing", DeliveryCopy.storeLine("play", "invited"))
        assertEquals("In Google's review", DeliveryCopy.storeLine("play", "in_review"))
        assertEquals("Published", DeliveryCopy.storeLine("apple", "published"))
    }

    @Test fun theInviteWordsNameTheRolesAndThePublishingUser() {
        val email = "publishing@example.com"
        assertEquals(
            "In App Store Connect, open Users and Access, then People, and tap +. Invite $email with the App Manager role, and turn on Access to Certificates, Identifiers & Profiles.",
            DeliveryCopy.appleInvite(email),
        )
        assertTrue(DeliveryCopy.googleInvite(email).startsWith("In Play Console, open Users and permissions, then Invite new users. Invite $email and turn on: "))
        assertTrue(DeliveryCopy.googleInvite(email).endsWith("Manage policy declarations; View app information (read-only)."))
    }

    @Test fun settingsShowsAwsOnlyForABackendAtHandover() {
        assertFalse(GoLiveLogic.needsAwsRow(listOf(project(backend = false))))
        assertFalse("not before approval", GoLiveLogic.needsAwsRow(listOf(project(status = ProjectStatus.DELIVERED, backend = true))))
        assertTrue(GoLiveLogic.needsAwsRow(listOf(project(backend = true))))
        assertTrue(GoLiveLogic.needsAwsRow(listOf(project(status = ProjectStatus.LIVE, backend = true))))
    }

    /** R-103: a report's category in config's words; an unknown id reads as "Something else". */
    @Test fun reportCategoriesUseConfigLabels() {
        val categories = com.raviga.app.data.api.ReportConfig().categories
        assertEquals("Something doesn't work (a bug)", GoLiveLogic.reportLabel("bug", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("new_kind", categories))
        assertEquals("Something else", GoLiveLogic.reportLabel("bug", emptyList()))
    }

    /** v0.12.0: a delivery decodes with its demo, changes and handover; the old source fields are ignored. */
    @Test fun aV012DeliveryDecodes() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val d = json.decodeFromString(
            Delivery.serializer(),
            """{"deliveredAt":"a","acceptBy":"b",
               "demo":{"testflight":{"status":"pending","url":null},"play":{"status":"ready","url":"https://play.google.com/apps/internaltest/1"},"invitedEmails":{"apple":"a@b.co","google":"c@d.co"}},
               "changes":[{"id":"ch_1","status":"quoted","statusLine":"Change requested","brief":{"title":"Loyalty card","items":["A stamp card"]},"quote":{"credits":2,"usd":20,"inr":1700,"estimatedWorkingDays":3,"expiresAt":"x"}}],
               "handover":{"stores":{"apple":"pending","play":"in_review"},"storeLines":{"apple":"Waiting for your invite","play":"In Google's review"},"storeUrls":{"apple":null,"play":null},"backend":"none","awsAccountId":null,"consoleUrl":null,"statusLine":"Next: create your store accounts and invite Raviga."},
               "sourceUrl":"https://old","aws":{"status":"deployed"}}""",
        )
        assertFalse(d.demo!!.testflight!!.ready)
        assertTrue(d.demo!!.play!!.ready)
        assertEquals("c@d.co", d.demo!!.invitedEmails.google)
        assertEquals(2.0, d.openChange!!.quote!!.credits, 0.0)
        assertEquals("In Google's review", d.handover!!.storeLines["play"])
    }
}

/** R-109 (C, D, E, G; PM-approved as iOS): the app's own words for the demo, the invites and changes. */
class DeliveryCopyTest {
    @Test fun theInviteEmailsAreChecked() {
        assertEquals("Add an email address.", DeliveryCopy.emailProblem("  "))
        assertEquals("That doesn't look like an email address.", DeliveryCopy.emailProblem("you@phone"))
        assertEquals("That doesn't look like an email address.", DeliveryCopy.emailProblem("you at icloud.com"))
        assertNull(DeliveryCopy.emailProblem(" you@icloud.com "))
        assertEquals("Saved. You can change these until the invites go out.", DeliveryCopy.SAVED)
    }

    @Test fun theDemoSaysWhereTheInvitesWent() {
        assertEquals(
            "Install TestFlight from the App Store, then accept the invite we sent to a@b.co.",
            DeliveryCopy.testflightReady("a@b.co"),
        )
        assertEquals("Accept the invite we sent to c@d.co, then install the app from Google Play.", DeliveryCopy.playReady("c@d.co"))
        assertEquals("We're building. You'll get TestFlight and Play invites to try it.", DeliveryCopy.BUILDING_LINE)
        assertEquals(
            // R-119 / R-121 (as iOS): no brief sentence.
            "Next, we publish it to your App Store and Google Play accounts.",
            DeliveryCopy.APPROVE_BODY,
        )
    }

    @Test fun aChangeSaysWhatItCosts() {
        assertEquals("This change: 2 credits, about US\$20.", DeliveryCopy.changePrice("2 credits", "US\$20"))
        assertEquals("We're building your change. You'll get new invites to try it.", DeliveryCopy.CHANGE_BUILDING)
    }

    @Test fun nothingAboutCodeOrSource() {
        val all = listOf(
            DeliveryCopy.LIVE_INTRO, DeliveryCopy.LIVE_LINE, DeliveryCopy.AWS_LINE, DeliveryCopy.CONNECT_AWS_INTRO,
            DeliveryCopy.NOTHING_YET, DeliveryCopy.PUBLISH_LINE, DeliveryCopy.AWS_ID_HINT,
        )
        assertEquals("Your 12-digit AWS account id. We deploy your app's backend here when it goes live.", DeliveryCopy.AWS_ID_HINT)
        assertTrue(all.none { "code" in it.lowercase() || "github" in it.lowercase() || "source" in it.lowercase() })
    }
}
