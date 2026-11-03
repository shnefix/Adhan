package com.example.prayeralarm.ui.locations

import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.model.SavedLocation

/**
 * UI State for the Location Management Screen.
 */
data class LocationsUiState(
    val locations: List<SavedLocation> = emptyList(),
    val activeLocation: SavedLocation? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showAddEditDialog: Boolean = false,
    val editingLocation: SavedLocation? = null, // null if adding new
    val locationToDelete: SavedLocation? = null, // non-null when delete confirmation dialog is shown
    val calculationSettings: PrayerCalculationSettings = PrayerCalculationSettings() // drives the dialog's live preview
)

