package com.example.prayeralarm.alarm

import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.domain.model.SavedLocation
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Single entry point that brings AlarmManager in line with the current location, Fajr
 * times, calculation settings and alarm settings. Used by the UI ViewModel, the boot /
 * time-change receiver, and the alarm receiver itself (which extends the rolling window
 * every time an alarm fires).
 */
object FajrAlarmReconciler {

    /** Today plus the next two days stay scheduled, so a missed refresh is harmless. */
    const val HORIZON_DAYS = 3

    fun reconcile(
        scheduler: PrayerAlarmScheduler,
        location: SavedLocation,
        config: AlarmPlanConfig,
        settings: PrayerCalculationSettings,
        clock: Clock = Clock.systemUTC()
    ): AlarmReconciliationResult {
        val zone = PrayerTimeCalculator.zoneIdFor(location)
        val today = LocalDate.now(clock.withZone(zone))
        val dates = (0 until HORIZON_DAYS).map { today.plusDays(it.toLong()) }
        val days = dates.map { date ->
            FajrScheduleDay(
                PrayerTimeCalculator
                    .calculateTimetable(location, date, settings.method, settings.madhab)
                    .prayerDateTimes.getValue(PrayerType.FAJR)
            )
        }
        val window = listOf(today.minusDays(1)) + dates
        return PrayerAlarmCoordinator(scheduler)
            .reconcile(days, config, Instant.now(clock), location.name, window, DismissedSeries.all(today))
    }

    /** Reads the current inputs from storage and reconciles. Null when no location exists. */
    suspend fun refresh(application: PrayerAlarmApplication): AlarmReconciliationResult? {
        DismissedSeries.init(application)
        val location = application.locationRepository.getActiveLocation().first() ?: return null
        val config = application.alarmPlanPreferences.config.first()
        val settings = application.preferencesManager.calculationSettings.first()
        return reconcile(application.prayerAlarmScheduler, location, config, settings)
    }
}
