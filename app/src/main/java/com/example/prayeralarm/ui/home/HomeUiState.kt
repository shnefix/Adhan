package com.example.prayeralarm.ui.home

import com.example.prayeralarm.domain.model.SavedLocation
import java.time.LocalDate

/**
 * Immutable UI State representing everything required to render the Home Screen.
 *
 * NOTE ON SAMPLE DATA:
 * If [isSampleData] is true, the displayed times are representative mock times
 * for UI preview/development only. Real astronomical calculation is wired in Stage 4.
 */
data class HomeUiState(
    val selectedLocation: SavedLocation = SavedLocation(
        id = 1,
        name = "Tunis (Sample)",
        latitude = 36.8065,
        longitude = 10.1815,
        timeZoneId = "Africa/Tunis",
        isDefault = true
    ),
    val currentDate: LocalDate = LocalDate.now(),
    val hijriDate: String = "",
    val nextPrayer: PrayerScheduleItem? = null,
    val remainingHours: Long = 2,
    val remainingMinutes: Long = 15,
    val remainingSeconds: Long = 40,
    val prayerTimetable: List<PrayerScheduleItem> = emptyList(),
    val isSampleData: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

