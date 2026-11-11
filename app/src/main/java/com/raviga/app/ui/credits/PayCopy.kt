package com.raviga.app.ui.credits

import com.raviga.app.data.api.CreditPack
import com.raviga.app.util.Money

/**
 * R-113 (her yes, 2026-11-11, walkthrough item 3; the PM's call: the same for change requests): paying asks first,
 * and buying credits from a project comes straight back to pay. Real numbers everywhere: whole stays whole, fractions
 * to two decimals rounded down; what's owed rounds up.
 */
object PayCopy {
    /** (a) "Pay 30 credits? You'll have 12 left." */
    fun confirm(price: Double, balance: Double): String =
        "Pay ${Money.credits(price)}? You'll have ${Money.creditsNumber((balance - price).coerceAtLeast(0.0))} left."

    /** (b) After a purchase that covers it: "Credits added. Pay 30 credits to start building?" */
    fun creditsAdded(price: Double): String = "Credits added. Pay ${Money.credits(price)} to start building?"

    const val PAY = "Pay"
    const val CANCEL = "Cancel"

    /** The saved-state key a project screen reads when Credits comes back from a purchase made for it. */
    const val CREDITS_ADDED = "credits_added"
}

/**
 * R-113 (her yes, 3c): opened from a project, the packs screen highlights the smallest pack that covers what's owed,
 * bonus credits counted (the quarter credit only when 0.25 covers it); the largest when no single pack does.
 */
object PackPick {
    fun covering(packs: List<CreditPack>, short: Double): CreditPack? {
        if (short <= 0.0 || packs.isEmpty()) return null
        val bySize = packs.sortedBy { it.total }
        return bySize.firstOrNull { it.total + 1e-9 >= short } ?: bySize.last()
    }
}
