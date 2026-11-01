package com.example.prayeralarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A sound the user picked. [uriString] == null means "device default alarm sound". */
data class PickedSound(val uriString: String?, val name: String)

object AlarmSounds {
    private const val DIR = "alarm_sounds"
    private const val MAX_BYTES = 25L * 1024 * 1024

    fun defaultUri(context: Context): Uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun systemSoundName(context: Context, uri: Uri): String =
        runCatching { RingtoneManager.getRingtone(context, uri).getTitle(context) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Selected sound"

    /** The alarm service must be able to open the sound later, so verify it now. */
    fun canRead(context: Context, uri: Uri): Boolean =
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)

    /**
     * Copies a user-chosen audio file into app-private storage so it keeps playing even if
     * the original is moved/deleted or its permission grant expires.
     */
    suspend fun importAudio(context: Context, source: Uri): Result<PickedSound> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val displayName = queryName(context, source) ?: "Custom sound"
                val extension = displayName.substringAfterLast('.', "")
                    .lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "audio" }
                val directory = File(context.filesDir, DIR).apply { mkdirs() }
                val target = File(directory, "custom_${System.currentTimeMillis()}.$extension")
                var total = 0L
                try {
                    resolver.openInputStream(source).use { input ->
                        requireNotNull(input) { "Unable to open the selected file." }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= MAX_BYTES) { "That file is larger than 25 MB." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    target.delete()
                    throw t
                }
                PickedSound(Uri.fromFile(target).toString(), displayName)
            }
        }

    /** Deletes imported files that are not [keepUriString] (replaced or cancelled picks). */
    fun deleteUnusedCustom(context: Context, keepUriString: String?) {
        File(context.filesDir, DIR).listFiles()?.forEach { file ->
            if (Uri.fromFile(file).toString() != keepUriString) file.delete()
        }
    }

    private fun queryName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
}

/** Short preview playback on the alarm stream, so the user hears what will actually ring. */
class SoundPreviewPlayer(private val context: Context) {

    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    fun toggle(uri: Uri?) {
        if (playing) stop() else play(uri)
    }

    fun play(uri: Uri?) {
        stop()
        error = null
        val target = uri ?: AlarmSounds.defaultUri(context)
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context, target)
            mp.setOnPreparedListener {
                it.start()
                playing = true
                handler.postDelayed(autoStop, PREVIEW_MS)
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                error = "This sound can't be played."
                handler.post { stop() }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            error = "This sound can't be played."
            stop()
        }
    }

    fun stop() {
        handler.removeCallbacks(autoStop)
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        playing = false
    }

    private companion object {
        const val PREVIEW_MS = 15_000L
    }
}
