package com.example.prayeralarm.domain.calculator

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Madhab

enum class PrayerCalculationMethod(val displayName: String, val summary: String) {
    MUSLIM_WORLD_LEAGUE("Muslim World League", "Fajr 18°, Isha 17°"),
    EGYPTIAN("Egyptian General Authority of Survey", "Fajr 19.5°, Isha 17.5°"),
    KARACHI("Univ. of Islamic Sciences, Karachi", "Fajr 18°, Isha 18°"),
    UMM_AL_QURA("Umm al-Qura University, Makkah", "Fajr 18.5°, Isha 90 min after Maghrib"),
    NORTH_AMERICA("Islamic Society of North America", "Fajr 15°, Isha 15°"),
    TUNISIA("Tunisia (18°/18°)", "Fajr 18°, Isha 18°");

    /** Fresh parameters each call so callers cannot mutate shared state. */
    fun createParameters(madhab: Madhab = Madhab.SHAFI): CalculationParameters {
        val p = when (this) {
            MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
            EGYPTIAN -> CalculationMethod.EGYPTIAN.parameters
            KARACHI -> CalculationMethod.KARACHI.parameters
            UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA.parameters
            NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA.parameters
            TUNISIA -> CalculationMethod.OTHER.parameters.also {
                it.fajrAngle = 18.0
                it.ishaAngle = 18.0
            }
        }
        p.madhab = madhab
        return p
    }

    companion object {
        val DEFAULT: PrayerCalculationMethod = TUNISIA
    }
}

data class PrayerCalculationSettings(
    val method: PrayerCalculationMethod = PrayerCalculationMethod.DEFAULT,
    val madhab: Madhab = Madhab.SHAFI
)
