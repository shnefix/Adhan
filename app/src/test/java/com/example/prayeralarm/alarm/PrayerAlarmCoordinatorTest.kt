package com.example.prayeralarm.alarm

import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Behaves like AlarmManager: one alarm per request code; re-scheduling replaces. */
private class FakeScheduler : PrayerAlarmScheduler {
    val active = linkedMapOf<Int, ScheduledPrayerAlarm>()
    var scheduleCalls = 0

    override fun schedule(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult {
        scheduleCalls++
        alarms.forEach { active[it.requestCode] = it }
        return AlarmScheduleResult.Scheduled
    }

    override fun cancel(alarms: List<ScheduledPrayerAlarm>): AlarmScheduleResult {
        alarms.forEach { active.remove(it.requestCode) }
        return AlarmScheduleResult.Cancelled
    }
}

class PrayerAlarmCoordinatorTest {

    private val zone = ZoneId.of("Africa/Tunis")
    private val today = LocalDate.of(2026, 6, 1)
    private val now: Instant = ZonedDateTime.of(2026, 6, 1, 0, 0, 0, 0, zone).toInstant()
    private val scheduler = FakeScheduler()
    private val coordinator = PrayerAlarmCoordinator(scheduler)

    private val dates = (0L..2L).map { today.plusDays(it) }
    private val window = listOf(today.minusDays(1)) + dates
    private val days = dates.map { FajrScheduleDay(ZonedDateTime.of(it, java.time.LocalTime.of(3, 30), zone)) }

    private fun run(config: AlarmPlanConfig, at: Instant = now) =
        coordinator.reconcile(days, config, at, "Tunis", window)

    private fun triggerTimes() = scheduler.active.values.map { it.triggerAtMillis }.sorted()

    private fun expected(minutes: Long) = days.map { it.fajrAt.minusMinutes(minutes).toInstant().toEpochMilli() }

    @Test
    fun beforeFajr20_schedulesOneAlarmPerDayAtTheRightTime() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        assertEquals(3, scheduler.active.size)
        assertEquals(expected(20), triggerTimes())
    }

    @Test
    fun changingMinutes_replacesTheSameSlots_noDuplicates() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 30))
        assertEquals(3, scheduler.active.size)
        assertEquals(expected(30), triggerTimes())
    }

    @Test
    fun runningTwiceWithSameConfig_isIdempotent() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20)
        run(config)
        val first = scheduler.active.toMap()
        run(config)
        assertEquals(first, scheduler.active)
    }

    @Test
    fun fiveAlarmSeries_schedulesFivePerDay_andShrinkingCancelsExtras() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 1, beforeFajrCount = 5))
        assertEquals(15, scheduler.active.size)
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 1, beforeFajrCount = 2))
        assertEquals(6, scheduler.active.size)
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 1, beforeFajrCount = 5))
        assertEquals(15, scheduler.active.size)
    }

    @Test
    fun aroundFajr_schedulesBeforeAtAndAfterFajr() {
        run(AlarmPlanConfig(enabled = true, beforeFajrEnabled = false, aroundEnabled = true, aroundBefore = 3, aroundAfter = 2))
        assertEquals(18, scheduler.active.size) // 6 per day x 3 days
        val firstDay = days.first().fajrAt
        val expectedFirstDay = (-3L..2L).map { firstDay.plusMinutes(it).toInstant().toEpochMilli() }
        assertEquals(expectedFirstDay, triggerTimes().take(6))
    }

    @Test
    fun stoppedSeries_isNotRescheduled_andOtherDaysAre() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 1, beforeFajrCount = 5)
        run(config)
        val series = PrayerAlarmCoordinator.seriesFor(today, com.example.prayeralarm.domain.alarm.FajrAlarmMode.BEFORE_FAJR)
        coordinator.reconcile(days, config, now, "Tunis", window, dismissed = setOf(series))
        assertEquals(10, scheduler.active.size) // today's 5 cancelled, other 2 days kept
        assertTrue(scheduler.active.keys.none { PrayerAlarmCoordinator.seriesOf(it) == series })
    }

    @Test
    fun previousVersionCodes_areCancelled() {
        val mode = com.example.prayeralarm.domain.alarm.FajrAlarmMode.BEFORE_FAJR
        val series = PrayerAlarmCoordinator.seriesFor(today, mode)
        scheduler.active[series] = ScheduledPrayerAlarm(series, 1L, "old", "")           // v1 code
        scheduler.active[series * 10 + 3] = ScheduledPrayerAlarm(series * 10 + 3, 1L, "old", "") // v2 code
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        assertTrue(scheduler.active.values.none { it.prayerName == "old" })
        assertEquals(3, scheduler.active.size)
    }

    @Test
    fun qiyam90_schedulesAtZeroTwoHundred() {
        run(AlarmPlanConfig(enabled = true, beforeFajrEnabled = false, qiyamEnabled = true, qiyamMinutes = 90))
        assertEquals(expected(90), triggerTimes())
        assertTrue(scheduler.active.values.all { it.prayerName == "Qiyam Al-Layl" })
    }

    @Test
    fun switchingMode_cancelsThePreviousModeAlarms() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        run(AlarmPlanConfig(enabled = true, beforeFajrEnabled = false, qiyamEnabled = true, qiyamMinutes = 90))
        assertEquals(3, scheduler.active.size)
        assertTrue(scheduler.active.values.all { it.prayerName == "Qiyam Al-Layl" })
    }

    @Test
    fun disabling_cancelsEverything() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        run(AlarmPlanConfig(enabled = false))
        assertTrue(scheduler.active.isEmpty())
    }

    @Test
    fun pastAlarms_areSkipped() {
        // Now is 03:20 on day 1: the 03:10 alarm (20 min before 03:30) has already passed.
        val late = ZonedDateTime.of(2026, 6, 1, 3, 20, 0, 0, zone).toInstant()
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20), late)
        assertEquals(2, scheduler.active.size)
        assertTrue(triggerTimes().all { it > late.toEpochMilli() })
    }

    @Test
    fun newFajrTimes_afterLocationChange_moveTheAlarms() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20)
        run(config)
        val shifted = days.map { FajrScheduleDay(it.fajrAt.plusMinutes(12)) }
        coordinator.reconcile(shifted, config, now, "Sfax", window)
        assertEquals(3, scheduler.active.size)
        assertEquals(expected(20).map { it + 12 * 60_000 }, triggerTimes())
    }

    @Test
    fun dateRollover_dropsYesterdayAndAddsANewDay() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20)
        run(config)
        val tomorrow = today.plusDays(1)
        val newDates = (0L..2L).map { tomorrow.plusDays(it) }
        val newDays = newDates.map { FajrScheduleDay(ZonedDateTime.of(it, java.time.LocalTime.of(3, 30), zone)) }
        val later = ZonedDateTime.of(2026, 6, 2, 0, 5, 0, 0, zone).toInstant()
        coordinator.reconcile(newDays, config, later, "Tunis", listOf(today) + newDates)
        assertEquals(3, scheduler.active.size)
        assertEquals(
            newDays.map { it.fajrAt.minusMinutes(20).toInstant().toEpochMilli() },
            triggerTimes()
        )
    }

    @Test
    fun invalidConfig_failsWithoutTouchingAlarms() {
        run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))
        val before = scheduler.active.toMap()
        val result = run(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 500))
        assertTrue(result is AlarmReconciliationResult.Failed)
        assertEquals(before, scheduler.active)
    }

    @Test
    fun requestCodes_areUniquePerDateAndMode() {
        val codes = window.flatMap { d ->
            com.example.prayeralarm.domain.alarm.FajrAlarmMode.values().flatMap { m ->
                (0 until com.example.prayeralarm.domain.alarm.FajrAlarmMode.SERIES_SLOTS).map { coordinator.requestCodeFor(d, m, it) }
            }
        }
        assertEquals(codes.size, codes.toSet().size)
    }
}
