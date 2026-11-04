package com.raviga.app.ui

import com.raviga.app.data.api.CreditPack
import com.raviga.app.data.api.CreditsConfig
import com.raviga.app.ui.credits.BriefPricing
import com.raviga.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** R-099 (contract v0.11.1-2; PM-approved copy as iOS): fractional credits and what a new brief costs. */
class BriefPricingTest {
    private val config = CreditsConfig(
        creditValueUsd = 10.0,
        packs = listOf(
            CreditPack("credits_quarter", 0.25, 0.0, priceHintUsd = 2.49, label = "3 briefs"),
            CreditPack("credits_10", 10.0, 0.0, priceHintUsd = 99.99, label = "10 credits"),
        ),
        briefPriceCredits = 0.0833,
        freeBriefs = 1,
    )

    /** J: whole numbers stay whole; a fraction shows two decimals, rounded down. */
    @Test fun balancesReadAsIos() {
        assertEquals("30 credits", Money.credits(30.0))
        assertEquals("1 credit", Money.credits(1.0))
        assertEquals("2.75 credits", Money.credits(2.75))
        assertEquals("rounded down, never more than there is", "9.91 credits", Money.credits(9.9167))
        assertEquals("0.25 credits", Money.credits(0.25))
        assertEquals("0 credits", Money.credits(0.0))
        assertEquals("0.08", Money.creditsNumber(0.0833))
        assertEquals("float noise reads whole", "203", Money.creditsNumber(202.99999))
        assertEquals("2.74", Money.creditsNumber(2.7499))
    }

    /** K: above "Looks right, write the brief". */
    @Test fun theNewBriefLineSaysWhatItCosts() {
        assertEquals("Your first brief is free.", BriefPricing.line(1, config, 0.0))
        assertEquals(
            "This brief uses 1/12 credit (about US$0.83). You have 2.75 credits.",
            BriefPricing.line(0, config, 2.75),
        )
        // As iOS: free only when the server doesn't say 0 and this phone has no other brief or submitted project.
        assertEquals(1, BriefPricing.freeLeft(1, wroteBefore = false))
        assertEquals("a /me read before the last brief can still say 1", 0, BriefPricing.freeLeft(1, wroteBefore = true))
        assertEquals("the server's 0 wins", 0, BriefPricing.freeLeft(0, wroteBefore = false))
        assertEquals("absent is unknown", 1, BriefPricing.freeLeft(null, wroteBefore = false))
        assertEquals(0, BriefPricing.freeLeft(null, wroteBefore = true))
    }

    /** L: short of a twelfth of a credit. */
    @Test fun theAddCreditsSheetNamesTheSmallestPack() {
        assertEquals("Add credits to write this brief", BriefPricing.NEED_TITLE)
        assertEquals(
            "A new brief uses 1/12 credit. The smallest pack, 0.25 credits for US$2.49, covers 3 briefs.",
            BriefPricing.needBody(config),
        )
        assertEquals("Buy 0.25 credits", BriefPricing.buyLabel(config))
        assertEquals("credits_quarter", BriefPricing.smallestPack(config)?.productId)
        assertEquals("no packs (as iOS)", "A new brief needs a little credit. Buy credits to continue.", BriefPricing.needBody(config.copy(packs = emptyList())))
    }

    /** Prod stays on v0.9.9 until the launch deploy: no brief price in /config, so no line (as iOS). */
    @Test fun anOlderServerShowsNoLine() {
        val older = CreditsConfig(creditValueUsd = 10.0, packs = config.packs.drop(1))
        assertEquals(0.0, older.briefPriceCredits, 0.0)
        assertNull(BriefPricing.line(1, older, 25.0))
        assertNull(BriefPricing.line(0, older, 25.0))
        assertEquals("1/12 credit", BriefPricing.briefPrice(0.0833))
        assertEquals("as iOS: two decimals", "0.50 credits", BriefPricing.briefPrice(0.5))
    }
}
