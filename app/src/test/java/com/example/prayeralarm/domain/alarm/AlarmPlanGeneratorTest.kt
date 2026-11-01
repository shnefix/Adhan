package com.example.prayeralarm.domain.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmPlanGeneratorTest {

    private val tunis = ZoneId.of("Africa/Tunis")
    private val fajr = ZonedDateTime.of(2026, 6, 1, 3, 30, 0, 0, tunis)

    private fun alarmFor(config: AlarmPlanConfig): PlannedAlarm? =
        (AlarmPlanGenerator.generate(fajr, config) as AlarmPlanResult.Success).alarms.singleOrNull()

    @Test
    fun beforeFajr_20_minutes() {
        val alarm = alarmFor(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 20))!!
        assertEquals(FajrAlarmMode.BEFORE_FAJR, alarm.mode)
        assertEquals(fajr.minusMinutes(20), alarm.triggerAt)
    }

    @Test
    fun beforeFajr_changing_to_30_moves_the_alarm() {
        val alarm = alarmFor(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 30))!!
        assertEquals(fajr.minusMinutes(30), alarm.triggerAt)
    }

    @Test
    fun beforeFajr_45_minutes() {
        val alarm = alarmFor(AlarmPlanConfig(enabled = true, beforeFajrMinutes = 45))!!
        assertEquals(fajr.minusMinutes(45), alarm.triggerAt)
    }

    @Test
    fun qiyam_90_minutes_before_0330_is_0200() {
        val config = AlarmPlanConfig(
            enabled = true, beforeFajrEnabled = false, qiyamEnabled = true, qiyamMinutes = 90
        )
        val alarm = alarmFor(config)!!
        assertEquals(FajrAlarmMode.QIYAM_AL_LAYL, alarm.mode)
        assertEquals(fajr.minusMinutes(90), alarm.triggerAt)
        assertEquals(2, alarm.triggerAt.hour)
        assertEquals(0, alarm.triggerAt.minute)
    }

    @Test
    fun fiveAlarms_oneMinuteApart_endingOneMinuteBeforeFajr() {
        val fajr5 = ZonedDateTime.of(2026, 6, 1, 5, 0, 0, 0, tunis)
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 1, beforeFajrCount = 5)
        val alarms = (AlarmPlanGenerator.generate(fajr5, config) as AlarmPlanResult.Success).alarms
        assertEquals(listOf("04:55", "04:56", "04:57", "04:58", "04:59"),
            alarms.map { "%02d:%02d".format(it.triggerAt.hour, it.triggerAt.minute) })
        assertEquals(listOf(0, 1, 2, 3, 4), alarms.map { it.index })
    }

    @Test
    fun beforeAndAfterFajr_0457_to_0502_around_0500() {
        val fajr5 = ZonedDateTime.of(2026, 6, 1, 5, 0, 0, 0, tunis)
        val config = AlarmPlanConfig(
            enabled = true, beforeFajrEnabled = false, aroundEnabled = true, aroundBefore = 3, aroundAfter = 2
        )
        val alarms = (AlarmPlanGenerator.generate(fajr5, config) as AlarmPlanResult.Success).alarms
        assertEquals(listOf("04:57", "04:58", "04:59", "05:00", "05:01", "05:02"),
            alarms.map { "%02d:%02d".format(it.triggerAt.hour, it.triggerAt.minute) })
        assertTrue(alarms.all { it.mode == FajrAlarmMode.AROUND_FAJR })
    }

    @Test
    fun beforeAndAfterFajr_onlyFajrItself_whenZeroBeforeAndAfter() {
        val config = AlarmPlanConfig(
            enabled = true, beforeFajrEnabled = false, aroundEnabled = true, aroundBefore = 0, aroundAfter = 0
        )
        val alarms = (AlarmPlanGenerator.generate(fajr, config) as AlarmPlanResult.Success).alarms
        assertEquals(listOf(fajr), alarms.map { it.triggerAt })
    }

    @Test
    fun beforeAndAfterFajr_invalidCountsRejected() {
        val bad = AlarmPlanConfig(enabled = true, beforeFajrEnabled = false, aroundEnabled = true, aroundBefore = 11)
        assertTrue(AlarmPlanGenerator.generate(fajr, bad) is AlarmPlanResult.Invalid)
    }

    @Test
    fun invalidCount_isRejected() {
        assertTrue(AlarmPlanGenerator.generate(fajr, AlarmPlanConfig(enabled = true, beforeFajrCount = 0)) is AlarmPlanResult.Invalid)
        assertTrue(AlarmPlanGenerator.generate(fajr, AlarmPlanConfig(enabled = true, beforeFajrCount = 11)) is AlarmPlanResult.Invalid)
    }

    @Test
    fun masterSwitchOff_producesNoAlarm() {
        assertNull(alarmFor(AlarmPlanConfig(enabled = false)))
    }

    @Test
    fun noModeSelected_producesNoAlarm() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrEnabled = false, qiyamEnabled = false)
        assertNull(alarmFor(config))
    }

    @Test
    fun bothFlagged_beforeFajrWins_soOnlyOneAlarmExists() {
        val config = AlarmPlanConfig(enabled = true, beforeFajrEnabled = true, qiyamEnabled = true)
        assertEquals(FajrAlarmMode.BEFORE_FAJR, alarmFor(config)!!.mode)
    }

    @Test
    fun invalidMinutes_areRejected() {
        listOf(
            AlarmPlanConfig(enabled = true, beforeFajrMinutes = 0),
            AlarmPlanConfig(enabled = true, beforeFajrMinutes = 181),
            AlarmPlanConfig(enabled = true, qiyamMinutes = 4),
            AlarmPlanConfig(enabled = true, qiyamMinutes = 481)
        ).forEach {
            assertTrue(AlarmPlanGenerator.generate(fajr, it) is AlarmPlanResult.Invalid)
        }
    }

    @Test
    fun alarmCanFallOnThePreviousDay() {
        val earlyFajr = ZonedDateTime.of(2026, 6, 1, 0, 40, 0, 0, tunis)
        val config = AlarmPlanConfig(enabled = true, beforeFajrMinutes = 60)
        val alarm = (AlarmPlanGenerator.generate(earlyFajr, config) as AlarmPlanResult.Success).alarms.single()
        assertEquals(earlyFajr.toLocalDate().minusDays(1), alarm.triggerAt.toLocalDate())
        assertEquals(23, alarm.triggerAt.hour)
        assertEquals(40, alarm.triggerAt.minute)
    }

    @Test
    fun dstChangeBetweenAlarmAndFajr_keepsTheRealInterval() {
        val paris = ZoneId.of("Europe/Paris")
        val fajrParis = ZonedDateTime.of(2026, 3, 29, 6, 0, 0, 0, paris)
        val config = AlarmPlanConfig(
            enabled = true, beforeFajrEnabled = false, qiyamEnabled = true, qiyamMinutes = 360
        )
        val alarm = (AlarmPlanGenerator.generate(fajrParis, config) as AlarmPlanResult.Success).alarms.single()
        // Clocks jump forward at 02:00 that night: 360 real minutes before 06:00 CEST is 23:00 CET.
        assertEquals("2026-03-28T23:00+01:00[Europe/Paris]", alarm.triggerAt.toString())
    }
}
