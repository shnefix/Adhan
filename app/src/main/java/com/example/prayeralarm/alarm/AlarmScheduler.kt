package com.example.prayeralarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.prayeralarm.MainActivity
import java.time.LocalDate

data class ScheduledPrayerAlarm(
    val requestCode: Int,
    val triggerAtMillis: Long,
    val prayerName: String,
    val locationName: String
)

sealed interface AlarmScheduleResult {
    data object Scheduled : AlarmScheduleResult
    data object Cancelled : AlarmScheduleResult
    data object ExactAlarmPermissionRequired : AlarmScheduleResult
    data class Invalid(val message: String) : AlarmScheduleResult
}

interface PrayerAlarmScheduler {
    fun schedule(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult
    fun cancel(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult
}

/**
 * Schedules real alarms with [AlarmManager.setAlarmClock].
 *
 * Why setAlarmClock instead of setExactAndAllowWhileIdle: it is the API intended for
 * user-facing alarms. It is exempt from Doze/standby batching, shows the system alarm icon,
 * and its firing grants the app permission to start the ringing foreground service.
 * It still needs SCHEDULE_EXACT_ALARM on Android 12+ (denied by default on Android 14+).
 *
 * Identity: one PendingIntent per request code. Scheduling the same code again REPLACES
 * the previous alarm, so editing settings can never create duplicates.
 */
class AndroidAlarmScheduler(context: Context) : PrayerAlarmScheduler {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult {
        if (alarms.isEmpty()) return AlarmScheduleResult.Scheduled
        if (alarms.any { it.triggerAtMillis <= 0L || it.requestCode < 0 }) {
            return AlarmScheduleResult.Invalid("Alarm identity and trigger time must be positive.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return AlarmScheduleResult.ExactAlarmPermissionRequired
        }
        return try {
            alarms.forEach { alarm ->
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, showIntent()),
                    pendingIntent(alarm)
                )
            }
            AlarmScheduleResult.Scheduled
        } catch (securityException: SecurityException) {
            AlarmScheduleResult.Invalid(
                securityException.message ?: "Android rejected exact alarm scheduling."
            )
        }
    }

    override fun cancel(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult {
        alarms.forEach { cancelCode(it.requestCode) }
        return AlarmScheduleResult.Cancelled
    }

    private fun baseIntent(): Intent =
        Intent(appContext, PrayerAlarmReceiver::class.java)
            .setAction(PrayerAlarmReceiver.ACTION_FAJR_ALARM)

    private fun pendingIntent(alarm: ScheduledPrayerAlarm): PendingIntent {
        val intent = baseIntent().apply {
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, alarm.prayerName)
            putExtra(PrayerAlarmReceiver.EXTRA_LOCATION_NAME, alarm.locationName)
            putExtra(PrayerAlarmReceiver.EXTRA_REQUEST_CODE, alarm.requestCode)
        }
        return PendingIntent.getBroadcast(
            appContext,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Cancels without creating a PendingIntent that does not exist. */
    private fun cancelCode(requestCode: Int) {
        val existing = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            baseIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        SHOW_REQUEST_CODE,
        Intent(appContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val SHOW_REQUEST_CODE = 2001
    }
}

/**
 * Cancels alarms created by the pre-redesign scheduler (no intent action, request code
 * epochDay * 31 + sequence). Without this, upgrading users could keep stale alarms.
 * Safe to call repeatedly; the new alarms use an intent action so they never match.
 */
object LegacyAlarms {
    fun cancel(context: Context, today: LocalDate = LocalDate.now()) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (dayOffset in -1L..3L) {
            val epochDay = today.plusDays(dayOffset).toEpochDay()
            for (sequence in 0..10) {
                val code = Math.floorMod((epochDay * 31L + sequence).toInt(), Int.MAX_VALUE)
                val pending = PendingIntent.getBroadcast(
                    appContext,
                    code,
                    Intent(appContext, PrayerAlarmReceiver::class.java),
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pending != null) {
                    alarmManager.cancel(pending)
                    pending.cancel()
                }
            }
        }
    }
}
