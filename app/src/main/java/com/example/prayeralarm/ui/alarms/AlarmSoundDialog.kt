package com.example.prayeralarm.ui.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.prayeralarm.alarm.AlarmSounds
import com.example.prayeralarm.alarm.PickedSound
import com.example.prayeralarm.alarm.SoundPreviewPlayer
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Choose, preview and confirm an alarm sound.
 * Sources: the device's own alarm sounds, any audio file the user picks, or the default.
 * Nothing is saved until "Use this sound" is pressed.
 */
@Composable
fun AlarmSoundDialog(
    currentUriString: String?,
    currentName: String?,
    onUse: (PickedSound) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = remember { SoundPreviewPlayer(context) }
    var pending by remember { mutableStateOf<PickedSound?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { preview.stop() } }

    fun setPending(sound: PickedSound) {
        preview.stop()
        message = null
        pending = sound
    }

    fun uriOf(uriString: String?): Uri? = uriString?.let { Uri.parse(it) }

    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        when {
            picked == null -> Unit
            RingtoneManager.getDefaultType(picked) != -1 ->
                setPending(PickedSound(null, "Default alarm sound"))

            !AlarmSounds.canRead(context, picked) ->
                message = "This app can't access that sound. Use \"Choose audio file\" instead."

            else -> setPending(PickedSound(picked.toString(), AlarmSounds.systemSoundName(context, picked)))
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source: Uri? ->
        if (source != null) {
            scope.launch {
                importing = true
                AlarmSounds.importAudio(context, source)
                    .onSuccess { setPending(it) }
                    .onFailure { message = it.message ?: "Could not import that file." }
                importing = false
            }
        }
    }

    val dismiss = {
        preview.stop()
        AlarmSounds.deleteUnusedCustom(context, currentUriString) // drop an unsaved import
        onDismiss()
    }

    val shape = RoundedCornerShape(20.dp)
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = NavySurface,
        shape = shape,
        modifier = Modifier.border(1.dp, NavyBorder, shape),
        title = {
            Text(
                text = "Alarm sound",
                style = MaterialTheme.typography.titleLarge,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundRow(
                    label = "Current",
                    name = currentName ?: if (currentUriString == null) "Default alarm sound" else "Custom sound",
                    playing = preview.playing && pending == null,
                    onPreview = { preview.toggle(uriOf(currentUriString)) }
                )
                pending?.let { sound ->
                    SoundRow(
                        label = "New",
                        name = sound.name,
                        playing = preview.playing,
                        onPreview = { preview.toggle(uriOf(sound.uriString)) }
                    )
                }
                (message ?: preview.error)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (importing) {
                    Text("Importing…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        val existing = uriOf(currentUriString)?.takeIf { it.scheme == "content" }
                            ?: AlarmSounds.defaultUri(context)
                        systemPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Device sounds", color = TextPrimary) }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose audio file", color = TextPrimary) }
                OutlinedButton(
                    onClick = { setPending(PickedSound(null, "Default alarm sound")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Restore default sound", color = TextPrimary) }
                Text(
                    text = "Previews play on the alarm volume.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pending != null && preview.error == null && !importing,
                onClick = {
                    pending?.let { sound ->
                        preview.stop()
                        onUse(sound)
                        AlarmSounds.deleteUnusedCustom(context, sound.uriString)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) { Text("Use this sound", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun SoundRow(label: String, name: String, playing: Boolean, onPreview: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )
        }
        TextButton(onClick = onPreview) {
            Text(if (playing) "Stop" else "Preview", color = GoldPrimary, fontWeight = FontWeight.Bold)
        }
    }
}
