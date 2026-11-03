package com.example.prayeralarm.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prayeralarm.data.repository.LocationRepository
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.model.PrayerTimeAdjustments
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.util.LocationValidator
import com.example.prayeralarm.util.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Raw text from the add/edit form, validated by [LocationsViewModel.saveLocation]. */
data class LocationFormInput(
    val name: String,
    val latitude: String,
    val longitude: String,
    val timeZoneId: String,
    val adjustments: List<String> = emptyList(),
    val calculationMethod: PrayerCalculationMethod? = null
)

/**
 * ViewModel managing location list presentation, editing, deletion with confirmation,
 * coordinate validation, and active selection.
 */
class LocationsViewModel(
    private val repository: LocationRepository,
    private val calculationSettings: Flow<PrayerCalculationSettings>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationsUiState(isLoading = true))
    val uiState: StateFlow<LocationsUiState> = _uiState.asStateFlow()

    init {
        observeLocations()
        calculationSettings?.let { flow ->
            viewModelScope.launch {
                flow.collect { settings -> _uiState.update { it.copy(calculationSettings = settings) } }
            }
        }
    }

    private fun observeLocations() {
        viewModelScope.launch {
            combine(
                repository.getAllLocations(),
                repository.getActiveLocation()
            ) { locations, active ->
                Pair(locations, active)
            }.collect { (locations, active) ->
                _uiState.update { current ->
                    current.copy(
                        locations = locations,
                        activeLocation = active,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.setActiveLocation(location.id)
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddEditDialog = true, editingLocation = null) }
    }

    fun openEditDialog(location: SavedLocation) {
        _uiState.update { it.copy(showAddEditDialog = true, editingLocation = location) }
    }

    fun dismissAddEditDialog() {
        _uiState.update { it.copy(showAddEditDialog = false, editingLocation = null) }
    }

    fun saveLocation(input: LocationFormInput): ValidationResult {
        val validation = LocationValidator.validate(
            name = input.name,
            latitudeStr = input.latitude,
            longitudeStr = input.longitude,
            timezoneId = input.timeZoneId,
            adjustmentStrs = input.adjustments
        )

        if (!validation.isValid) {
            return validation
        }

        val lat = LocationValidator.parseCoordinate(input.latitude)!!
        val lon = LocationValidator.parseCoordinate(input.longitude)!!
        val tz = input.timeZoneId.trim()
        val locName = input.name.trim()
        val adj = input.adjustments.map(LocationValidator::parseAdjustment)
        val adjustments = PrayerTimeAdjustments(
            fajr = adj.getOrElse(0) { 0 },
            dhuhr = adj.getOrElse(1) { 0 },
            asr = adj.getOrElse(2) { 0 },
            maghrib = adj.getOrElse(3) { 0 },
            isha = adj.getOrElse(4) { 0 }
        )

        viewModelScope.launch {
            val currentEditing = _uiState.value.editingLocation
            if (currentEditing != null) {
                // Update
                val updated = currentEditing.copy(
                    name = locName,
                    latitude = lat,
                    longitude = lon,
                    timeZoneId = tz,
                    adjustments = adjustments,
                    calculationMethod = input.calculationMethod
                )
                repository.updateLocation(updated)
            } else {
                // Add
                val isFirst = _uiState.value.locations.isEmpty()
                val newLocation = SavedLocation(
                    name = locName,
                    latitude = lat,
                    longitude = lon,
                    timeZoneId = tz,
                    isDefault = isFirst,
                    adjustments = adjustments,
                    calculationMethod = input.calculationMethod
                )
                repository.addLocation(newLocation)
            }
            dismissAddEditDialog()
        }

        return validation
    }

    fun requestDeleteLocation(location: SavedLocation) {
        _uiState.update { it.copy(locationToDelete = location) }
    }

    fun cancelDeleteLocation() {
        _uiState.update { it.copy(locationToDelete = null) }
    }

    fun confirmDeleteLocation() {
        val location = _uiState.value.locationToDelete ?: return
        viewModelScope.launch {
            repository.deleteLocation(location)
            _uiState.update { it.copy(locationToDelete = null) }
        }
    }

    class Factory(
        private val repository: LocationRepository,
        private val calculationSettings: Flow<PrayerCalculationSettings>? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LocationsViewModel::class.java)) {
                return LocationsViewModel(repository, calculationSettings) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

