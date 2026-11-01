package com.example.prayeralarm.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RingInfo(val title: String, val locationName: String)

/** Observed by [AlarmRingActivity]; non-null exactly while an alarm is ringing. */
object AlarmRingState {
    val ringing = MutableStateFlow<RingInfo?>(null)
}

private fun alarmAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

/**
 * Foreground service that actually rings the alarm: loops the chosen sound on the ALARM
 * audio stream (so it follows alarm volume and the user's Do Not Disturb alarm rules),
 * vibrates, holds a wake lock, and shows a full-screen stop screen.
 *
 * Only one ring can exist: a second start while ringing is ignored. The ring stops on
 * user request or after [RING_TIMEOUT_MS].
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { stopRinging(userStopped = false) }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var series = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginRinging(intent)
            ACTION_STOP -> stopRinging(userStopped = true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun beginRinging(intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Fajr alarm"
        val locationName = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val firstStart = !ringing
        if (firstStart) {
            ringing = true
            DismissedSeries.init(this)
            intent.getIntExtra(EXTRA_REQUEST_CODE, -1).let {
                series = if (it >= 0) PrayerAlarmCoordinator.seriesOf(it) else -1
            }
            AlarmRingState.ringing.value = RingInfo(title, locationName)
        }

        // Must be called promptly after startForegroundService, even for a duplicate start.
        val notification = buildNotification(this, title, locationName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (!firstStart) return // already ringing: never start a second instance

        acquireWakeLock()
        requestAudioFocus()
        startVibration()
        handler.postDelayed(timeoutRunnable, RING_TIMEOUT_MS)

        scope.launch {
            val soundUri = withContext(Dispatchers.IO) {
                (applicationContext as PrayerAlarmApplication)
                    .alarmPlanPreferences.config.first().soundUri
            }
            if (ringing) play(soundUri, allowFallback = true)
        }
    }

    private fun play(uriString: String?, allowFallback: Boolean) {
        releasePlayer()
        val uri = uriString?.let { Uri.parse(it) } ?: AlarmSounds.defaultUri(this)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(alarmAudioAttributes())
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.setOnPreparedListener { it.start() }
            mp.setOnErrorListener { _, _, _ ->
                handler.post {
                    if (ringing && player === mp) {
                        if (uriString != null && allowFallback) {
                            play(null, allowFallback = false) // custom sound failed: use default
                        } else {
                            releasePlayer() // vibration keeps going
                        }
                    }
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            releasePlayer()
            if (uriString != null && allowFallback) play(null, allowFallback = false)
        }
    }

    private fun releasePlayer() {
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(alarmAudioAttributes())
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!v.hasVibrator()) return
        vibrator = v
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0), attributes)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "prayeralarm:ring")
            .apply { acquire(RING_TIMEOUT_MS + 30_000L) }
    }

    private fun releaseAll() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let {
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it)
        }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ringing = false
    }

    /**
     * [userStopped]: the user pressed Stop ("I woke up"), so the rest of today's series is
     * cancelled. On timeout the remaining alarms of the series keep going.
     */
    private fun stopRinging(userStopped: Boolean) {
        val stoppedSeries = series
        releaseAll()
        series = -1
        if (userStopped && stoppedSeries >= 0) {
            DismissedSeries.add(stoppedSeries)
            val app = applicationContext as PrayerAlarmApplication
            CoroutineScope(Dispatchers.IO).launch { FajrAlarmReconciler.refresh(app) }
        }
        AlarmRingState.ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseAll()
        AlarmRingState.ringing.value = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.prayeralarm.action.START_RINGING"
        const val ACTION_STOP = "com.example.prayeralarm.action.STOP_RINGING"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_LOCATION = "extra_location"
        private const val EXTRA_REQUEST_CODE = "extra_request_code"
        const val NOTIFICATION_ID = 4242
        private const val RING_TIMEOUT_MS = 5 * 60 * 1000L
        private const val OPEN_REQUEST_CODE = 3001
        private const val STOP_REQUEST_CODE = 3002

        fun start(context: Context, title: String, locationName: String, requestCode: Int = -1) {
            NotificationChannels.create(context)
            val intent = Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LOCATION, locationName)
                .putExtra(EXTRA_REQUEST_CODE, requestCode)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android refused the foreground start: at least show the full-screen alert.
                postFallbackNotification(context, title, locationName)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP)
                )
            } catch (e: IllegalStateException) {
                // Service not running; nothing to stop.
            }
        }

        @SuppressLint("MissingPermission")
        private fun postFallbackNotification(context: Context, title: String, location: String) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, buildNotification(context, title, location))
            } catch (e: SecurityException) {
                // Notifications blocked; nothing else we can do.
            }
        }

        fun buildNotification(context: Context, title: String, locationName: String): Notification {
            val openIntent = PendingIntent.getActivity(
                context,
                OPEN_REQUEST_CODE,
                Intent(context, AlarmRingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopIntent = PendingIntent.getService(
                context,
                STOP_REQUEST_CODE,
                Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (locationName.isBlank()) "Alarm is ringing" else "$locationName - alarm is ringing"
            return NotificationCompat.Builder(context, NotificationChannels.ALARM_RING_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openIntent)
                .setFullScreenIntent(openIntent, true)
                .addAction(0, "Stop", stopIntent)
                .build()
        }
    }
}
