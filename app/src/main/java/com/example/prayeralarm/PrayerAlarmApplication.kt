package com.example.prayeralarm

import android.app.Application
import com.example.prayeralarm.data.local.LocationSeeder
import com.example.prayeralarm.data.local.PrayerAlarmDatabase
import com.example.prayeralarm.data.preferences.UserPreferencesManager
import com.example.prayeralarm.data.notification.NotificationHelper
import com.example.prayeralarm.data.repository.LocationRepository
import com.example.prayeralarm.data.repository.LocationRepositoryImpl
import com.example.prayeralarm.alarm.AndroidAlarmScheduler
import com.example.prayeralarm.alarm.DismissedSeries
import com.example.prayeralarm.alarm.LegacyAlarms
import com.example.prayeralarm.alarm.PrayerAlarmScheduler
import com.example.prayeralarm.ui.alarms.AlarmPlanPreferences
import com.example.prayeralarm.ui.alarms.DataStoreAlarmPlanPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Custom Application class for Prayer Alarm.
 *
 * Serves as the central service locator / manual dependency injection container
 * for long-lived application components (Room database, repositories, preferences).
 */
class PrayerAlarmApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: PrayerAlarmDatabase by lazy {
        PrayerAlarmDatabase.getInstance(this, applicationScope)
    }

    val preferencesManager: UserPreferencesManager by lazy {
        UserPreferencesManager(this)
    }

    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(
            locationDao = database.locationDao(),
            selectionStore = preferencesManager
        )
    }

    val alarmPlanPreferences: AlarmPlanPreferences by lazy {
        DataStoreAlarmPlanPreferences(preferencesManager)
    }

    val prayerAlarmScheduler: PrayerAlarmScheduler by lazy {
        AndroidAlarmScheduler(this)
    }

    override fun onCreate() {
        super.onCreate()
        DismissedSeries.init(this)
        NotificationHelper.init(this)
        // Seed exactly once on a fresh install and repair a missing/dangling selection.
        applicationScope.launch {
            LegacyAlarms.cancel(this@PrayerAlarmApplication)
            LocationSeeder(
                locationDao = database.locationDao(),
                selectionStore = preferencesManager
            ).seedIfNeeded()
        }
    }
}

