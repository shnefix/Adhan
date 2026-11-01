package com.example.prayeralarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.data.notification.NotificationHelper
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Re-applies alarms and the countdown notification after reboot, app update, date change,
 * manual time change and time-zone change. AlarmManager forgets all alarms on reboot.
 */
class PrayerAlarmRecoveryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val application = context.applicationContext as PrayerAlarmApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reconcile(application)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun reconcile(application: PrayerAlarmApplication) {
        LegacyAlarms.cancel(application)

        val location = application.locationRepository.getActiveLocation().first()
        val config = application.alarmPlanPreferences.config.first()
        if (location == null) {
            NotificationHelper.cancel(application)
            return
        }
        val settings = application.preferencesManager.calculationSettings.first()

        FajrAlarmReconciler.reconcile(
            scheduler = application.prayerAlarmScheduler,
            location = location,
            config = config,
            settings = settings
        )

        if (!config.enabled) {
            NotificationHelper.cancel(application)
            return
        }

        val clock = Clock.systemUTC()
        val zone = PrayerTimeCalculator.zoneIdFor(location)
        val today = LocalDate.now(clock.withZone(zone))
        val todayTimetable =
            PrayerTimeCalculator.calculateTimetable(location, today, settings.method, settings.madhab)
        val tomorrowTimetable =
            PrayerTimeCalculator.calculateTimetable(location, today.plusDays(1), settings.method, settings.madhab)
        val now = Instant.now(clock)
        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = location,
            currentDate = today,
            now = now,
            todayTimetable = todayTimetable,
            tomorrowTimetable = tomorrowTimetable
        )
        val remainingMillis = countdown.nextPrayerDateTime
            ?.let { Duration.between(now, it).toMillis() }
            ?: 0L
        NotificationHelper.postWhenEnabled(
            context = application,
            selectedLocation = location,
            nextPrayer = countdown.nextPrayerItem,
            remainingMillis = remainingMillis
        )
        CountdownNotificationService.start(application)
    }
}
