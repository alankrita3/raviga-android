package com.raviga.app.ui.credits

import com.raviga.app.data.api.CreditsConfig
import com.raviga.app.util.Money
import java.util.Locale

/**
 * R-099 (contract v0.11.1-2, her decision; PM-approved copy as iOS): the first new brief per account is free, each later
 * one uses a twelfth of a credit. The server counts (`freeBriefsLeft`) and its 402 decides; these are the words.
 */
object BriefPricing {
    const val FREE_LINE = "Your first brief is free."
    const val NEED_TITLE = "Add credits to write this brief"

    /** A brief's price in her words: "1/12 credit" for a twelfth, else the amount (as iOS's Format.briefPrice). */
    fun briefPrice(credits: Double): String = if (Math.abs(credits * 12 - 1) < 0.01) "1/12 credit" else Money.credits(credits)

    /** "US$0.83": a brief's price in dollars, to the cent. */
    fun briefUsd(c: CreditsConfig): String =
        "US$" + String.format(Locale.US, "%.2f", Math.round(c.briefPriceCredits * c.creditValueUsd * 100) / 100.0)

    /** K: above "Looks right, write the brief"; null on a server without brief pricing (prod before the launch deploy). */
    fun line(freeLeft: Int, c: CreditsConfig, balance: Double): String? = when {
        c.briefPriceCredits <= 0.0 -> null
        freeLeft > 0 -> FREE_LINE
        else -> "This brief uses ${briefPrice(c.briefPriceCredits)} (about ${briefUsd(c)}). You have ${Money.credits(balance)}."
    }

    /**
     * As iOS: free only when the server's count (v0.11.2; absent is unknown) isn't 0 and this phone has written no other
     * brief, submitted no project and has no "brief" line in its ledger. The server's 402 decides either way.
     */
    fun freeLeft(server: Int?, wroteBefore: Boolean): Int = if (server == 0 || wroteBefore) 0 else server ?: 1

    /** The smallest pack, which covers a few briefs (credits_quarter). */
    fun smallestPack(c: CreditsConfig) = c.packs.minByOrNull { it.total }

    /** L's body (as iOS): "A new brief uses 1/12 credit. The smallest pack, 0.25 credits for US$2.49, covers 3 briefs." */
    fun needBody(c: CreditsConfig): String {
        val price = c.briefPriceCredits
        val pack = smallestPack(c)
        if (price <= 0.0 || pack == null) return "A new brief needs a little credit. Buy credits to continue."
        val cost = pack.priceHintUsd?.let { " for US$" + String.format(Locale.US, "%.2f", it) } ?: ""
        val briefs = Math.floor(pack.total / price + 0.01).toInt()
        return "A new brief uses ${briefPrice(price)}. The smallest pack, ${Money.credits(pack.total)}$cost, covers $briefs briefs."
    }

    /** L's first button: "Buy 0.25 credits". */
    fun buyLabel(c: CreditsConfig): String? = smallestPack(c)?.let { "Buy ${Money.credits(it.total)}" }
}
