package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
import com.raviga.app.data.api.Change
import com.raviga.app.data.api.ChangeQuote
import com.raviga.app.data.api.Delivery
import com.raviga.app.data.api.Payment
import com.raviga.app.data.api.Project
import com.raviga.app.data.api.ProjectStatus
import com.raviga.app.data.api.Quote
import com.raviga.app.data.api.QuoteResult
import com.raviga.app.ui.status.StatusViewModel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-103 (contract v0.10.0 section 11): paying an accepted project, as iOS does it; R-109: and a change. */
class PayStateTest {
    private val now = 1_800_000_000_000L
    private fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
    private val accepted = Project(
        id = "pr_1", status = ProjectStatus.APPROVED,
        quote = Quote(id = "qt_1", credits = 30, expiresAt = iso(now + 60_000)),
    )
    private fun state(project: Project = accepted, balance: Double = 50.0, clock: Long = now) =
        StatusViewModel.State(project = project, balance = balance, clock = clock)

    @Test fun enoughCreditsPaysTheProjectsPrice() {
        val s = state()
        assertTrue(s.awaitingPayment)
        assertFalse(s.priceExpired)
        assertEquals(30, s.price)
        assertEquals(0.0, s.shortBy, 0.0)
    }

    @Test fun shortOfCreditsSaysByHowMuch() {
        assertEquals(20.0, state(balance = 10.0).shortBy, 0.0)
    }

    @Test fun anExpiredPriceAsksForANewQuoteFirst() {
        val s = state(clock = now + 60_000)
        assertTrue(s.priceExpired)
        assertEquals("no shortfall line while the price is out of date", 0.0, s.shortBy, 0.0)
        val requoted = s.copy(newQuote = QuoteResult(Quote(id = "qt_2", credits = 34, expiresAt = iso(now + 9_000_000)), "tok2"))
        assertFalse(requoted.priceExpired)
        assertEquals(34, requoted.price)
        assertEquals(0.0, requoted.copy(balance = 34.0).shortBy, 0.0)
        assertEquals(4.0, requoted.copy(balance = 30.0).shortBy, 0.0)
    }

    @Test fun onlyAnUnpaidAcceptedProjectIsPaid() {
        assertFalse(state(accepted.copy(status = ProjectStatus.SUBMITTED)).awaitingPayment)
        assertFalse(state(accepted.copy(status = ProjectStatus.BUILDING)).awaitingPayment)
        assertFalse("hardening: paid goes by payment, not status alone", state(accepted.copy(payment = Payment(30, "x"))).awaitingPayment)
    }

    /** R-105 (her decision; iOS's copy): built in the client's own AWS, so paying waits for a verified role. */
    /** R-109 (contract v0.12.0): no AWS before paying; Raviga builds on its own side. */
    @Test fun payingNeedsNoAws() {
        val s = state().copy(awsVerified = false)
        assertTrue(s.awaitingPayment)
        assertEquals("the price, whatever AWS says", 0.0, s.shortBy, 0.0)
        assertEquals(20.0, s.copy(balance = 10.0).shortBy, 0.0)
    }

    /** R-109 (E): one change at a time, after the demo; a priced one is paid like the project, short of credits too. */
    @Test fun aChangeIsAskedAfterTheDemoAndPaidLikeTheProject() {
        val demo = accepted.copy(status = ProjectStatus.DELIVERED, payment = Payment(30, iso(now)), delivery = Delivery())
        assertTrue(state(demo).canRequestChange)
        assertTrue(state(demo.copy(status = ProjectStatus.ACCEPTED)).canRequestChange)
        assertTrue(state(demo.copy(status = ProjectStatus.LIVE)).canRequestChange)
        assertFalse("not while it's being built", state(demo.copy(status = ProjectStatus.BUILDING)).canRequestChange)
        assertFalse("not before the demo", state(accepted).canRequestChange)

        val quoted = Change("ch_1", status = Change.QUOTED, statusLine = "Change requested", quote = ChangeQuote(credits = 2.0, usd = 20.0))
        val withChange = demo.copy(delivery = Delivery(changes = listOf(Change("ch_0", status = Change.DELIVERED), quoted)))
        val s = state(withChange, balance = 1.5)
        assertFalse("one open change at a time", s.canRequestChange)
        assertEquals("ch_1", s.quotedChange?.id)
        assertEquals(0.5, s.changeShortBy, 0.0)
        assertEquals(0.0, state(withChange, balance = 5.0).changeShortBy, 0.0)
        val paid = withChange.copy(delivery = Delivery(changes = listOf(quoted.copy(status = Change.PAID))))
        assertNull("paid: nothing to pay", state(paid).quotedChange)
        assertEquals(Change.PAID, state(paid).openChange?.status)
    }

    /** R-109 (C): the invites asked for are the project's apps; editable while it's built, until they go out. */
    @Test fun theInvitesFollowTheApps() {
        val building = accepted.copy(status = ProjectStatus.BUILDING, apps = listOf("ios"))
        assertTrue(state(building).invitesEditable)
        assertTrue(state(building).wantsApple)
        assertFalse(state(building).wantsGoogle)
        assertTrue("an older server: both", state(building.copy(apps = emptyList())).wantsGoogle)
        assertFalse("the invites are out", state(building.copy(status = ProjectStatus.DELIVERED)).invitesEditable)
    }

    @Test fun aFailedPaymentShowsTheNextStep() {
        val short = StatusViewModel.afterFailedPay(state(balance = 50.0), ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need 20 more credits.",
            details = buildJsonObject { put("required", 30); put("balance", 10) },
        ))
        assertNull("the balance line and Buy credits say it, not an error", short.error)
        assertEquals(10.0, short.balance, 0.0)
        assertEquals(20.0, short.shortBy, 0.0)

        val expired = StatusViewModel.afterFailedPay(state(), ApiException(
            ApiException.QUOTE_STALE, 409, "This price has expired. Get a new quote to start building.",
            details = buildJsonObject { put("expired", true) },
        ))
        assertTrue("the server's clock decides", expired.priceExpired)
        assertNull(expired.error)

        val other = StatusViewModel.afterFailedPay(state(), ApiException(ApiException.INTERNAL, 500, "boom"))
        assertEquals("Something went wrong on our side. Try again in a moment.", other.error)
    }
}
