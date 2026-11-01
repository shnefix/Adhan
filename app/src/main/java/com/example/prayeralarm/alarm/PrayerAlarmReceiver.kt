package com.example.prayeralarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.prayeralarm.PrayerAlarmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fires when AlarmManager delivers a Fajr / Qiyam alarm. It does NOT post an ordinary
 * notification: it starts [AlarmRingService], which plays the chosen sound on the alarm
 * audio stream, then extends the rolling schedule window.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Alarms scheduled by the pre-redesign version carry no action; ignore any leftovers.
        if (intent.action != ACTION_FAJR_ALARM) return

        val title = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: "Fajr alarm"
        val locationName = intent.getStringExtra(EXTRA_LOCATION_NAME) ?: ""
        val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
        val application = context.applicationContext as PrayerAlarmApplication
        DismissedSeries.init(application)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Safety net against a stale alarm firing after the user switched it off.
                // If storage is slow or unreadable we fail OPEN: missing Fajr is worse.
                val stillWanted = withTimeoutOrNull(2_000L) {
                    val config = application.alarmPlanPreferences.config.first()
                    config.enabled && config.activeMode != null
                } ?: true
                val alreadyStopped = requestCode >= 0 &&
                    DismissedSeries.contains(PrayerAlarmCoordinator.seriesOf(requestCode))
                if (stillWanted && !alreadyStopped) {
                    AlarmRingService.start(application, title, locationName, requestCode)
                }
                FajrAlarmReconciler.refresh(application)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FAJR_ALARM = "com.example.prayeralarm.action.FAJR_ALARM"
        const val EXTRA_PRAYER_NAME = "extra_prayer_name"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
        const val EXTRA_REQUEST_CODE = "extra_request_code"
    }
}
