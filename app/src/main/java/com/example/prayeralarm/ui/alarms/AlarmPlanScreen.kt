package com.example.prayeralarm.ui.alarms

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.alarm.AlarmPlanGenerator
import com.example.prayeralarm.domain.alarm.FajrAlarmMode
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.ui.components.ErrorBanner
import com.example.prayeralarm.ui.home.HomeUiState
import com.example.prayeralarm.ui.theme.GoldContainer
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.GreenActive
import com.example.prayeralarm.ui.theme.GreenActiveContainer
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyCard
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavyInset
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.OnGold
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Everything uses the app's navy/gold theme so this page matches the rest of the app.
private val Bg = NavyDark
private val CardBg = NavySurface
private val CardBorder = NavyBorder
private val ChipBorder = NavyBorder
private val InsetBg = NavyInset
private val Amber = GoldPrimary
private val AmberSoft = GoldContainer
private val OnAmber = OnGold
private val TextMain = TextPrimary
private val TextSoft = TextPrimary
private val Muted = TextSecondary
private val Dim = TextSecondary
private val OnPill = GreenActive
private val OnPillBg = GreenActiveContainer
private val OffPill = TextSecondary
private val OffPillBg = NavyCard

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Fajr alarm section: master switch, the three modes (Before Fajr, Qiyam Al-Layl,
 * Before & After Fajr) and the alarm sound. Only the selected mode is expanded.
 */
@Composable
fun AlarmPlanScreen(
    homeUiState: HomeUiState,
    viewModel: AlarmPlanViewModel,
    modifier: Modifier = Modifier
) {
    val config by viewModel.config.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current
    var showSoundDialog by remember { mutableStateOf(false) }

    val fajrTime: LocalTime? =
        homeUiState.prayerTimetable.firstOrNull { it.type == PrayerType.FAJR }?.time
    val date = homeUiState.currentDate
    val active = config.activeMode

    val exactAlarmOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    val alarmVolumeMuted = (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .getStreamVolume(AudioManager.STREAM_ALARM) == 0
    val notificationsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val fullScreenOk = Build.VERSION.SDK_INT < 34 ||
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).canUseFullScreenIntent()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---- Header ----
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Fajr alarms",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Row {
                Text(text = "Fajr today at ", fontSize = 15.sp, color = Muted)
                Text(
                    text = fajrTime?.format(timeFormatter) ?: "--:--",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Amber
                )
            }
        }

        // ---- Master switch ----
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Fajr alarm", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = when {
                            !config.enabled -> "Off. No alarm will ring."
                            active == null -> "On, but no mode is selected below."
                            else -> "${active.label} rings every day."
                        },
                        fontSize = 14.sp,
                        color = Muted
                    )
                }
                Pill(
                    text = if (config.enabled && active != null) "Active" else "Disabled",
                    positive = config.enabled && active != null
                )
                AlarmSwitch(config.enabled, "Fajr alarm") { viewModel.setEnabled(it) }
            }
        }

        // ---- Warnings that would stop the alarm from working ----
        if (config.enabled && !exactAlarmOk) {
            ErrorBanner("Exact alarm permission is required. Allow it in Settings > Fajr alarms.")
        }
        if (config.enabled && !fullScreenOk) {
            ActionBanner(
                message = "The stop screen can't open over the lock screen. Allow full-screen alarms.",
                action = "Allow"
            ) {
                runCatching {
                    context.startActivity(
                        Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT")
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        if (config.enabled && !notificationsOk) {
            ErrorBanner("Notifications are blocked. The alarm will still sound, but the lock-screen stop screen may not appear.")
        }
        if (config.enabled && alarmVolumeMuted) {
            ErrorBanner("Your alarm volume is 0, so the alarm will be silent. Raise it in the device volume settings.")
        }
        errorMessage?.let { ErrorBanner(message = it) }

        // ---- Before Fajr ----
        ModeCard(
            title = "Before Fajr",
            subtitle = "Wake up shortly before Fajr",
            modeEnabled = config.beforeFajrEnabled,
            alarmsOn = config.enabled,
            onToggle = { viewModel.setModeEnabled(FajrAlarmMode.BEFORE_FAJR, it) }
        ) {
            MinutesSection(
                title = "Minutes before Fajr",
                mode = FajrAlarmMode.BEFORE_FAJR,
                minutes = config.beforeFajrMinutes,
                step = 1,
                onMinutes = { viewModel.setMinutes(FajrAlarmMode.BEFORE_FAJR, it) }
            )
            CountSection(config.beforeFajrCount) { viewModel.setCount(FajrAlarmMode.BEFORE_FAJR, it) }
            RingsAt(config, FajrAlarmMode.BEFORE_FAJR, date, fajrTime)
        }

        // ---- Qiyam Al-Layl ----
        ModeCard(
            title = "Qiyam Al-Layl",
            subtitle = "Wake in the night for Qiyam",
            modeEnabled = config.qiyamEnabled,
            alarmsOn = config.enabled,
            onToggle = { viewModel.setModeEnabled(FajrAlarmMode.QIYAM_AL_LAYL, it) }
        ) {
            MinutesSection(
                title = "Minutes before Fajr",
                mode = FajrAlarmMode.QIYAM_AL_LAYL,
                minutes = config.qiyamMinutes,
                step = 5,
                onMinutes = { viewModel.setMinutes(FajrAlarmMode.QIYAM_AL_LAYL, it) }
            )
            CountSection(config.qiyamCount) { viewModel.setCount(FajrAlarmMode.QIYAM_AL_LAYL, it) }
            RingsAt(config, FajrAlarmMode.QIYAM_AL_LAYL, date, fajrTime)
        }

        // ---- Before & After Fajr ----
        ModeCard(
            title = "Before & After Fajr",
            subtitle = "Alarms around Fajr, one minute apart",
            modeEnabled = config.aroundEnabled,
            alarmsOn = config.enabled,
            onToggle = { viewModel.setModeEnabled(FajrAlarmMode.AROUND_FAJR, it) }
        ) {
            StepperRow(
                title = "Alarms before Fajr",
                value = config.aroundBefore,
                range = 0..FajrAlarmMode.MAX_COUNT,
                onChange = { viewModel.setAroundCounts(before = it) }
            )
            StepperRow(
                title = "Alarms after Fajr",
                value = config.aroundAfter,
                range = 0..FajrAlarmMode.MAX_COUNT,
                onChange = { viewModel.setAroundCounts(after = it) }
            )
            Text(
                "An alarm also rings exactly at Fajr. Up to ${FajrAlarmMode.MAX_COUNT} before and ${FajrAlarmMode.MAX_COUNT} after.",
                fontSize = 13.sp,
                color = Dim
            )
            RingsAt(config, FajrAlarmMode.AROUND_FAJR, date, fajrTime)
        }

        Text(
            text = "Only one mode is active at a time. Turning one on turns the others off. " +
                "Tap Stop on the alarm screen to cancel the rest of that day's alarms.",
            fontSize = 13.sp,
            color = Dim,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // ---- Sound ----
        Card {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Alarm sound", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        text = config.soundName
                            ?: if (config.soundUri == null) "Default alarm sound" else "Custom sound",
                        fontSize = 14.sp,
                        color = Muted,
                        maxLines = 2
                    )
                }
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Amber)
                        .clickable(role = Role.Button) { showSoundDialog = true }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Change", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnAmber)
                }
            }
        }
        Text(
            text = "The alarm plays on the alarm volume and follows your Do Not Disturb settings for alarms. " +
                "Some phones also need battery restrictions removed for this app.",
            fontSize = 13.sp,
            color = Dim,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    if (showSoundDialog) {
        AlarmSoundDialog(
            currentUriString = config.soundUri,
            currentName = config.soundName,
            onUse = { picked ->
                viewModel.setSound(picked.uriString, picked.name)
                showSoundDialog = false
            },
            onDismiss = { showSoundDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Mode card and its sections
// ---------------------------------------------------------------------------------------------

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    modeEnabled: Boolean,
    alarmsOn: Boolean,
    onToggle: (Boolean) -> Unit,
    body: @Composable () -> Unit
) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(subtitle, fontSize = 14.sp, color = Muted, lineHeight = 19.sp)
                }
                Pill(
                    text = if (modeEnabled && alarmsOn) "Scheduled" else "Off",
                    positive = modeEnabled && alarmsOn
                )
                AlarmSwitch(modeEnabled, title) { onToggle(it) }
            }
            if (modeEnabled) body()
        }
    }
}

@Composable
private fun MinutesSection(
    title: String,
    mode: FajrAlarmMode,
    minutes: Int,
    step: Int,
    onMinutes: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperRow(title, minutes, mode.range, step, onMinutes)
        ChipGrid(
            items = mode.presets,
            columns = 3,
            selected = minutes,
            label = { "$it min" },
            onClick = onMinutes
        )
        Text("Anywhere from ${mode.minMinutes} to ${mode.maxMinutes} minutes", fontSize = 13.sp, color = Dim)
    }
}

@Composable
private fun CountSection(count: Int, onCount: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperRow("Number of alarms", count, 1..FajrAlarmMode.MAX_COUNT, 1, onCount)
        ChipGrid(
            items = listOf(1, 2, 3, 5, 7, 10),
            columns = 6,
            selected = count,
            label = { it.toString() },
            onClick = onCount
        )
        Text("Up to ${FajrAlarmMode.MAX_COUNT} alarms, one minute apart", fontSize = 13.sp, color = Dim)
    }
}

/** "RINGS AT" box: one pill per alarm, computed with the same code that schedules them. */
@Composable
private fun RingsAt(config: AlarmPlanConfig, mode: FajrAlarmMode, date: LocalDate, fajrTime: LocalTime?) {
    val times: List<LocalDateTime> = fajrTime?.let { f ->
        val fajrAt = LocalDateTime.of(date, f)
        AlarmPlanGenerator.offsetsMinutes(config, mode).map { fajrAt.minusMinutes(it) }
    }.orEmpty()
    val fajrMoment = fajrTime?.let { LocalDateTime.of(date, it) }
    val startsPreviousDay = times.firstOrNull()?.toLocalDate()?.let { it != date } == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(InsetBg)
            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("RINGS AT", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp, color = Muted)
        if (times.isEmpty()) {
            Text("--:--", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber)
        } else {
            times.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        val atFajr = t == fajrMoment
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(if (atFajr) Amber else AmberSoft)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t.format(timeFormatter),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (atFajr) OnAmber else Amber
                            )
                        }
                    }
                }
            }
            if (startsPreviousDay) {
                Text("The first alarm rings the day before.", fontSize = 13.sp, color = Dim)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBg)
            .border(1.dp, CardBorder, shape)
            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp)
    ) { content() }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSoft, modifier = Modifier.weight(1f))
        Stepper(value, range, step, onChange)
    }
}

/** [-] value [+]; the value is editable so any number in range can be typed. */
@Composable
private fun Stepper(value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, ChipBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StepButton("−", "Decrease", enabled = value > range.first) {
            onChange((value - step).coerceAtLeast(range.first))
        }
        BasicTextField(
            value = text,
            onValueChange = { input ->
                val digits = input.filter { it.isDigit() }.take(3)
                text = digits
                digits.toIntOrNull()?.takeIf { it in range }?.let(onChange)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(Amber),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .width(44.dp)
                .onFocusChanged { if (!it.isFocused) text = value.toString() }
        )
        StepButton("+", "Increase", enabled = value < range.last) {
            onChange((value + step).coerceAtMost(range.last))
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(40.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) TextSoft else Dim.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

/** Equal-width chips in rows of [columns]; the chip equal to [selected] is amber. */
@Composable
private fun ChipGrid(
    items: List<Int>,
    columns: Int,
    selected: Int,
    label: (Int) -> String,
    onClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val isSelected = item == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Amber else InsetBg)
                            .then(
                                if (isSelected) Modifier
                                else Modifier.border(BorderStroke(1.dp, ChipBorder), RoundedCornerShape(16.dp))
                            )
                            .clickable(role = Role.RadioButton) { onClick(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label(item),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) OnAmber else TextMain
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Pill(text: String, positive: Boolean) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (positive) OnPillBg else OffPillBg)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (positive) OnPill else OffPill
        )
    }
}

@Composable
private fun AlarmSwitch(checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = NavyDark,
            checkedTrackColor = Amber,
            checkedBorderColor = Amber,
            uncheckedThumbColor = Dim,
            uncheckedTrackColor = NavyCard,
            uncheckedBorderColor = NavyBorder
        )
    )
}

@Composable
private fun ActionBanner(message: String, action: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AmberSoft)
            .border(1.dp, Amber.copy(alpha = 0.5f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message, fontSize = 14.sp, color = TextMain, modifier = Modifier.weight(1f))
        Text(
            text = action,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = OnAmber,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Amber)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
