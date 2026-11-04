package com.example.prayeralarm.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.prayeralarm.MainActivity
import com.example.prayeralarm.R
import com.example.prayeralarm.alarm.NotificationChannels
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.home.PrayerScheduleItem
import java.time.format.DateTimeFormatter

const val NOTIFICATION_ID_NEXT_PRAYER = 12345

/**
 * Handles posting, updating, and canceling the persistent "next prayer" notification.
 * A stable notification ID is reused so updates replace the existing notification
 * instead of creating duplicates.
 *
 * Integration points:
 * - [init] is called from [com.example.prayeralarm.PrayerAlarmApplication.onCreate]
 * - [postWhenEnabled] is called when the alarm plan is enabled and data is available
 * - [updateCountdown] may be called by the lifecycle-independent countdown service
 * - [cancel] is called when the alarm plan is disabled
 * - [postWhenEnabled] is also called from PrayerAlarmRecoveryReceiver after reboot/etc.
 */
object NotificationHelper {

    private const val CONTENT_INTENT_REQUEST_CODE = 1001

    @Volatile
    private var channelsCreated = false

    /** Must be called once during Application.onCreate. */
    fun init(context: Context) {
        ensureChannels(context)
    }

    /** Post the persistent notification when the alarm plan becomes enabled. */
    fun postWhenEnabled(
        context: Context,
        selectedLocation: SavedLocation,
        nextPrayer: PrayerScheduleItem?,
        remainingMillis: Long
    ) {
        if (!isNotificationEnabled(context)) return

        val appContext = context.applicationContext
        ensureChannels(appContext)

        if (nextPrayer == null) {
            cancel(appContext)
            return
        }

        manager(appContext).notify(
            NOTIFICATION_ID_NEXT_PRAYER,
            buildNotification(appContext, nextPrayer.type.displayName, nextPrayer.time.format(timeFormatter), remainingMillis)
        )
    }

    /** Update the notification content (replaces the existing notification). */
    fun updateCountdown(
        context: Context,
        nextPrayer: PrayerScheduleItem?,
        remainingMillis: Long,
        selectedLocation: SavedLocation
    ) {
        postWhenEnabled(context, selectedLocation, nextPrayer, remainingMillis)
    }

    /** Cancel the persistent notification. */
    fun cancel(context: Context) {
        manager(context.applicationContext).cancel(NOTIFICATION_ID_NEXT_PRAYER)
    }

    /**
     * Builds the same stable notification used by the foreground service for
     * both its initial foreground notification and subsequent updates.
     */
    fun buildNotification(
        context: Context,
        prayerName: String,
        prayerTime: String,
        remainingMillis: Long
    ): android.app.Notification {
        val appContext = context.applicationContext
        ensureChannels(appContext)
        // Above 10 minutes: "9h 54m" text, re-posted each minute. Below: the chronometer
        // counts the seconds down to this point by itself.
        val countdownBase = SystemClock.elapsedRealtime() + remainingMillis.coerceAtLeast(0L)
        val contentText = "$prayerName at $prayerTime"
        val compactView = notificationView(
            appContext,
            R.layout.notification_countdown,
            prayerName,
            remainingMillis,
            countdownBase,
            prayerTime
        )
        val expandedView = notificationView(
            appContext,
            R.layout.notification_countdown_expanded,
            prayerName,
            remainingMillis,
            countdownBase,
            prayerTime
        )

        return NotificationCompat.Builder(
            appContext,
            NotificationChannels.PRAYER_COUNTDOWN_CHANNEL
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
            setColor(ContextCompat.getColor(appContext, R.color.notification_bg))
            setColorized(true)
            setContentTitle(prayerName)
            setContentText(contentText)
            setStyle(
                NotificationCompat.DecoratedCustomViewStyle()
            )
            setCustomContentView(compactView)
            setCustomBigContentView(expandedView)
            setContentIntent(openAppIntent(appContext))
            setPriority(NotificationCompat.PRIORITY_LOW)
            setCategory(NotificationCompat.CATEGORY_STATUS)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setShowWhen(false)
            setOnlyAlertOnce(true)
            setSilent(true)
            setOngoing(true)
            setAutoCancel(false)
        }.build()
    }

    private fun notificationView(
        context: Context,
        layoutId: Int,
        prayerName: String,
        remainingMillis: Long,
        countdownBase: Long,
        prayerTime: String
    ): RemoteViews {
        return RemoteViews(context.packageName, layoutId).apply {
            setTextViewText(R.id.notification_prayer_name, prayerName)
            if (CountdownFormat.showsSeconds(remainingMillis)) {
                setViewVisibility(R.id.notification_countdown_text, View.GONE)
                setViewVisibility(R.id.notification_countdown, View.VISIBLE)
                setChronometer(R.id.notification_countdown, countdownBase, null, true)
                setChronometerCountDown(R.id.notification_countdown, true)
            } else {
                setViewVisibility(R.id.notification_countdown, View.GONE)
                setViewVisibility(R.id.notification_countdown_text, View.VISIBLE)
                setTextViewText(R.id.notification_countdown_text, CountdownFormat.hoursMinutes(remainingMillis))
            }
            setTextViewText(R.id.notification_prayer_time, prayerTime)
        }
    }

    /** Check if notification permission is granted and notifications are enabled. */
    fun isNotificationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled()
    }

    private fun ensureChannels(context: Context) {
        if (channelsCreated) return
        NotificationChannels.create(context.applicationContext)
        channelsCreated = true
    }

    private fun manager(context: Context): NotificationManagerCompat {
        return NotificationManagerCompat.from(context.applicationContext)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            CONTENT_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
}
