package com.example.prayeralarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimeZoneGuesserTest {

    private val now = Instant.parse("2026-11-03T12:00:00Z")

    @Test
    fun singleZoneCountry_returnsThatZone() {
        assertEquals("Africa/Tunis", TimeZoneGuesser.pick(listOf("Africa/Tunis"), 10.18, now))
    }

    @Test
    fun multiZoneCountry_picksZoneClosestToSolarOffset() {
        val us = listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles")
        assertEquals("America/Los_Angeles", TimeZoneGuesser.pick(us, -118.24, now))
        assertEquals("America/New_York", TimeZoneGuesser.pick(us, -74.0, now))
    }

    @Test
    fun legacyAliases_loseToCanonicalIds() {
        // Android's ICU lists these together for Egypt; "Egypt" is a valid java.time alias.
        assertEquals("Africa/Cairo", TimeZoneGuesser.pick(listOf("Egypt", "Africa/Cairo"), 31.24, now))
    }

    @Test
    fun unknownZones_areIgnored() {
        assertNull(TimeZoneGuesser.pick(listOf("Not/AZone"), 0.0, now))
    }

    @Test
    fun suggestions_matchPartialCityNames() {
        val found = TimeZoneGuesser.suggestions("tun")
        assertTrue(found.contains("Africa/Tunis"))
        assertTrue(TimeZoneGuesser.suggestions("").isEmpty())
    }
}
