package com.example.prayeralarm.alarm

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.data.notification.NOTIFICATION_ID_NEXT_PRAYER
import com.example.prayeralarm.data.notification.NotificationHelper
import com.example.prayeralarm.domain.calculator.CalculatedTimetable
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class CountdownNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // startForeground must always be called after startForegroundService, even when
        // stopping right away; otherwise the system kills the app.
        startForeground(
            NOTIFICATION_ID_NEXT_PRAYER,
            NotificationHelper.buildNotification(this, "Prayer", "--:--", 0L)
        )
        if (!NotificationHelper.isNotificationEnabled(this)) {
            stopSelf()
            return
        }
        startUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startUpdates()
        return START_STICKY
    }

    private fun startUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = serviceScope.launch {
            val application = applicationContext as PrayerAlarmApplication
            val clock = Clock.systemUTC()
            var cachedLocationId: Long? = null
            var cachedDate: LocalDate? = null
            var cachedSettings: com.example.prayeralarm.domain.calculator.PrayerCalculationSettings? = null
            var today: CalculatedTimetable? = null
            var tomorrow: CalculatedTimetable? = null

            while (isActive) {
                try {
                    val config = application.alarmPlanPreferences.config.first()
                    if (!config.enabled) {
                        stopSelf()
                        return@launch
                    }
                    val location = application.locationRepository.getActiveLocation().first()
                    if (location == null) {
                        NotificationHelper.cancel(this@CountdownNotificationService)
                        delay(1000L)
                        continue
                    }

                    val settings = application.preferencesManager.calculationSettings.first()
                    val zone = PrayerTimeCalculator.zoneIdFor(location)
                    val currentDate = LocalDate.now(clock.withZone(zone))
                    if (cachedLocationId != location.id || cachedDate != currentDate || cachedSettings != settings) {
                        cachedLocationId = location.id
                        cachedDate = currentDate
                        cachedSettings = settings
                        today = PrayerTimeCalculator.calculateTimetable(location, currentDate, settings.method, settings.madhab)
                        tomorrow = PrayerTimeCalculator.calculateTimetable(location, currentDate.plusDays(1), settings.method, settings.madhab)
                    }

                    val now = Instant.now(clock)
                    val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
                        location = location,
                        currentDate = currentDate,
                        now = now,
                        todayTimetable = checkNotNull(today),
                        tomorrowTimetable = tomorrow
                    )
                    val nextPrayer = countdown.nextPrayerItem
                    val target = countdown.nextPrayerDateTime
                    if (nextPrayer != null && target != null) {
                        NotificationHelper.updateCountdown(
                            context = this@CountdownNotificationService,
                            nextPrayer = nextPrayer,
                            remainingMillis = java.time.Duration.between(now, target).toMillis(),
                            selectedLocation = location
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad tick must not crash the app and take the notification down.
                    Log.w(TAG, "Countdown update failed", e)
                }
                delay(1000L)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the activity task must not stop this foreground service.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CountdownService"
        private const val ACTION_STOP = "com.example.prayeralarm.action.STOP_COUNTDOWN"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val intent = Intent(context, CountdownNotificationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, CountdownNotificationService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
