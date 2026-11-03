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
