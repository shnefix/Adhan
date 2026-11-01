package com.example.prayeralarm.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prayeralarm.data.preferences.UserPreferencesManager
import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.alarm.FajrAlarmMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AlarmPlanViewModel(
    private val preferences: AlarmPlanPreferences
) : ViewModel() {

    private val _config = MutableStateFlow(AlarmPlanConfig.DEFAULT)
    val config: StateFlow<AlarmPlanConfig> = _config.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.config
                .catch { error -> _errorMessage.value = error.message ?: "Unable to load alarm settings." }
                .collect { _config.value = it }
        }
    }

    /** Master switch for all Fajr alarms. */
    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    /**
     * Turning a mode on turns the others off: only one Fajr-related alarm series is ever
     * active, so there can be no accidental double alarm.
     */
    fun setModeEnabled(mode: FajrAlarmMode, enabled: Boolean) = update { config ->
        fun on(m: FajrAlarmMode) = if (m == mode) enabled else if (enabled) false else config.isModeEnabled(m)
        config.copy(
            beforeFajrEnabled = on(FajrAlarmMode.BEFORE_FAJR),
            qiyamEnabled = on(FajrAlarmMode.QIYAM_AL_LAYL),
            aroundEnabled = on(FajrAlarmMode.AROUND_FAJR)
        )
    }

    /** Before & After Fajr: how many alarms ring before Fajr and after it (Fajr itself always rings). */
    fun setAroundCounts(before: Int? = null, after: Int? = null) = update {
        it.copy(aroundBefore = before ?: it.aroundBefore, aroundAfter = after ?: it.aroundAfter)
    }

    fun setMinutes(mode: FajrAlarmMode, minutes: Int) = update { config ->
        when (mode) {
            FajrAlarmMode.BEFORE_FAJR -> config.copy(beforeFajrMinutes = minutes)
            FajrAlarmMode.QIYAM_AL_LAYL -> config.copy(qiyamMinutes = minutes)
            FajrAlarmMode.AROUND_FAJR -> config
        }
    }

    fun setCount(mode: FajrAlarmMode, count: Int) = update { config ->
        when (mode) {
            FajrAlarmMode.BEFORE_FAJR -> config.copy(beforeFajrCount = count)
            FajrAlarmMode.QIYAM_AL_LAYL -> config.copy(qiyamCount = count)
            FajrAlarmMode.AROUND_FAJR -> config
        }
    }

    /** [uri] == null restores the device default alarm sound. */
    fun setSound(uri: String?, name: String?) =
        update { it.copy(soundUri = uri, soundName = if (uri == null) null else name) }

    private fun update(transform: (AlarmPlanConfig) -> AlarmPlanConfig) {
        val updated = transform(_config.value)
        val problem = updated.validationError()
        if (problem != null) {
            _errorMessage.value = problem
            return
        }
        _errorMessage.value = null
        _config.value = updated
        viewModelScope.launch {
            runCatching { preferences.setConfig(updated) }
                .onFailure { _errorMessage.value = it.message ?: "Unable to save alarm settings." }
        }
    }

    class Factory(private val preferences: AlarmPlanPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AlarmPlanViewModel::class.java)) {
                return AlarmPlanViewModel(preferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

interface AlarmPlanPreferences {
    val config: kotlinx.coroutines.flow.Flow<AlarmPlanConfig>
    suspend fun setConfig(config: AlarmPlanConfig)
}

class DataStoreAlarmPlanPreferences(
    private val manager: UserPreferencesManager
) : AlarmPlanPreferences {
    override val config = manager.alarmPlanConfig
    override suspend fun setConfig(config: AlarmPlanConfig) {
        manager.setAlarmPlanConfig(config)
    }
}
