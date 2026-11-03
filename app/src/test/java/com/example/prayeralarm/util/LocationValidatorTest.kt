package com.example.prayeralarm.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying location parameter validation:
 * - Latitude bounds (-90.0 to 90.0)
 * - Longitude bounds (-180.0 to 180.0)
 * - IANA timezone ID resolution via java.time.ZoneId
 * - Name length and emptiness constraints
 */
class LocationValidatorTest {

    @Test
    fun validLocation_passesValidation() {
        val result = LocationValidator.validate(
            name = "Tunis",
            latitudeStr = "36.8065",
            longitudeStr = "10.1815",
            timezoneId = "Africa/Tunis"
        )

        assertTrue("Expected valid location to pass", result.isValid)
        assertNull(result.nameError)
        assertNull(result.latitudeError)
        assertNull(result.longitudeError)
        assertNull(result.timezoneError)
    }

    @Test
    fun emptyName_failsValidation() {
        val result = LocationValidator.validateName("   ")
        assertNotNull("Expected error for empty name", result)
        assertEquals("Location name cannot be empty", result)
    }

    @Test
    fun shortName_failsValidation() {
        val result = LocationValidator.validateName("A")
        assertNotNull("Expected error for single character name", result)
    }

    @Test
    fun excessiveLengthName_failsValidation() {
        val longName = "A".repeat(51)
        val result = LocationValidator.validateName(longName)
        assertNotNull("Expected error for name > 50 chars", result)
    }

    @Test
    fun latitudeBounds_validation() {
        // Valid edge cases
        assertNull(LocationValidator.validateLatitude("-90.0"))
        assertNull(LocationValidator.validateLatitude("0.0"))
        assertNull(LocationValidator.validateLatitude("90.0"))
        assertNull(LocationValidator.validateLatitude("36.8065"))

        // Invalid cases
        assertNotNull(LocationValidator.validateLatitude("-90.0001"))
        assertNotNull(LocationValidator.validateLatitude("90.0001"))
        assertNotNull(LocationValidator.validateLatitude("abc"))
        assertNotNull(LocationValidator.validateLatitude(""))
    }

    @Test
    fun longitudeBounds_validation() {
        // Valid edge cases
        assertNull(LocationValidator.validateLongitude("-180.0"))
        assertNull(LocationValidator.validateLongitude("0.0"))
        assertNull(LocationValidator.validateLongitude("180.0"))
        assertNull(LocationValidator.validateLongitude("10.1815"))

        // Invalid cases
        assertNotNull(LocationValidator.validateLongitude("-180.0001"))
        assertNotNull(LocationValidator.validateLongitude("180.0001"))
        assertNotNull(LocationValidator.validateLongitude("invalid"))
        assertNotNull(LocationValidator.validateLongitude(""))
    }

    @Test
    fun nonFiniteCoordinates_areRejected() {
        assertNotNull(LocationValidator.validateLatitude("NaN"))
        assertNotNull(LocationValidator.validateLatitude("Infinity"))
        assertNotNull(LocationValidator.validateLatitude("-Infinity"))
        assertNotNull(LocationValidator.validateLongitude("NaN"))
        assertNotNull(LocationValidator.validateLongitude("Infinity"))
        assertNotNull(LocationValidator.validateLongitude("-Infinity"))
    }

    @Test
    fun ianaTimezone_validation() {
        // Valid canonical timezones and IANA aliases
        assertNull(LocationValidator.validateTimezoneId("Africa/Tunis"))
        assertNull(LocationValidator.validateTimezoneId("Europe/Paris"))
        assertNull(LocationValidator.validateTimezoneId("UTC"))
        assertNull(LocationValidator.validateTimezoneId("Asia/Riyadh"))

        // Invalid timezone IDs
        assertNotNull(LocationValidator.validateTimezoneId("NonExistent/City"))
        assertNotNull(LocationValidator.validateTimezoneId("random_string"))
        assertNotNull(LocationValidator.validateTimezoneId(""))
    }

    @Test
    fun offsetStyleTimeZones_areRejected() {
        // These resolve in java.time but have no historical DST rules and are not IANA IDs.
        assertNotNull(LocationValidator.validateTimezoneId("+01:00"))
        assertNotNull(LocationValidator.validateTimezoneId("-05:00"))
        assertNotNull(LocationValidator.validateTimezoneId("Z"))
        assertNotNull(LocationValidator.validateTimezoneId("UTC+01:00"))
    }

    @Test
    fun fullValidation_rejectsOffsetZoneAndNonFiniteCoordinate() {
        val result = LocationValidator.validate(
            name = "Tunis",
            latitudeStr = "NaN",
            longitudeStr = "10.1815",
            timezoneId = "+01:00"
        )

        assertFalse(result.isValid)
        assertNotNull(result.latitudeError)
        assertNotNull(result.timezoneError)
    }

    @Test
    fun adjustments_blankSignedAndInRange_areValid() {
        listOf("", " ", "0", "5", "+2", "-3", "60", "-60").forEach {
            assertNull("'$it' should be valid", LocationValidator.validateAdjustment(it))
        }
        assertEquals(2, LocationValidator.parseAdjustment("+2"))
        assertEquals(-3, LocationValidator.parseAdjustment("-3"))
        assertEquals(0, LocationValidator.parseAdjustment(""))
    }

    @Test
    fun adjustments_outOfRangeOrMalformed_failValidation() {
        listOf("61", "-61", "1.5", "abc", "--2").forEach {
            assertNotNull("'$it' should be invalid", LocationValidator.validateAdjustment(it))
        }
        val result = LocationValidator.validate(
            name = "Tunis",
            latitudeStr = "36.8065",
            longitudeStr = "10.1815",
            timezoneId = "Africa/Tunis",
            adjustmentStrs = listOf("0", "0", "99", "0", "0")
        )
        assertFalse(result.isValid)
        assertNotNull(result.adjustmentError)
    }

    @Test
    fun coordinates_acceptDecimalComma() {
        assertEquals(34.7406, LocationValidator.parseCoordinate("34,7406")!!, 0.0)
        assertEquals(-10.5, LocationValidator.parseCoordinate(" -10,5 ")!!, 0.0)
        assertNull(LocationValidator.validateLatitude("34,7406"))
        assertNull(LocationValidator.parseCoordinate("1,2.3"))
        assertNotNull(LocationValidator.validateLongitude("1,2,3"))
    }
}
