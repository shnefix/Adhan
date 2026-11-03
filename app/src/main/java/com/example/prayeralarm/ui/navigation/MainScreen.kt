package com.example.prayeralarm.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.prayeralarm.PrayerAlarmApplication
import com.example.prayeralarm.alarm.CountdownNotificationService
import com.example.prayeralarm.alarm.PrayerAlarmSchedulingViewModel
import com.example.prayeralarm.ui.alarms.AlarmPlanScreen
import com.example.prayeralarm.ui.alarms.AlarmPlanViewModel
import com.example.prayeralarm.ui.home.HomeScreen
import com.example.prayeralarm.ui.home.HomeViewModel
import com.example.prayeralarm.ui.locations.LocationsScreen
import com.example.prayeralarm.ui.locations.LocationsViewModel
import com.example.prayeralarm.ui.settings.SettingsScreen
import com.example.prayeralarm.ui.settings.SettingsViewModel
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyBar
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyCard
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.OnGold
import com.example.prayeralarm.ui.theme.TextSecondary

@Composable
fun MainScreen() {
    val context = LocalContext.current.applicationContext as PrayerAlarmApplication
    val locationRepository = context.locationRepository
    val clock = java.time.Clock.systemUTC()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            repository = locationRepository,
            alarmPlanPreferences = context.alarmPlanPreferences,
            notificationContext = context.applicationContext,
            calculationSettings = context.preferencesManager.calculationSettings,
            clock = clock
        )
    )
    val locationsViewModel: LocationsViewModel = viewModel(
        factory = LocationsViewModel.Factory(
            locationRepository,
            context.preferencesManager.calculationSettings
        )
    )
    val alarmPlanViewModel: AlarmPlanViewModel = viewModel(
        factory = AlarmPlanViewModel.Factory(context.alarmPlanPreferences)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(locationRepository, context.preferencesManager)
    )
    viewModel<PrayerAlarmSchedulingViewModel>(
        factory = PrayerAlarmSchedulingViewModel.Factory(
            locationRepository = locationRepository,
            alarmPreferences = context.alarmPlanPreferences,
            scheduler = context.prayerAlarmScheduler,
            calculationSettings = context.preferencesManager.calculationSettings
        )
    )

    // The on/off switch now lives in Settings, so the persistent countdown notification
    // is started/stopped here, where it is always active.
    val alarmConfig by alarmPlanViewModel.config.collectAsState()
    LaunchedEffect(alarmConfig.enabled) {
        if (alarmConfig.enabled) {
            CountdownNotificationService.start(context)
        } else {
            CountdownNotificationService.stop(context)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun navigateToTopLevel(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val screens = listOf(
        Screen.Home,
        Screen.Alarms,
        Screen.Settings
    )

    Scaffold(
        containerColor = NavyDark,
        bottomBar = {
            FloatingBottomBar(
                screens = screens,
                // Locations is opened from Settings, so keep the Settings tab lit there.
                selectedRoute = if (currentRoute == Screen.Locations.route) Screen.Settings.route else currentRoute,
                onSelect = { navigateToTopLevel(it.route) }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val uiState by homeViewModel.uiState.collectAsState()
                HomeScreen(
                    uiState = uiState,
                    onToggleNotification = { prayerType ->
                        homeViewModel.togglePrayerNotification(prayerType)
                    }
                )
            }

            composable(Screen.Locations.route) {
                val locationsUiState by locationsViewModel.uiState.collectAsState()
                LocationsScreen(
                    uiState = locationsUiState,
                    onSelectLocation = { location ->
                        locationsViewModel.selectLocation(location)
                    },
                    onOpenAddDialog = {
                        locationsViewModel.openAddDialog()
                    },
                    onOpenEditDialog = { location ->
                        locationsViewModel.openEditDialog(location)
                    },
                    onDismissDialog = {
                        locationsViewModel.dismissAddEditDialog()
                    },
                    onSaveLocation = locationsViewModel::saveLocation,
                    onRequestDelete = { location ->
                        locationsViewModel.requestDeleteLocation(location)
                    },
                    onCancelDelete = {
                        locationsViewModel.cancelDeleteLocation()
                    },
                    onConfirmDelete = {
                        locationsViewModel.confirmDeleteLocation()
                    }
                )
            }

            composable(Screen.Alarms.route) {
                val uiState by homeViewModel.uiState.collectAsState()
                AlarmPlanScreen(homeUiState = uiState, viewModel = alarmPlanViewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    alarmPlanViewModel = alarmPlanViewModel,
                    onLocationChange = { navigateToTopLevel(Screen.Locations.route) }
                )
            }
        }
    }
}

/** Rounded bar that floats above the content; the active tab is a gold pill. */
@Composable
private fun FloatingBottomBar(
    screens: List<Screen>,
    selectedRoute: String?,
    onSelect: (Screen) -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(NavyBar)
            .border(1.dp, NavyBorder, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        screens.forEach { screen ->
            BarItem(
                screen = screen,
                selected = screen.route == selectedRoute,
                onClick = { onSelect(screen) }
            )
        }
    }
}

@Composable
private fun RowScope.BarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) OnGold else TextSecondary
    Column(
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) GoldPrimary else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = screen.title,
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
