package com.example.prayeralarm.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prayeralarm.data.repository.LocationRepository
import com.example.prayeralarm.alarm.CountdownNotificationService
import com.example.prayeralarm.domain.calculator.CalculatedTimetable
import com.example.prayeralarm.domain.calculator.NextPrayerCountdown
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import kotlinx.coroutines.flow.Flow
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.alarms.AlarmPlanPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * ViewModel managing Home Screen prayer timetable and real-time countdown.
 *
 * Implements real astronomical calculation based on the active [SavedLocation].
 * Uses an injectable [Clock] for deterministic testing.
 */
class HomeViewModel(
    private val locationRepository: LocationRepository? = null,
    private val clock: Clock = Clock.systemUTC(),
    private val alarmPlanPreferences: AlarmPlanPreferences? = null,
    private val notificationContext: Context? = null,
    private val calculationSettings: Flow<PrayerCalculationSettings>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var activeLocation: SavedLocation? = null
    private var cachedTimetable: CalculatedTimetable? = null
    private var cachedTimetableDate: LocalDate? = null
    private var cachedTomorrowTimetable: CalculatedTimetable? = null
    private var persistentNotificationEnabled = false
    private var settings = PrayerCalculationSettings()

    init {
        observeActiveLocation()
        observeNotificationPlan()
        observeCalculationSettings()
        startCountdownTicker()
    }

    /**
     * Keeps the persistent notification in sync with the persisted alarm-plan toggle.
     * Disabling the plan cancels the notification immediately.
     */
    private fun observeNotificationPlan() {
        val preferences = alarmPlanPreferences ?: return
        viewModelScope.launch {
            preferences.config.collect { config ->
                persistentNotificationEnabled = config.enabled
                        if (!config.enabled) {
                            notificationContext?.let { CountdownNotificationService.stop(it) }
                        }
            }
        }
    }

    private fun observeCalculationSettings() {
        val flow = calculationSettings ?: return
        viewModelScope.launch {
            flow.collect { s -> settings = s; activeLocation?.let { recalculateTimetable(it) } }
        }
    }

    private fun observeActiveLocation() {
        locationRepository?.let { repo ->
            viewModelScope.launch {
                repo.getActiveLocation().collect { location ->
                    if (location != null) {
                        activeLocation = location
                        recalculateTimetable(location)
                    } else {
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                errorMessage = "No active location available."
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Recalculates prayer times for the provided location and updates the countdown.
     * Avoids recalculating table values on every countdown tick by caching the day's timetable.
     */
    private fun recalculateTimetable(location: SavedLocation) {
        val zoneId = PrayerTimeCalculator.zoneIdFor(location)

        val today = LocalDate.now(clock.withZone(zoneId))
        val timetable = PrayerTimeCalculator.calculateTimetable(location, today, settings.method, settings.madhab)
        cachedTimetable = timetable
        cachedTimetableDate = today
        cachedTomorrowTimetable = PrayerTimeCalculator.calculateTimetable(location, today.plusDays(1), settings.method, settings.madhab)

        val now = Instant.now(clock)
        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = location,
            currentDate = today,
            now = now,
            todayTimetable = timetable,
            tomorrowTimetable = cachedTomorrowTimetable
        )

        val updatedItems = applyNextPrayerHighlight(timetable.items, countdown)

        _uiState.update { current ->
            current.copy(
                selectedLocation = location,
                currentDate = today,
                prayerTimetable = updatedItems,
                nextPrayer = countdown.nextPrayerItem,
                remainingHours = countdown.remainingHours,
                remainingMinutes = countdown.remainingMinutes,
                remainingSeconds = countdown.remainingSeconds,
                isSampleData = false,
                isLoading = false,
                errorMessage = null
            )
        }
    }

    /**
     * Marks at most one row in today's timetable as the next prayer.
     *
     * After Isha the countdown targets tomorrow's Fajr. Tomorrow's time is shown
     * in the next-prayer card only; no row in today's list is marked as "next"
     * because none of today's rows represents that upcoming prayer.
     */
    private fun applyNextPrayerHighlight(
        items: List<PrayerScheduleItem>,
        countdown: NextPrayerCountdown
    ): List<PrayerScheduleItem> {
        val nextTodayType = if (countdown.isPastAllPrayersForDay) {
            null
        } else {
            countdown.nextPrayerItem?.type
        }
        return items.map { item -> item.copy(isNextPrayer = item.type == nextTodayType) }
    }

    /**
     * Precise ticker running once per second.
     * Only updates the countdown and next-prayer pointer against pre-calculated timestamps.
     * If midnight passes (date changes), triggers a full timetable recalculation.
     */
    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                tickCountdown()
            }
        }
    }

    /**
     * Advances the countdown by comparing the current instant with the upcoming prayer's timestamp.
     */
    fun tickCountdown() {
        val location = activeLocation ?: return
        val timetable = cachedTimetable ?: return
        val timetableDate = cachedTimetableDate ?: return

        val zoneId = PrayerTimeCalculator.zoneIdFor(location)

        val currentDate = LocalDate.now(clock.withZone(zoneId))

        // If the day has rolled over at midnight, recalculate today's timetable!
        if (currentDate != timetableDate) {
            recalculateTimetable(location)
            return
        }

        val now = Instant.now(clock)
        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = location,
            currentDate = currentDate,
            now = now,
            todayTimetable = timetable,
            tomorrowTimetable = cachedTomorrowTimetable
        )

        val updatedItems = applyNextPrayerHighlight(timetable.items, countdown)

        _uiState.update { current ->
            current.copy(
                nextPrayer = countdown.nextPrayerItem,
                prayerTimetable = updatedItems,
                remainingHours = countdown.remainingHours,
                remainingMinutes = countdown.remainingMinutes,
                remainingSeconds = countdown.remainingSeconds
            )
        }
    }

    fun togglePrayerNotification(prayerType: PrayerType) {
        _uiState.update { state ->
            val updated = state.prayerTimetable.map { item ->
                if (item.type == prayerType) {
                    item.copy(isNotificationEnabled = !item.isNotificationEnabled)
                } else {
                    item
                }
            }
            state.copy(prayerTimetable = updated)
        }
    }

    class Factory(
        private val repository: LocationRepository,
        private val clock: Clock = Clock.systemUTC(),
        private val alarmPlanPreferences: AlarmPlanPreferences? = null,
        private val notificationContext: Context? = null,
        private val calculationSettings: Flow<PrayerCalculationSettings>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(
                    repository,
                    clock,
                    alarmPlanPreferences,
                    notificationContext,
                    calculationSettings
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        /**
         * Sample state strictly for Compose @Preview rendering in IDE.
         */
        fun createPreviewState(): HomeUiState {
            val sampleLocation = SavedLocation(
                id = 1,
                name = "Mahdia (Preview)",
                latitude = 35.5047,
                longitude = 11.0622,
                timeZoneId = "Africa/Tunis",
                isDefault = true
            )
            val timetable = PrayerTimeCalculator.calculateTimetable(sampleLocation, LocalDate.now())
            return HomeUiState(
                selectedLocation = sampleLocation,
                currentDate = LocalDate.now(),
                hijriDate = "18 Jumada al-Awwal 1448",
                nextPrayer = timetable.items[1],
                remainingHours = 2,
                remainingMinutes = 15,
                remainingSeconds = 30,
                prayerTimetable = timetable.items,
                isSampleData = true
            )
        }
    }
}
