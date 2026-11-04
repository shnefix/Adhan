package com.example.prayeralarm.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.batoulapps.adhan.Madhab
import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.alarm.FajrAlarmMode
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prayer_alarm_preferences")

/**
 * Manages key-value application settings using Jetpack DataStore Preferences.
 */
class UserPreferencesManager(private val context: Context) : LocationSelectionStore {

    companion object {
        val KEY_SELECTED_LOCATION_ID = longPreferencesKey("selected_location_id")
        val KEY_CALCULATION_METHOD = stringPreferencesKey("calculation_method")
        private val KEY_ASR_MADHAB = stringPreferencesKey("asr_madhab")
        private val KEY_HIJRI_ADJUSTMENT = intPreferencesKey("hijri_adjustment_days")

        // Alarm settings. KEY_ALARM_ENABLED is unchanged so the master switch survives upgrade.
        private val KEY_ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        private val KEY_BEFORE_ENABLED = booleanPreferencesKey("alarm_before_fajr_enabled")
        private val KEY_BEFORE_MINUTES = intPreferencesKey("alarm_before_fajr_minutes")
        private val KEY_QIYAM_ENABLED = booleanPreferencesKey("alarm_qiyam_enabled")
        private val KEY_QIYAM_MINUTES = intPreferencesKey("alarm_qiyam_minutes")
        private val KEY_BEFORE_COUNT = intPreferencesKey("alarm_before_fajr_count")
        private val KEY_QIYAM_COUNT = intPreferencesKey("alarm_qiyam_count")
        private val KEY_AROUND_ENABLED = booleanPreferencesKey("alarm_around_enabled")
        private val KEY_AROUND_BEFORE = intPreferencesKey("alarm_around_before")
        private val KEY_AROUND_AFTER = intPreferencesKey("alarm_around_after")
        private val KEY_ALARM_SOUND_URI = stringPreferencesKey("alarm_sound_uri")
        private val KEY_ALARM_SOUND_NAME = stringPreferencesKey("alarm_sound_name")
        private val KEY_ENABLE_PERSISTENT_NOTIFICATION =
            booleanPreferencesKey("enable_persistent_notification")
    }

    private val prefsFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    // ---- Calculation settings -------------------------------------------------------------

    val calculationMethod: Flow<PrayerCalculationMethod> = prefsFlow.map { preferences ->
        preferences[KEY_CALCULATION_METHOD]?.let { name ->
            runCatching { PrayerCalculationMethod.valueOf(name) }.getOrNull()
        } ?: PrayerCalculationMethod.DEFAULT
    }

    suspend fun setCalculationMethod(method: PrayerCalculationMethod) {
        context.dataStore.edit { it[KEY_CALCULATION_METHOD] = method.name }
    }

    val madhab: Flow<Madhab> = prefsFlow.map { preferences ->
        preferences[KEY_ASR_MADHAB]?.let { runCatching { Madhab.valueOf(it) }.getOrNull() }
            ?: Madhab.SHAFI
    }

    suspend fun setMadhab(madhab: Madhab) {
        context.dataStore.edit { it[KEY_ASR_MADHAB] = madhab.name }
    }

    /** Days added to the calculated Hijri date to match the locally announced one. */
    val hijriAdjustment: Flow<Int> = prefsFlow.map { it[KEY_HIJRI_ADJUSTMENT] ?: 0 }

    suspend fun setHijriAdjustment(days: Int) {
        context.dataStore.edit { it[KEY_HIJRI_ADJUSTMENT] = days }
    }

    // ---- Location selection ---------------------------------------------------------------

    override val selectedLocationId: Flow<Long?> =
        prefsFlow.map { preferences -> preferences[KEY_SELECTED_LOCATION_ID] }

    override suspend fun setSelectedLocationId(locationId: Long) {
        context.dataStore.edit { it[KEY_SELECTED_LOCATION_ID] = locationId }
    }

    override suspend fun clearSelectedLocationId() {
        context.dataStore.edit { it.remove(KEY_SELECTED_LOCATION_ID) }
    }

    // ---- Fajr alarm settings --------------------------------------------------------------

    val alarmPlanConfig: Flow<AlarmPlanConfig> = prefsFlow.map { preferences ->
        val defaults = AlarmPlanConfig.DEFAULT
        val beforeEnabled = preferences[KEY_BEFORE_ENABLED] ?: defaults.beforeFajrEnabled
        // Only one mode may be active; if storage ever holds both, Before Fajr wins.
        val qiyamEnabled = (preferences[KEY_QIYAM_ENABLED] ?: defaults.qiyamEnabled) && !beforeEnabled
        val aroundEnabled = (preferences[KEY_AROUND_ENABLED] ?: defaults.aroundEnabled) &&
            !beforeEnabled && !qiyamEnabled
        AlarmPlanConfig(
            enabled = preferences[KEY_ALARM_ENABLED] ?: defaults.enabled,
            beforeFajrEnabled = beforeEnabled,
            beforeFajrMinutes = (preferences[KEY_BEFORE_MINUTES] ?: defaults.beforeFajrMinutes)
                .coerceIn(FajrAlarmMode.BEFORE_FAJR.range),
            qiyamEnabled = qiyamEnabled,
            qiyamMinutes = (preferences[KEY_QIYAM_MINUTES] ?: defaults.qiyamMinutes)
                .coerceIn(FajrAlarmMode.QIYAM_AL_LAYL.range),
            beforeFajrCount = (preferences[KEY_BEFORE_COUNT] ?: 1).coerceIn(1, FajrAlarmMode.MAX_COUNT),
            qiyamCount = (preferences[KEY_QIYAM_COUNT] ?: 1).coerceIn(1, FajrAlarmMode.MAX_COUNT),
            aroundEnabled = aroundEnabled,
            aroundBefore = (preferences[KEY_AROUND_BEFORE] ?: defaults.aroundBefore).coerceIn(0, FajrAlarmMode.MAX_COUNT),
            aroundAfter = (preferences[KEY_AROUND_AFTER] ?: defaults.aroundAfter).coerceIn(0, FajrAlarmMode.MAX_COUNT),
            soundUri = preferences[KEY_ALARM_SOUND_URI],
            soundName = preferences[KEY_ALARM_SOUND_NAME]
        )
    }

    suspend fun setAlarmPlanConfig(config: AlarmPlanConfig) {
        config.validationError()?.let { throw IllegalArgumentException(it) }
        context.dataStore.edit { preferences ->
            preferences[KEY_ALARM_ENABLED] = config.enabled
            preferences[KEY_BEFORE_ENABLED] = config.beforeFajrEnabled
            preferences[KEY_BEFORE_MINUTES] = config.beforeFajrMinutes
            preferences[KEY_QIYAM_ENABLED] = config.qiyamEnabled
            preferences[KEY_QIYAM_MINUTES] = config.qiyamMinutes
            preferences[KEY_BEFORE_COUNT] = config.beforeFajrCount
            preferences[KEY_QIYAM_COUNT] = config.qiyamCount
            preferences[KEY_AROUND_ENABLED] = config.aroundEnabled
            preferences[KEY_AROUND_BEFORE] = config.aroundBefore
            preferences[KEY_AROUND_AFTER] = config.aroundAfter
            if (config.soundUri == null) {
                preferences.remove(KEY_ALARM_SOUND_URI)
                preferences.remove(KEY_ALARM_SOUND_NAME)
            } else {
                preferences[KEY_ALARM_SOUND_URI] = config.soundUri
                preferences[KEY_ALARM_SOUND_NAME] = config.soundName ?: "Custom sound"
            }
        }
    }

    // Declared last: depends on calculationMethod and madhab being initialised.
    val calculationSettings: Flow<PrayerCalculationSettings> =
        combine(calculationMethod, madhab) { method, asr -> PrayerCalculationSettings(method, asr) }
}
