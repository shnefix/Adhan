package com.example.prayeralarm.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HijriDateTest {

    @Test
    fun format_knownUmmAlQuraDates() {
        // 1 Ramadan 1445 (Umm al-Qura) fell on 11 March 2024.
        assertEquals("1 Ramadan 1445", HijriDate.format(LocalDate.of(2024, 3, 11)))
        // 1 Muharram 1446 fell on 7 July 2024.
        assertEquals("1 Muharram 1446", HijriDate.format(LocalDate.of(2024, 7, 7)))
    }

    @Test
    fun format_adjustmentShiftsTheDay() {
        val date = LocalDate.of(2024, 3, 11)
        assertEquals("29 Shaban 1445", HijriDate.format(date, adjustmentDays = -1))
        assertEquals("2 Ramadan 1445", HijriDate.format(date, adjustmentDays = 1))
    }

    @Test
    fun format_adjustmentIsClamped() {
        val date = LocalDate.of(2024, 3, 11)
        assertEquals(HijriDate.format(date, 2), HijriDate.format(date, 9))
    }

    @Test
    fun format_outsideSupportedRangeIsEmpty() {
        assertEquals("", HijriDate.format(LocalDate.of(1800, 1, 1)))
    }
}
