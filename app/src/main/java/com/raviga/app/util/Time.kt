package com.raviga.app.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** ISO-8601 parsing and the short, human date formats the design calls for. */
object Time {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    // Built per use, so a change of the phone's language while the app runs is picked up.
    private val dayMonth get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val dayMonthYear get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val dayMonthTime get() = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun now(): Instant = Instant.now()
    fun nowIso(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso)
        } catch (_: DateTimeParseException) {
            try {
                ZonedDateTime.parse(iso).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate.parse(iso, isoDate).atStartOfDay(zone).toInstant()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    /** "9 Oct" or "9 Oct 2025" when not this year. */
    fun shortDate(iso: String?): String {
        val instant = parse(iso) ?: return ""
        val date = instant.atZone(zone)
        val thisYear = ZonedDateTime.now(zone).year == date.year
        return (if (thisYear) dayMonth else dayMonthYear).format(date)
    }

    /** "9 Oct 14:02" */
    fun dateTime(iso: String?): String {
        val instant = parse(iso) ?: return ""
        return dayMonthTime.format(instant.atZone(zone))
    }

    /** "just now", "4 min ago", "2 h ago", "yesterday", "3 days ago", else short date. */
    fun relative(iso: String?): String {
        val instant = parse(iso) ?: return ""
        val d = Duration.between(instant, Instant.now())
        val mins = d.toMinutes()
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            d.toHours() < 24 -> "${d.toHours()} h ago"
            d.toDays() == 1L -> "yesterday"
            d.toDays() < 7 -> "${d.toDays()} days ago"
            else -> shortDate(iso)
        }
    }

    fun isoDate(date: LocalDate): String = isoDate.format(date)

    /** Adds working days (Mon-Fri) to a date. */
    fun addWorkingDays(from: LocalDate, days: Int): LocalDate {
        var date = from
        var left = days
        while (left > 0) {
            date = date.plusDays(1)
            val dow = date.dayOfWeek.value
            if (dow < 6) left--
        }
        return date
    }
}
