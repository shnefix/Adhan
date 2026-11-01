package com.example.prayeralarm.alarm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prayeralarm.data.repository.LocationRepository
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.ui.alarms.AlarmPlanPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

/**
 * Keeps AlarmManager in sync while the app is open: any change to the active location,
 * alarm settings (mode, minutes, sound, switches) or calculation method/Asr re-plans the
 * alarms immediately. Reconciliation is stateless and idempotent (see PrayerAlarmCoordinator),
 * so repeated emissions can never produce duplicates.
 */
class PrayerAlarmSchedulingViewModel(
    locationRepository: LocationRepository,
    alarmPreferences: AlarmPlanPreferences,
    private val scheduler: PrayerAlarmScheduler,
    calculationSettings: Flow<PrayerCalculationSettings>,
    private val clock: Clock = Clock.systemUTC()
) : ViewModel() {

    private val _status = MutableStateFlow<AlarmReconciliationResult?>(null)
    val status: StateFlow<AlarmReconciliationResult?> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                locationRepository.getActiveLocation(),
                alarmPreferences.config,
                calculationSettings
            ) { location, config, settings -> Triple(location, config, settings) }
                .catch { error ->
                    _status.value = AlarmReconciliationResult.Failed(
                        AlarmScheduleResult.Invalid(error.message ?: "Unable to load alarm inputs.")
                    )
                }
                .collectLatest { (location, config, settings) ->
                    if (location == null) {
                        _status.value = AlarmReconciliationResult.Applied(emptyList(), emptyList())
                    } else {
                        _status.value = withContext(Dispatchers.Default) {
                            FajrAlarmReconciler.reconcile(scheduler, location, config, settings, clock)
                        }
                    }
                }
        }
    }

    class Factory(
        private val locationRepository: LocationRepository,
        private val alarmPreferences: AlarmPlanPreferences,
        private val scheduler: PrayerAlarmScheduler,
        private val calculationSettings: Flow<PrayerCalculationSettings>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PrayerAlarmSchedulingViewModel::class.java)) {
                return PrayerAlarmSchedulingViewModel(
                    locationRepository,
                    alarmPreferences,
                    scheduler,
                    calculationSettings
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
