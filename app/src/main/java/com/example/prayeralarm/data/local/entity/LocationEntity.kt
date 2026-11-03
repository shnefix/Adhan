package com.example.prayeralarm.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.model.PrayerTimeAdjustments
import com.example.prayeralarm.domain.model.SavedLocation

/**
 * Room SQLite Entity representing a persisted location.
 *
 * Mapped to the table "saved_locations".
 *
 * @property id Unique auto-generated primary key in SQLite.
 * @property name Human-readable city/location name (e.g., "Tunis", "Sfax").
 * @property latitude Geographic latitude in decimal degrees (-90.0 to +90.0).
 * @property longitude Geographic longitude in decimal degrees (-180.0 to +180.0).
 * @property timeZoneId Canonical IANA timezone identifier (e.g., "Africa/Tunis").
 *
 * The `*_adjustment` columns are signed minute offsets applied to each calculated prayer time.
 * `calculation_method` holds a [PrayerCalculationMethod] name, or NULL to follow the app setting.
 */
@Entity(tableName = "saved_locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double,

    @ColumnInfo(name = "longitude")
    val longitude: Double,

    @ColumnInfo(name = "timezone_id")
    val timeZoneId: String,

    @ColumnInfo(name = "fajr_adjustment", defaultValue = "0")
    val fajrAdjustment: Int = 0,

    @ColumnInfo(name = "dhuhr_adjustment", defaultValue = "0")
    val dhuhrAdjustment: Int = 0,

    @ColumnInfo(name = "asr_adjustment", defaultValue = "0")
    val asrAdjustment: Int = 0,

    @ColumnInfo(name = "maghrib_adjustment", defaultValue = "0")
    val maghribAdjustment: Int = 0,

    @ColumnInfo(name = "isha_adjustment", defaultValue = "0")
    val ishaAdjustment: Int = 0,

    @ColumnInfo(name = "calculation_method")
    val calculationMethod: String? = null
)

/**
 * Conversion extensions between database entities and domain models.
 */
fun LocationEntity.toDomain(isSelected: Boolean = false): SavedLocation {
    return SavedLocation(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        isDefault = isSelected,
        adjustments = PrayerTimeAdjustments(
            fajr = fajrAdjustment,
            dhuhr = dhuhrAdjustment,
            asr = asrAdjustment,
            maghrib = maghribAdjustment,
            isha = ishaAdjustment
        ),
        // An unknown name (e.g. a method removed in a later version) falls back to the app setting.
        calculationMethod = calculationMethod?.let { name ->
            PrayerCalculationMethod.entries.firstOrNull { it.name == name }
        }
    )
}

fun SavedLocation.toEntity(): LocationEntity {
    return LocationEntity(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        fajrAdjustment = adjustments.fajr,
        dhuhrAdjustment = adjustments.dhuhr,
        asrAdjustment = adjustments.asr,
        maghribAdjustment = adjustments.maghrib,
        ishaAdjustment = adjustments.isha,
        calculationMethod = calculationMethod?.name
    )
}

