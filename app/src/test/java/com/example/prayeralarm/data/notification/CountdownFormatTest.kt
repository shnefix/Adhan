package com.example.prayeralarm.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownFormatTest {

    private fun millis(hours: Int = 0, minutes: Int = 0, seconds: Int = 0): Long =
        ((hours * 60L + minutes) * 60L + seconds) * 1000L

    @Test
    fun hoursMinutes_showsHoursAndRoundsMinutesUp() {
        assertEquals("9h 54m", CountdownFormat.hoursMinutes(millis(9, 53, 1)))
        assertEquals("9h 54m", CountdownFormat.hoursMinutes(millis(9, 54)))
        assertEquals("1h 0m", CountdownFormat.hoursMinutes(millis(0, 59, 30)))
        assertEquals("45m", CountdownFormat.hoursMinutes(millis(0, 44, 10)))
    }

    @Test
    fun showsSeconds_onlyForTheLastTenMinutes() {
        assertFalse(CountdownFormat.showsSeconds(millis(0, 10, 1)))
        assertTrue(CountdownFormat.showsSeconds(millis(0, 10)))
        assertTrue(CountdownFormat.showsSeconds(millis(0, 0, 5)))
    }

    @Test
    fun millisUntilNextUpdate_wakesWhenTheShownMinuteChanges() {
        // 9h 53m 20s left shows "9h 54m" until 9h 53m 0s, 20 seconds from now.
        assertEquals(millis(seconds = 20), CountdownFormat.millisUntilNextUpdate(millis(9, 53, 20)))
        assertEquals(millis(minutes = 1), CountdownFormat.millisUntilNextUpdate(millis(2, 0)))
        // 10m 30s left: the switch to the seconds countdown is 30 seconds away.
        assertEquals(millis(seconds = 30), CountdownFormat.millisUntilNextUpdate(millis(0, 10, 30)))
    }

    @Test
    fun millisUntilNextUpdate_inTheLastTenMinutesWaitsForThePrayer() {
        assertEquals(millis(0, 5, 1), CountdownFormat.millisUntilNextUpdate(millis(0, 5)))
    }
}
