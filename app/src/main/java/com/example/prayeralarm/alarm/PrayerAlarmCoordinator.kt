package com.example.prayeralarm.alarm

import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.alarm.AlarmPlanGenerator
import com.example.prayeralarm.domain.alarm.AlarmPlanResult
import com.example.prayeralarm.domain.alarm.FajrAlarmMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class FajrScheduleDay(
    val fajrAt: ZonedDateTime
)

sealed interface AlarmReconciliationResult {
    data class Applied(
        val desired: List<ScheduledPrayerAlarm>,
        val cancelled: List<ScheduledPrayerAlarm>
    ) : AlarmReconciliationResult

    data class Failed(val reason: AlarmScheduleResult) : AlarmReconciliationResult
}

/**
 * Turns settings + Fajr times into the exact set of alarms that must exist.
 *
 * Request codes are deterministic: series (Fajr date, mode) * SERIES_SLOTS + index. That
 * makes reconciliation stateless: every possible code in the window that is not desired is
 * cancelled, so no alarm can be orphaned (even after process death) and re-scheduling a code
 * replaces, never duplicates.
 */
class PrayerAlarmCoordinator(
    private val scheduler: PrayerAlarmScheduler
) {
    fun requestCodeFor(date: LocalDate, mode: FajrAlarmMode, index: Int = 0): Int =
        seriesFor(date, mode) * FajrAlarmMode.SERIES_SLOTS + index

    /**
     * @param days Fajr time for each date that may hold an alarm
     * @param window every date whose codes this call is allowed to cancel (superset of days)
     * @param dismissed series the user already stopped ("I woke up"): never rescheduled
     */
    fun reconcile(
        days: List<FajrScheduleDay>,
        config: AlarmPlanConfig,
        now: Instant,
        locationName: String,
        window: List<LocalDate>,
        dismissed: Set<Int> = emptySet()
    ): AlarmReconciliationResult {
        val desired = mutableListOf<ScheduledPrayerAlarm>()
        if (config.enabled) {
            for (day in days) {
                when (val result = AlarmPlanGenerator.generate(day.fajrAt, config)) {
                    is AlarmPlanResult.Invalid -> return AlarmReconciliationResult.Failed(
                        AlarmScheduleResult.Invalid(result.message)
                    )

                    is AlarmPlanResult.Success -> {
                        for (planned in result.alarms) {
                            val date = day.fajrAt.toLocalDate()
                            if (seriesFor(date, planned.mode) in dismissed) continue
                            val trigger = planned.triggerAt.toInstant()
                            if (!trigger.isAfter(now)) continue
                            desired += ScheduledPrayerAlarm(
                                requestCode = requestCodeFor(date, planned.mode, planned.index),
                                triggerAtMillis = trigger.toEpochMilli(),
                                prayerName = planned.mode.alarmTitle,
                                locationName = locationName
                            )
                        }
                    }
                }
            }
        }

        val desiredCodes = desired.map { it.requestCode }.toSet()
        val obsolete = window
            .flatMap { date ->
                FajrAlarmMode.values().flatMap { mode ->
                    (0 until FajrAlarmMode.SERIES_SLOTS).map { requestCodeFor(date, mode, it) } +
                        previousCodesFor(date, mode)
                }
            }
            .filter { it !in desiredCodes }
            .distinct()
            .map { ScheduledPrayerAlarm(it, 0L, "", "") }

        val cancelResult = scheduler.cancel(obsolete)
        if (cancelResult !is AlarmScheduleResult.Cancelled) {
            return AlarmReconciliationResult.Failed(cancelResult)
        }
        val scheduleResult = scheduler.schedule(desired)
        if (scheduleResult !is AlarmScheduleResult.Scheduled) {
            return AlarmReconciliationResult.Failed(scheduleResult)
        }
        return AlarmReconciliationResult.Applied(desired, obsolete)
    }

    companion object {
        /** Fixed so codes stay stable if more modes are ever appended. */
        private const val CODES_PER_DAY = 4L

        /** One series = all alarms of one mode on one Fajr date. */
        fun seriesFor(date: LocalDate, mode: FajrAlarmMode): Int =
            (date.toEpochDay() * CODES_PER_DAY + mode.ordinal).toInt()

        /** The series a request code belongs to. */
        fun seriesOf(requestCode: Int): Int = requestCode / FajrAlarmMode.SERIES_SLOTS

        /** Epoch day a series belongs to. */
        fun epochDayOf(series: Int): Long = series / CODES_PER_DAY

        /** Codes used by earlier versions of this feature, cancelled so upgrades leave no orphans. */
        private fun previousCodesFor(date: LocalDate, mode: FajrAlarmMode): List<Int> {
            val series = seriesFor(date, mode)
            // v1: one alarm per series; v2: 10 slots per series.
            return listOf(series) + (0 until 10).map { series * 10 + it }
        }
    }
}
