package com.example.prayeralarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prayeralarm.data.preferences.UserPreferencesManager
import com.example.prayeralarm.data.repository.LocationRepository
import com.batoulapps.adhan.Madhab
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 *
 * Exposes the currently selected location and the prayer-time calculation convention.
 * Persists user changes to calculation settings.
 */
class SettingsViewModel(
    private val locationRepository: LocationRepository,
    private val preferencesManager: UserPreferencesManager? = null
) : ViewModel() {

    /** The currently selected active location, or null when none is available. */
    val selectedLocation: Flow<SavedLocation?> = locationRepository.getActiveLocation()

    /** The named prayer-time calculation convention used by the app. */
    val calculationMethod: Flow<PrayerCalculationMethod> =
        preferencesManager?.calculationMethod ?: flowOf(PrayerCalculationMethod.DEFAULT)

    val madhab: Flow<Madhab> = preferencesManager?.madhab ?: flowOf(Madhab.SHAFI)
    val hijriAdjustment: Flow<Int> = preferencesManager?.hijriAdjustment ?: flowOf(0)
    fun setHijriAdjustment(days: Int) {
        preferencesManager?.let { viewModelScope.launch { it.setHijriAdjustment(days) } }
    }

    fun setMadhab(m: Madhab) { preferencesManager?.let { viewModelScope.launch { it.setMadhab(m) } } }

    fun setCalculationMethod(method: PrayerCalculationMethod) {
        preferencesManager?.let { prefs ->
            viewModelScope.launch {
                prefs.setCalculationMethod(method)
            }
        }
    }

    class Factory(
        private val locationRepository: LocationRepository,
        private val preferencesManager: UserPreferencesManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(locationRepository, preferencesManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
