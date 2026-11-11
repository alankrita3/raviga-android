package com.raviga.app.ui

import com.raviga.app.data.api.CreditPack
import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.Document
import com.raviga.app.data.api.Handover
import com.raviga.app.data.api.HistoryEntry
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.ReportConfig
import com.raviga.app.data.api.Section
import com.raviga.app.data.api.SectionIds
import com.raviga.app.data.drafts.LocalDraft
import com.raviga.app.data.drafts.LocalVersion
import com.raviga.app.ui.credits.PackPick
import com.raviga.app.ui.credits.PayCopy
import com.raviga.app.ui.onboarding.HowItWorks
import com.raviga.app.ui.status.DeliveryCopy
import com.raviga.app.ui.status.GoLiveLogic
import com.raviga.app.ui.status.StatusViewModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-113: her walkthrough bundle (2026-11-10/11, iOS docs/walkthrough-bundle-2026-11-10.md; contract v0.12.1, v0.13.0). */
class WalkthroughBundleTest {

    // ----- 1. Brief: no open questions; an old draft drops the section quietly -----

    @Test fun theBriefHasElevenSections() {
        assertEquals(11, SectionIds.ordered.size)
        assertEquals(SectionIds.OUT_OF_SCOPE, SectionIds.ordered.last())
        assertFalse(SectionIds.headings.containsValue("Open questions"))
    }

    @Test fun aStoredTwelveSectionDraftLoadsWithoutOpenQuestions() {
        val json = Json { ignoreUnknownKeys = true }
        val stored = """{"id":"ld_1","createdAt":"a","updatedAt":"b","versions":[
            {"version":1,"createdAt":"a","source":"draft","document":{"title":"Salon","sections":[
              {"id":"summary","heading":"Summary","body":"A salon app"},
              {"id":"out_of_scope","heading":"Out of scope","body":"- A website"},
              {"id":"open_questions","heading":"Open questions","body":"- Do you have a logo?"}]}},
            {"version":2,"createdAt":"c","source":"edit","document":{"title":"Salon","sections":[
              {"id":"summary","heading":"Summary","body":"A salon booking app"},
              {"id":"open_questions","heading":"Open questions","body":"- Languages?"}]}}]}"""
        val draft = json.decodeFromString(LocalDraft.serializer(), stored).withoutRetired()
        assertEquals("every version kept", 2, draft.versions.size)
        assertTrue(draft.versions.all { v -> v.document.sections.none { it.id == SectionIds.RETIRED_OPEN_QUESTIONS } })
        assertEquals(listOf("summary", "out_of_scope"), draft.versions.first().document.sections.map { it.id })
        assertEquals("A salon booking app", draft.document?.section(SectionIds.SUMMARY)?.body)
        val clean = LocalDraft("ld_2", createdAt = "a", updatedAt = "b", versions = listOf(LocalVersion(1, Document(sections = listOf(Section("summary", "Summary", "x"))), "a", "draft")))
        assertTrue("a clean draft is left as it is", clean.withoutRetired() === clean)
    }

    // ----- 2. Quote -----

    @Test fun theQuoteSaysWhoKeepsTheCode() {
        assertEquals(
            "Your published app is yours. The editable code stays with us, so we can keep improving it for you.",
            com.raviga.app.ui.quote.OWNERSHIP_LINE,
        )
        assertEquals("the same words in How it works, step 3", com.raviga.app.ui.quote.OWNERSHIP_LINE, HowItWorks.steps[2].note)
    }

    // ----- 3. Pay -----

    @Test fun payingAsksFirstWithRealNumbers() {
        assertEquals("Pay 30 credits? You'll have 12 left.", PayCopy.confirm(30.0, 42.0))
        assertEquals("Pay 3 credits? You'll have 0.83 left.", PayCopy.confirm(3.0, 3.8333))
        assertEquals("Pay 1 credit? You'll have 0 left.", PayCopy.confirm(1.0, 1.0))
        assertEquals("Credits added. Pay 30 credits to start building?", PayCopy.creditsAdded(30.0))
    }

    private val packs = listOf(
        CreditPack("credits_quarter", 0.25, 0.0),
        CreditPack("credits_10", 10.0, 0.0),
        CreditPack("credits_25", 25.0, 2.0),
        CreditPack("credits_50", 50.0, 6.0),
        CreditPack("credits_100", 100.0, 15.0),
    )

    @Test fun theSmallestPackThatCoversItStandsOut() {
        assertEquals("credits_10", PackPick.covering(packs, 6.17)?.productId)
        assertEquals("bonus counted: 25 + 2", "credits_25", PackPick.covering(packs, 27.0)?.productId)
        assertEquals("credits_50", PackPick.covering(packs, 27.5)?.productId)
        assertEquals("the quarter only when 0.25 covers it", "credits_quarter", PackPick.covering(packs, 0.08)?.productId)
        assertEquals("credits_10", PackPick.covering(packs, 0.26)?.productId)
        assertEquals("none covers it: the largest", "credits_100", PackPick.covering(packs, 200.0)?.productId)
        assertNull("nothing owed: no highlight", PackPick.covering(packs, 0.0))
    }

    // ----- 4. Make it live -----

    private fun accepted(apps: List<String>, backend: String) = Project(
        id = "pr_1", status = ProjectStatus.ACCEPTED, apps = apps, hasBackend = backend != "none",
        delivery = Delivery(handover = Handover(stores = apps.associate { (if (it == "ios") "apple" else "play") to "pending" }, backend = backend)),
    )

    @Test fun noBackendMeansFourStepsAndNoAws() {
        val steps = GoLiveLogic.steps(accepted(listOf("ios", "android"), "none"), awsVerified = false)!!
        assertEquals(listOf("Create your store accounts", "Invite Raviga", "We publish", "Live"), steps.map { it.title })
        assertTrue(steps.none { "AWS" in it.title })
        assertEquals(5, GoLiveLogic.steps(accepted(listOf("ios"), "pending"), awsVerified = false)!!.size)
        assertEquals(5, GoLiveLogic.steps(accepted(listOf("ios"), "deployed"), awsVerified = false)!!.size)
    }

    @Test fun storeAccountsSayTheFeesWithoutPrices() {
        assertEquals("Apple and Google charge their own account fees.", DeliveryCopy.feeLine(apple = true, play = true))
        assertEquals("Apple charges its own account fee.", DeliveryCopy.feeLine(apple = true, play = false))
        assertEquals("Google charges its own account fee.", DeliveryCopy.feeLine(apple = false, play = true))
        assertEquals("Join the Apple Developer Program.", DeliveryCopy.APPLE_JOIN)
        assertEquals("Create a Google Play developer account.", DeliveryCopy.GOOGLE_JOIN)
        assertFalse(DeliveryCopy.APPLE_JOIN.contains("$") || DeliveryCopy.GOOGLE_JOIN.contains("$"))
    }

    @Test fun theInviteStepsFoldAndNameTheEmail() {
        assertEquals("Invite us as a team member", DeliveryCopy.INVITE_LINE)
        assertEquals("Show exact steps", DeliveryCopy.SHOW_STEPS)
        assertTrue(DeliveryCopy.appleInvite(DeliveryCopy.PUBLISHING_EMAIL_FALLBACK).contains("Invite Raviga's publishing email with the App Manager role"))
    }

    // ----- 5. Live: the free-fix window -----

    private val now = 1_800_000_000_000L
    private fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
    private fun state(status: String, until: String? = null, history: List<HistoryEntry> = emptyList()) = StatusViewModel.State(
        project = Project(id = "pr_1", status = status, freeFixesUntil = until, apps = listOf("ios", "android"), history = history),
        clock = now,
    )

    @Test fun somethingsWrongFromTheDemoUntilSevenDaysAfterLaunch() {
        assertTrue("the demo", state(ProjectStatus.DELIVERED).freeFixesOpen)
        assertTrue("between approval and launch", state(ProjectStatus.ACCEPTED).freeFixesOpen)
        assertTrue("live, inside the window", state(ProjectStatus.LIVE, iso(now + 60_000)).freeFixesOpen)
        assertFalse("live, after it", state(ProjectStatus.LIVE, iso(now - 60_000)).freeFixesOpen)
        assertFalse("a date the app can't read: hidden", state(ProjectStatus.LIVE, "soon").freeFixesOpen)
        assertFalse("while it's being built", state(ProjectStatus.BUILDING).freeFixesOpen)
        assertFalse("the server said it ended", state(ProjectStatus.LIVE, iso(now + 60_000)).copy(freeFixesEnded = true).freeFixesOpen)
    }

    @Test fun theChangeSheetPointsToSomethingsWrongOnlyInTheWindow() {
        assertEquals(DeliveryCopy.CHANGE_LINE, DeliveryCopy.changeLine(freeFixesOpen = true))
        assertEquals("Describe what's new or different.", DeliveryCopy.changeLine(freeFixesOpen = false))
        assertEquals(
            "Bugs and anything that doesn't match the brief are fixed free until 7 days after launch. New features are quoted as changes.",
            ReportConfig().freeFixesLine,
        )
        assertEquals(7, ReportConfig().freeFixDays)
    }

    @Test fun theTileSaysYourAppIsLiveOnceItsInAStore() {
        assertFalse(state(ProjectStatus.ACCEPTED).launched)
        assertTrue(state(ProjectStatus.LIVE).launched)
        assertTrue("a fix after launch goes round again", state(ProjectStatus.DELIVERED, iso(now + 1)).launched)
        assertEquals("Your app is live", DeliveryCopy.LIVE_TILE)
    }

    @Test fun aSecondApprovalPublishesAgain() {
        assertFalse(state(ProjectStatus.DELIVERED).secondApproval)
        val again = state(ProjectStatus.DELIVERED, history = listOf(HistoryEntry(ProjectStatus.ACCEPTED, "a", "client"), HistoryEntry(ProjectStatus.LIVE, "b", "system")))
        assertTrue(again.secondApproval)
        assertEquals("Approve this version?", DeliveryCopy.APPROVE_AGAIN_TITLE)
        assertEquals("Next, we publish it again to your App Store and Google Play accounts.", DeliveryCopy.approveAgainBody(apple = true, play = true))
        assertEquals("Next, we publish it again to your App Store account.", DeliveryCopy.approveAgainBody(apple = true, play = false))
        assertEquals("Next, we publish it again to your Google Play account.", DeliveryCopy.approveAgainBody(apple = false, play = true))
    }

    // ----- 6. How it works -----

    @Test fun howItWorksIsTheApprovedEightSteps() {
        assertEquals(8, HowItWorks.steps.size)
        assertEquals(
            listOf(
                "Tell us your idea", "We write your brief", "You see the price", "Submit, then pay when we accept",
                "We build your app", "Try the demo on your phone", "Go live under your name", "Your app is live",
            ),
            HowItWorks.steps.map { it.title },
        )
        assertEquals(listOf("You", "We", "We", "You", "We", "You", "You, then we", "We"), HowItWorks.steps.map { it.who })
        assertEquals(listOf(false, true, true, false, true, false, false, true), HowItWorks.steps.map { it.ours })
        val demo = HowItWorks.steps[5]
        assertEquals(listOf("It works, approved", "Something's wrong", "Request a change"), demo.choices.map { it.label })
        assertEquals(listOf(false, true, false), demo.choices.map { it.free })
        assertTrue(HowItWorks.steps[6].note!!.startsWith("Apple and Google charge their own account fees."))
    }
}
