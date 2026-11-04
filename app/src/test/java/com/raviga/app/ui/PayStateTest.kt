package com.raviga.app.ui

import com.raviga.app.data.api.ApiException
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

/** R-103 (contract v0.10.0 section 11): paying an accepted project, as iOS does it. */
class PayStateTest {
    private val now = 1_800_000_000_000L
    private fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()
    private val accepted = Project(
        id = "pr_1", status = ProjectStatus.APPROVED,
        quote = Quote(id = "qt_1", credits = 30, expiresAt = iso(now + 60_000)),
    )
    private fun state(project: Project = accepted, balance: Int = 50, clock: Long = now) =
        StatusViewModel.State(project = project, balance = balance, clock = clock)

    @Test fun enoughCreditsPaysTheProjectsPrice() {
        val s = state()
        assertTrue(s.awaitingPayment)
        assertFalse(s.priceExpired)
        assertEquals(30, s.price)
        assertEquals(0, s.shortBy)
    }

    @Test fun shortOfCreditsSaysByHowMuch() {
        assertEquals(20, state(balance = 10).shortBy)
    }

    @Test fun anExpiredPriceAsksForANewQuoteFirst() {
        val s = state(clock = now + 60_000)
        assertTrue(s.priceExpired)
        assertEquals("no shortfall line while the price is out of date", 0, s.shortBy)
        val requoted = s.copy(newQuote = QuoteResult(Quote(id = "qt_2", credits = 34, expiresAt = iso(now + 9_000_000)), "tok2"))
        assertFalse(requoted.priceExpired)
        assertEquals(34, requoted.price)
        assertEquals(0, requoted.copy(balance = 34).shortBy)
        assertEquals(4, requoted.copy(balance = 30).shortBy)
    }

    @Test fun onlyAnUnpaidAcceptedProjectIsPaid() {
        assertFalse(state(accepted.copy(status = ProjectStatus.SUBMITTED)).awaitingPayment)
        assertFalse(state(accepted.copy(status = ProjectStatus.BUILDING)).awaitingPayment)
        assertFalse("hardening: paid goes by payment, not status alone", state(accepted.copy(payment = Payment(30, "x"))).awaitingPayment)
    }

    /** R-105 (her decision; iOS's copy): built in the client's own AWS, so paying waits for a verified role. */
    @Test fun anUnverifiedAwsAccountConnectsBeforePaying() {
        val required = com.raviga.app.data.api.AppConfig(delivery = com.raviga.app.data.api.DeliveryConfig(awsRequiredToPay = true))
        val s = state().copy(config = required)
        assertTrue(s.needsAws)
        assertEquals("no shortfall line while AWS isn't connected", 0, s.copy(balance = 10).shortBy)
        assertFalse(s.copy(awsVerified = true).needsAws)
        assertFalse("only an accepted, unpaid project", s.copy(project = accepted.copy(status = ProjectStatus.BUILDING)).needsAws)
        assertFalse("a server without the rule", state().needsAws)
        assertEquals(
            "We build your project in your own AWS account. Connect it once, then pay to start building.",
            com.raviga.app.ui.status.AWS_BEFORE_PAY,
        )
        val refused = StatusViewModel.afterFailedPay(state(), ApiException(
            ApiException.AWS_REQUIRED, 409, "Connect your AWS account to start the build.",
            details = buildJsonObject { put("accountId", "123456789012") },
        ))
        assertTrue("the server decides too", refused.needsAws)
        assertEquals("Connect your AWS account to start the build.", refused.error)
    }

    @Test fun aFailedPaymentShowsTheNextStep() {
        val short = StatusViewModel.afterFailedPay(state(balance = 50), ApiException(
            ApiException.INSUFFICIENT_CREDITS, 402, "You need 20 more credits.",
            details = buildJsonObject { put("required", 30); put("balance", 10) },
        ))
        assertNull("the balance line and Buy credits say it, not an error", short.error)
        assertEquals(10, short.balance)
        assertEquals(20, short.shortBy)

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
