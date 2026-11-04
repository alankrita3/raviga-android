package com.raviga.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Prices are shown in US dollars (contract v0.7); pack buttons use the store's own price string. */
object Money {
    /** "$320", "$1,250", "$99.99": cents only when there are some. */
    fun usd(amount: Double): String {
        val whole = abs(amount - amount.roundToLong()) < 0.005
        val format = NumberFormat.getCurrencyInstance(Locale.US).apply {
            minimumFractionDigits = if (whole) 0 else 2
            maximumFractionDigits = if (whole) 0 else 2
        }
        return format.format(amount)
    }

    fun credits(n: Int): String = if (n == 1) "1 credit" else "$n credits"

    /**
     * v0.11.1 (R-099; iOS's rule): whole numbers stay whole ("30 credits"); a fraction shows two decimals, rounded down
     * ("2.75 credits", never more than the account has).
     */
    fun credits(n: Double): String = "${creditsNumber(n)} ${if (Math.abs(n - 1) < 0.0001) "credit" else "credits"}"

    /** "30", "2.75", "0.08": the number alone, two decimals rounded down when it isn't whole (as iOS's Format.creditAmount). */
    fun creditsNumber(n: Double): String {
        val whole = Math.rint(n)
        if (Math.abs(n - whole) < 0.0001) return whole.toLong().toString()
        val cents = Math.floor(Math.abs(n) * 100 + 0.0001) / 100
        return (if (n < 0) "-" else "") + String.format(java.util.Locale.US, "%.2f", cents)
    }
}
