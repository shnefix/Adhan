package com.example.prayeralarm.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.OnGold
import com.example.prayeralarm.ui.theme.PrayerAlarmTheme
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val Amber = GoldPrimary
private val OnAmber = OnGold
private val Muted = TextSecondary
private val CardBg = NavySurface

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
    var now by remember { mutableStateOf(LocalTime.now()) }
    var stopping by remember { mutableStateOf(false) }

    // Keep the clock live while the screen is shown.
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1_000L)
        }
    }

    // The alarm was stopped (button, notification action, or timeout): close this screen.
    LaunchedEffect(info) {
        if (info == null) onFinished()
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(1_100), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = info?.title ?: "",
                fontSize = 20.sp,
                color = Amber,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = now.format(clockFormat),
                fontSize = 84.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            val location = info?.locationName.orEmpty()
            if (location.isNotBlank()) {
                Text(text = location, fontSize = 16.sp, color = Muted)
            }
        }

        // Big pulsing Stop button, easy to hit half asleep.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .clip(CircleShape)
                    .background(Amber)
            )
            Box(
                modifier = Modifier
                    .size(188.dp)
                    .clip(CircleShape)
                    .background(Amber)
                    .clickable(role = Role.Button, enabled = !stopping) {
                        stopping = true
                        AlarmRingService.stop(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "STOP",
                    fontSize = 36.sp,
                    color = OnAmber,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Tap Stop when you are awake. It also cancels today's remaining alarms.",
                    fontSize = 14.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
