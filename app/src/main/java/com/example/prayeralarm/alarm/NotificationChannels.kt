package com.example.prayeralarm.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val PRAYER_ALARM_CHANNEL = "prayer_alarm"
    const val PRAYER_COUNTDOWN_CHANNEL = "prayer_countdown"

    /** Ringing alarm. Silent as a channel: the sound is played by AlarmRingService. */
    const val ALARM_RING_CHANNEL = "fajr_alarm_ring"

    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmChannel = NotificationChannel(
            PRAYER_ALARM_CHANNEL,
            "Prayer alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for scheduled prayer alarms."
        }

        val countdownChannel = NotificationChannel(
            PRAYER_COUNTDOWN_CHANNEL,
            "Next prayer countdown",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent countdown to the next prayer."
            setShowBadge(false)
        }

        val ringChannel = NotificationChannel(
            ALARM_RING_CHANNEL,
            "Ringing alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shown while a Fajr or Qiyam Al-Layl alarm is ringing."
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(alarmChannel, countdownChannel, ringChannel))
    }
}
