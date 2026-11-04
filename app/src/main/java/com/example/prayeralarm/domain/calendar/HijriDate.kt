package com.example.prayeralarm.domain.calendar

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Hijri date from the Umm al-Qura calendar built into java.time.
 *
 * The announced date can differ from Umm al-Qura by a day where months start on a local
 * moon sighting (as in Tunisia), so the user can shift it with [adjustmentDays].
 */
object HijriDate {

    const val MIN_ADJUSTMENT = -2
    const val MAX_ADJUSTMENT = 2

    private val monthNames = listOf(
        "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
        "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Shaban",
        "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah"
    )

    /** "18 Jumada al-Awwal 1448", or an empty string outside the calendar's supported range. */
    fun format(date: LocalDate, adjustmentDays: Int = 0): String = runCatching {
        val shift = adjustmentDays.coerceIn(MIN_ADJUSTMENT, MAX_ADJUSTMENT).toLong()
        val hijri = HijrahDate.from(date.plusDays(shift))
        val day = hijri.get(ChronoField.DAY_OF_MONTH)
        val month = monthNames[hijri.get(ChronoField.MONTH_OF_YEAR) - 1]
        val year = hijri.get(ChronoField.YEAR)
        "$day $month $year"
    }.getOrDefault("")
}
