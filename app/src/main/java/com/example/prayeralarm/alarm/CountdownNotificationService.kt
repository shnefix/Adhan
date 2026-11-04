package com.example.prayeralarm.alarm

import android.Manifest
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.data.notification.CountdownFormat
import com.example.prayeralarm.data.notification.NOTIFICATION_ID_NEXT_PRAYER
import com.example.prayeralarm.data.notification.NotificationHelper
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import com.example.prayeralarm.domain.model.SavedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class CountdownNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updateJob: Job? = null

    // Delays don't advance while the device is in deep sleep, so refresh the minutes
    // as soon as the screen comes back on.
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = startUpdates()
    }

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
        ContextCompat.registerReceiver(
            this,
            screenOnReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
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

    /**
     * Re-posts the notification only when the shown minute changes; in the last 10 minutes
     * the chronometer in the notification counts down by itself. Restarted on every start command, so a time or
     * time-zone change (which restarts the service) recomputes the schedule right away.
     */
    private fun startUpdates() {
        updateJob?.cancel()
        val application = applicationContext as PrayerAlarmApplication
        updateJob = serviceScope.launch {
            combine(
                application.alarmPlanPreferences.config,
                application.locationRepository.getActiveLocation(),
                application.preferencesManager.calculationSettings
            ) { config, location, settings -> Triple(config, location, settings) }
                .collectLatest { (config, location, settings) ->
                    if (!config.enabled) {
                        stopSelf()
                        return@collectLatest
                    }
                    if (location == null) {
                        NotificationHelper.cancel(this@CountdownNotificationService)
                        return@collectLatest
                    }
                    while (isActive) {
                        val waitMillis = try {
                            postNextPrayer(location, settings)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // One bad update must not crash the app and take the notification down.
                            Log.w(TAG, "Countdown update failed", e)
                            RETRY_DELAY_MILLIS
                        }
                        delay(waitMillis)
                    }
                }
        }
    }

    /** Posts the countdown to the next prayer and returns how long to wait before the next update. */
    private fun postNextPrayer(location: SavedLocation, settings: PrayerCalculationSettings): Long {
        val clock = Clock.systemUTC()
        val zone = PrayerTimeCalculator.zoneIdFor(location)
        val currentDate = LocalDate.now(clock.withZone(zone))
        val today = PrayerTimeCalculator.calculateTimetable(location, currentDate, settings.method, settings.madhab)
        val tomorrow = PrayerTimeCalculator.calculateTimetable(location, currentDate.plusDays(1), settings.method, settings.madhab)
        val now = Instant.now(clock)
        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = location,
            currentDate = currentDate,
            now = now,
            todayTimetable = today,
            tomorrowTimetable = tomorrow
        )
        val nextPrayer = countdown.nextPrayerItem
        val target = countdown.nextPrayerDateTime ?: return RETRY_DELAY_MILLIS
        val remainingMillis = Duration.between(now, target).toMillis()
        NotificationHelper.updateCountdown(
            context = this,
            nextPrayer = nextPrayer,
            remainingMillis = remainingMillis,
            selectedLocation = location
        )
        return CountdownFormat.millisUntilNextUpdate(remainingMillis).coerceAtLeast(1_000L)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the activity task must not stop this foreground service.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOnReceiver) }
        updateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CountdownService"
        private const val RETRY_DELAY_MILLIS = 60_000L
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
