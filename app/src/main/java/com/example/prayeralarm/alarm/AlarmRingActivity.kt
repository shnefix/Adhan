package com.example.prayeralarm.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.OnGold
import com.example.prayeralarm.ui.theme.PrayerAlarmTheme
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import com.example.prayeralarm.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Night sky fading into a warm dawn at the bottom of the screen.
private val SkyTop = NavyDark
private val SkyMid = Color(0xFF151C3D)
private val SkyDawn = Color(0xFF3A2A4A)
private val Horizon = Color(0xFF6B3F2E)
private val SunLight = Color(0xFFF6D58E)
private val Sun = GoldPrimary
private val SunDeep = Color(0xFFD9993F)

/** Full-screen stop screen shown over the lock screen while an alarm rings. */
class AlarmRingActivity : ComponentActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            PrayerAlarmTheme(darkTheme = true) {
                RingScreen(onFinished = { finish() })
            }
        }
    }
}

@Composable
private fun RingScreen(onFinished: () -> Unit) {
    val info by AlarmRingState.ringing.collectAsState()
    val context = LocalContext.current
    val clockFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var stopping by remember { mutableStateOf(false) }
    var fajr by remember { mutableStateOf<ZonedDateTime?>(null) }

    // Keep the clock live while the screen is shown.
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000L)
        }
    }

    // Today's Fajr at the active location, for the "Fajr 05:15 · in 20 min" line.
    LaunchedEffect(Unit) {
        val app = context.applicationContext as PrayerAlarmApplication
        val location = app.locationRepository.getActiveLocation().first() ?: return@LaunchedEffect
        val settings = app.preferencesManager.calculationSettings.first()
        val today = LocalDate.now(PrayerTimeCalculator.zoneIdFor(location))
        fajr = runCatching {
            PrayerTimeCalculator.calculateTimetable(location, today, settings.method, settings.madhab)
                .prayerDateTimes[PrayerType.FAJR]
        }.getOrNull()
    }

    // The alarm was stopped (button, notification action, or timeout): close this screen.
    LaunchedEffect(info) {
        if (info == null) onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyDawn, Horizon)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Header(
                title = info?.title.orEmpty(),
                date = LocalDate.now().format(dateFormat),
                clock = now.format(clockFormat),
                location = info?.locationName.orEmpty(),
                // Recomputed with the clock so "in 20 min" counts down.
                fajrLine = fajr?.let { fajrLine(it, now) }
            )

            SunStopButton(
                enabled = !stopping,
                onStop = {
                    stopping = true
                    AlarmRingService.stop(context)
                }
            )

            Footer()
        }
    }
}

@Composable
private fun Header(title: String, date: String, clock: String, location: String, fajrLine: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title.uppercase(Locale.ENGLISH),
            fontSize = 13.sp,
            color = Sun,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Sun.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(text = date, fontSize = 15.sp, color = TextSecondary)
        Text(
            text = clock,
            fontSize = 92.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp
        )
        if (location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(text = location, fontSize = 15.sp, color = TextSecondary)
            }
        }
        if (fajrLine != null) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = PrayerType.FAJR.arabicName,
                    fontSize = 20.sp,
                    color = Sun,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(10.dp))
                Text(text = fajrLine, fontSize = 15.sp, color = TextPrimary)
            }
        }
    }
}

/** "Fajr 05:15 · in 20 min", "Fajr 05:15 · now" or "Fajr 05:15 · 4 min ago". */
@Suppress("UNUSED_PARAMETER") // `tick` only makes callers recompute this each second.
private fun fajrLine(fajr: ZonedDateTime, tick: LocalTime): String {
    val time = fajr.format(DateTimeFormatter.ofPattern("HH:mm"))
    val seconds = Duration.between(ZonedDateTime.now(fajr.zone), fajr).seconds
    val minutes = if (seconds > 0) (seconds + 59) / 60 else seconds / 60
    val relative = when {
        minutes >= 60 -> "in ${minutes / 60}h ${minutes % 60}m"
        minutes > 0 -> "in $minutes min"
        minutes == 0L -> "now"
        else -> "${-minutes} min ago"
    }
    return "Fajr $time · $relative"
}

/** The rising sun is the Stop button: big and easy to hit half asleep. */
@Composable
private fun SunStopButton(enabled: Boolean, onStop: () -> Unit) {
    val motion = rememberInfiniteTransition(label = "sun")
    val rayAngle by motion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "rayAngle"
    )
    // Two halos expanding out of the sun, half a cycle apart.
    val haloA by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_400, easing = LinearEasing), RepeatMode.Restart),
        label = "haloA"
    )
    val haloB by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2_400, easing = LinearEasing),
            RepeatMode.Restart,
            StartOffset(1_200)
        ),
        label = "haloB"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        // Soft glow behind the sun.
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(Brush.radialGradient(listOf(Sun.copy(alpha = 0.28f), Color.Transparent)))
        )
        Canvas(modifier = Modifier.size(270.dp).rotate(rayAngle)) {
            val rays = 16
            val inner = size.minDimension / 2f * 0.74f
            for (i in 0 until rays) {
                val angle = Math.toRadians(i * 360.0 / rays)
                val outer = size.minDimension / 2f * if (i % 2 == 0) 0.98f else 0.88f
                drawLine(
                    color = SunLight.copy(alpha = if (i % 2 == 0) 0.55f else 0.3f),
                    start = center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                    end = center + Offset((cos(angle) * outer).toFloat(), (sin(angle) * outer).toFloat()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        for (progress in listOf(haloA, haloB)) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .scale(1f + progress * 0.45f)
                    .alpha((1f - progress) * 0.35f)
                    .clip(CircleShape)
                    .background(Sun)
            )
        }
        Box(
            modifier = Modifier
                .size(176.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(SunLight, Sun, SunDeep)))
                .clickable(role = Role.Button, enabled = enabled, onClick = onStop),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "STOP",
                    fontSize = 34.sp,
                    color = OnGold,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "tap to stop",
                    fontSize = 12.sp,
                    color = OnGold.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // From the Fajr adhan.
        Text(
            text = "الصلاة خير من النوم",
            fontSize = 28.sp,
            color = SunLight,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Prayer is better than sleep",
            fontSize = 15.sp,
            color = TextPrimary.copy(alpha = 0.85f),
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Stopping also cancels today's remaining alarms.",
            fontSize = 12.sp,
            color = TextPrimary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
