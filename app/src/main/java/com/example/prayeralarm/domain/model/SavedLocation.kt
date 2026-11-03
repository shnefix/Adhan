package com.example.prayeralarm.domain.model

import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod

data class SavedLocation(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val isDefault: Boolean = false,
    val adjustments: PrayerTimeAdjustments = PrayerTimeAdjustments.NONE,
    /** Overrides the app-wide method for this location only; null follows the Settings choice. */
    val calculationMethod: PrayerCalculationMethod? = null
)

/**
 * Per-location minute offsets added on top of the astronomical result, so a city's
 * timetable can be tuned to match its official/mosque times exactly.
 */
data class PrayerTimeAdjustments(
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0
) {
    fun minutesFor(type: PrayerType): Int = when (type) {
        PrayerType.FAJR -> fajr
        PrayerType.DHUHR -> dhuhr
        PrayerType.ASR -> asr
        PrayerType.MAGHRIB -> maghrib
        PrayerType.ISHA -> isha
    }

    val isEmpty: Boolean get() = this == NONE

    companion object {
        const val MAX_MINUTES = 60
        val NONE = PrayerTimeAdjustments()
    }
}
