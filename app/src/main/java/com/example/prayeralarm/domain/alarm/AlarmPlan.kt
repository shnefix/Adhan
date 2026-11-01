package com.example.prayeralarm.domain.alarm

import java.time.ZonedDateTime

/**
 * The Fajr-related alarm modes.
 *
 * NOTE: [ordinal] is part of the alarm request-code scheme (see
 * PrayerAlarmCoordinator.requestCodeFor). Never reorder; only append. At most 4 modes.
 */
enum class FajrAlarmMode(
    val label: String,
    val alarmTitle: String,
    val minMinutes: Int,
    val maxMinutes: Int,
    val defaultMinutes: Int,
    val presets: List<Int>
) {
    BEFORE_FAJR("Before Fajr", "Fajr alarm", 1, 180, 20, listOf(10, 15, 20, 30, 45, 60)),
    QIYAM_AL_LAYL("Qiyam Al-Layl", "Qiyam Al-Layl", 5, 480, 90, listOf(60, 90, 120, 150, 180)),

    /** Alarms before AND after Fajr, one minute apart, with one exactly at Fajr. */
    AROUND_FAJR("Before & After Fajr", "Fajr alarm", 0, 0, 0, emptyList());

    val range: IntRange get() = minMinutes..maxMinutes

    companion object {
        /** Most alarms of one kind (before Fajr / after Fajr / Before-Fajr series). */
        const val MAX_COUNT = 10

        /** Request-code slots reserved per series: 2 * MAX_COUNT + 1 fits in 32. */
        const val SERIES_SLOTS = 32

        /** Minutes between consecutive alarms of one series. */
        const val SPACING_MINUTES = 1L
    }
}

/**
 * Persisted alarm settings.
 *
 * [enabled] is the master switch. Each mode has its own switch and settings. Only ONE mode
 * is active at a time: [activeMode] resolves the (normally impossible) case where several
 * flags are set by taking the first in enum order, so behaviour is always predictable.
 *
 * [soundUri] == null means "use the device default alarm sound".
 */
data class AlarmPlanConfig(
    val enabled: Boolean = false,
    val beforeFajrEnabled: Boolean = true,
    val beforeFajrMinutes: Int = FajrAlarmMode.BEFORE_FAJR.defaultMinutes,
    val qiyamEnabled: Boolean = false,
    val qiyamMinutes: Int = FajrAlarmMode.QIYAM_AL_LAYL.defaultMinutes,
    val beforeFajrCount: Int = 1,
    val qiyamCount: Int = 1,
    val aroundEnabled: Boolean = false,
    /** Alarms rung before Fajr in the Before & After mode (0..MAX_COUNT). */
    val aroundBefore: Int = 3,
    /** Alarms rung after Fajr in the Before & After mode (0..MAX_COUNT). */
    val aroundAfter: Int = 2,
    val soundUri: String? = null,
    val soundName: String? = null
) {
    fun minutesFor(mode: FajrAlarmMode): Int = when (mode) {
        FajrAlarmMode.BEFORE_FAJR -> beforeFajrMinutes
        FajrAlarmMode.QIYAM_AL_LAYL -> qiyamMinutes
        FajrAlarmMode.AROUND_FAJR -> 0
    }

    fun countFor(mode: FajrAlarmMode): Int = when (mode) {
        FajrAlarmMode.BEFORE_FAJR -> beforeFajrCount
        FajrAlarmMode.QIYAM_AL_LAYL -> qiyamCount
        FajrAlarmMode.AROUND_FAJR -> aroundBefore + 1 + aroundAfter
    }

    fun isModeEnabled(mode: FajrAlarmMode): Boolean = when (mode) {
        FajrAlarmMode.BEFORE_FAJR -> beforeFajrEnabled
        FajrAlarmMode.QIYAM_AL_LAYL -> qiyamEnabled
        FajrAlarmMode.AROUND_FAJR -> aroundEnabled
    }

    /** The mode that will actually ring, or null when alarms are off / no mode selected. */
    val activeMode: FajrAlarmMode?
        get() = if (!enabled) null else FajrAlarmMode.values().firstOrNull { isModeEnabled(it) }

    fun validationError(): String? {
        val max = FajrAlarmMode.MAX_COUNT
        listOf(FajrAlarmMode.BEFORE_FAJR, FajrAlarmMode.QIYAM_AL_LAYL).forEach { mode ->
            if (minutesFor(mode) !in mode.range) {
                return "${mode.label} must be between ${mode.minMinutes} and ${mode.maxMinutes} minutes."
            }
            if (countFor(mode) !in 1..max) {
                return "${mode.label} alarm count must be between 1 and $max."
            }
        }
        if (aroundBefore !in 0..max || aroundAfter !in 0..max) {
            return "Before & After Fajr: alarms before and after must each be between 0 and $max."
        }
        return null
    }

    companion object {
        val DEFAULT = AlarmPlanConfig()
    }
}

data class PlannedAlarm(
    val mode: FajrAlarmMode,
    val triggerAt: ZonedDateTime,
    /** 0 = earliest alarm of the series. */
    val index: Int = 0
)

sealed interface AlarmPlanResult {
    /** [alarms] is empty when alarms are disabled or no mode is selected; else earliest first. */
    data class Success(val alarms: List<PlannedAlarm>) : AlarmPlanResult
    data class Invalid(val message: String) : AlarmPlanResult
}

/** Pure, Android-free planner: one Fajr time in, one series of alarms out. */
object AlarmPlanGenerator {

    /**
     * Minutes BEFORE Fajr for each alarm of [mode], earliest first (negative = after Fajr).
     * Before Fajr / Qiyam: the LAST alarm rings `minutes` before Fajr, earlier ones are
     * spaced SPACING_MINUTES apart before it. Before & After: Fajr 05:00 with 3 before and
     * 2 after gives 04:57 04:58 04:59 05:00 05:01 05:02.
     */
    fun offsetsMinutes(config: AlarmPlanConfig, mode: FajrAlarmMode): List<Long> {
        val spacing = FajrAlarmMode.SPACING_MINUTES
        return when (mode) {
            FajrAlarmMode.AROUND_FAJR ->
                (0 until config.countFor(mode)).map { i -> (config.aroundBefore - i) * spacing }

            else -> {
                val count = config.countFor(mode)
                val minutes = config.minutesFor(mode).toLong()
                (0 until count).map { i -> minutes + (count - 1 - i) * spacing }
            }
        }
    }

    fun generate(fajrAt: ZonedDateTime, config: AlarmPlanConfig): AlarmPlanResult {
        if (!config.enabled) return AlarmPlanResult.Success(emptyList())
        config.validationError()?.let { return AlarmPlanResult.Invalid(it) }
        val mode = config.activeMode ?: return AlarmPlanResult.Success(emptyList())
        // ZonedDateTime.minusMinutes works on the instant timeline, so DST changes between
        // the alarm and Fajr cannot shift the interval.
        return AlarmPlanResult.Success(
            offsetsMinutes(config, mode).mapIndexed { i, offset ->
                PlannedAlarm(mode, fajrAt.minusMinutes(offset), i)
            }
        )
    }
}
