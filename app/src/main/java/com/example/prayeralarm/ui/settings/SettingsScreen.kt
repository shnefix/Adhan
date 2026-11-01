package com.example.prayeralarm.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.prayeralarm.alarm.CountdownNotificationService
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.batoulapps.adhan.Madhab
import com.example.prayeralarm.ui.alarms.AlarmPlanViewModel
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.GoldContainer
import com.example.prayeralarm.ui.theme.GreenActive
import com.example.prayeralarm.ui.theme.GreenActiveContainer
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyCard
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.OnGold
import com.example.prayeralarm.ui.theme.OnGoldContainer
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary

private fun notificationsAllowed(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    alarmPlanViewModel: AlarmPlanViewModel,
    onLocationChange: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedLocation by viewModel.selectedLocation.collectAsState(initial = null)
    val calculationMethod by viewModel.calculationMethod.collectAsState(initial = PrayerCalculationMethod.DEFAULT)
    val madhab by viewModel.madhab.collectAsState(initial = Madhab.SHAFI)
    val planConfig by alarmPlanViewModel.config.collectAsState()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val exactAlarmAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()

    var notificationsGranted by remember { mutableStateOf(notificationsAllowed(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        notificationsGranted = notificationsAllowed(context)
        if (planConfig.enabled) {
            CountdownNotificationService.start(context)
        }
    }

    val openExactAlarmPermission = {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    val openNotificationSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontFamily = FontFamily.Serif,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // ---- Location ----
        SectionLabel("Location")
        SettingsCard {
            selectedLocation?.let { location ->
                SettingsItem(
                    icon = Icons.Default.LocationOn,
                    title = "Active location",
                    subtitle = "Timezone: ${location.timeZoneId}",
                    onClick = onLocationChange
                ) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = TextSecondary
                    )
                }
            } ?: run {
                SettingsItem(
                    icon = Icons.Default.LocationOff,
                    title = "No active location",
                    subtitle = "Select a location to see prayer times"
                ) {}
                ActionButton("Select location", onLocationChange)
            }
        }

        // ---- Prayer calculation ----
        SectionLabel(
            title = "Prayer calculation",
            description = "Convention used to compute prayer times."
        )
        SettingsCard {
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.WbSunny,
                    title = calculationMethod.displayName,
                    subtitle = calculationMethod.summary,
                    onClick = { expanded = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select calculation method",
                        modifier = Modifier.size(28.dp),
                        tint = GoldPrimary
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(NavySurface)
                        .border(1.dp, NavyBorder, RoundedCornerShape(12.dp))
                ) {
                    PrayerCalculationMethod.values().forEach { method ->
                        val isSelected = method == calculationMethod
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = method.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isSelected) GoldPrimary else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = method.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setCalculationMethod(method)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        SectionLabel("Asr calculation", "Standard is used by Shafi'i, Maliki, and Hanbali. Hanafi gives a later Asr.")
        SettingsCard {
            SettingsItem(icon = Icons.Default.WbSunny, title = "Hanafi Asr",
                subtitle = if (madhab == Madhab.HANAFI) "Shadow = 2× object height" else "Shadow = 1× object height") {
                Switch(checked = madhab == Madhab.HANAFI,
                    onCheckedChange = { viewModel.setMadhab(if (it) Madhab.HANAFI else Madhab.SHAFI) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyDark, checkedTrackColor = GoldPrimary,
                        uncheckedThumbColor = TextSecondary, uncheckedTrackColor = NavyCard, uncheckedBorderColor = NavyBorder))
            }
        }

        // ---- Fajr alarms (moved here from the Fajr Alarms screen) ----
        SectionLabel(title = "Fajr alarms")
        SettingsCard {
            SettingsItem(
                icon = Icons.Default.NotificationsActive,
                title = "Enabled",
                subtitle = if (planConfig.enabled) {
                    "Alarms are active and will fire at the scheduled times."
                } else {
                    "Alarms are off. Turn on to schedule Fajr alarms."
                }
            ) {
                Switch(
                    checked = planConfig.enabled,
                    onCheckedChange = { enabled ->
                        alarmPlanViewModel.setEnabled(enabled)
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NavyDark,
                        checkedTrackColor = GoldPrimary,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = NavyCard,
                        uncheckedBorderColor = NavyBorder
                    )
                )
            }
            ItemDivider()
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Plan status",
                subtitle = if (planConfig.enabled) "The alarm plan is active." else "The alarm plan is disabled. No alarms will fire."
            ) {
                StatusPill(
                    text = if (planConfig.enabled) "Active" else "Disabled",
                    positive = planConfig.enabled
                )
            }
            ItemDivider()
            SettingsItem(
                icon = Icons.Default.Alarm,
                title = "Exact alarms"
            ) {
                StatusPill(
                    text = if (exactAlarmAvailable) "Available" else "Permission required",
                    positive = exactAlarmAvailable
                )
            }
            if (!exactAlarmAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ActionButton("Open exact-alarm permission", openExactAlarmPermission)
            }
        }

        // ---- Notifications ----
        SectionLabel(title = "Notifications")
        SettingsCard {
            SettingsItem(
                icon = if (notificationsGranted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                title = "Notifications"
            ) {
                StatusPill(
                    text = if (notificationsGranted) "Allowed" else "Permission required",
                    positive = notificationsGranted
                )
            }
            if (!notificationsGranted) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActionButton("Allow notifications") {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                ActionButton("Open notification settings", openNotificationSettings)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionLabel(title: String, description: String? = null) {
    Column(
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontFamily = FontFamily.Serif,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NavySurface)
            .border(1.dp, NavyBorder, shape)
            .padding(bottom = 4.dp)
    ) { content() }
}

@Composable
private fun ItemDivider() {
    Box(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(NavyBorder.copy(alpha = 0.8f))
    )
}

/** One row: gold icon, title + optional subtitle on the left, trailing content on the right. */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) { trailing() }
    }
}

@Composable
private fun StatusPill(text: String, positive: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (positive) GreenActive else OnGoldContainer,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (positive) GreenActiveContainer else GoldContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GoldPrimary)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = OnGold,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
