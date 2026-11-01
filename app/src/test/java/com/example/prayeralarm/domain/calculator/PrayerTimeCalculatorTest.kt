package com.example.prayeralarm.domain.calculator

import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.domain.model.SavedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Deterministic unit tests for the prayer time calculation engine and countdown calculations.
 */
class PrayerTimeCalculatorTest {

    private val mahdia = SavedLocation(
        name = "Mahdia",
        latitude = 35.5047,
        longitude = 11.0622,
        timeZoneId = "Africa/Tunis"
    )

    private val kelibia = SavedLocation(
        name = "Kelibia",
        latitude = 36.8476,
        longitude = 11.0939,
        timeZoneId = "Africa/Tunis"
    )

    private val ariana = SavedLocation(
        name = "Ariana",
        latitude = 36.8625,
        longitude = 10.1956,
        timeZoneId = "Africa/Tunis"
    )

    private val testDate = LocalDate.of(2026, 10, 29)

    @Test
    fun calculateTimetable_producesChronologicalPrayersIncludingSunrise() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)

        assertEquals(5, timetable.items.size)
        assertEquals(testDate, timetable.date)
        assertEquals(ZoneId.of("Africa/Tunis"), timetable.locationZoneId)
        assertEquals(PrayerCalculationMethod.TUNISIA, timetable.calculationMethod)

        val fajr = timetable.prayerDateTimes[PrayerType.FAJR]!!
        val sunrise = timetable.sunrise
        val dhuhr = timetable.prayerDateTimes[PrayerType.DHUHR]!!
        val asr = timetable.prayerDateTimes[PrayerType.ASR]!!
        val maghrib = timetable.prayerDateTimes[PrayerType.MAGHRIB]!!
        val isha = timetable.prayerDateTimes[PrayerType.ISHA]!!

        assertTrue("Fajr must precede Sunrise", fajr.isBefore(sunrise))
        assertTrue("Sunrise must precede Dhuhr", sunrise.isBefore(dhuhr))
        assertTrue("Dhuhr must precede Asr", dhuhr.isBefore(asr))
        assertTrue("Asr must precede Maghrib", asr.isBefore(maghrib))
        assertTrue("Maghrib must precede Isha", maghrib.isBefore(isha))

        listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).forEach { event ->
            assertEquals(ZoneId.of("Africa/Tunis"), event.zone)
        }
    }

    @Test
    fun calculationMethod_preservesTheExistingTunisianConvention() {
        val method = PrayerCalculationMethod.DEFAULT

        assertEquals(PrayerCalculationMethod.TUNISIA, method)
        assertEquals("Tunisia (18°/18°)", method.displayName)
        val params = method.createParameters()
        assertEquals(18.0, params.fajrAngle, 0.0)
        assertEquals(18.0, params.ishaAngle, 0.0)
    }

    @Test
    fun differentLocations_produceDistinctPrayerTimes() {
        val mahdiaTimetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val arianaTimetable = PrayerTimeCalculator.calculateTimetable(ariana, testDate)

        val mahdiaMaghrib = mahdiaTimetable.items.find { it.type == PrayerType.MAGHRIB }!!.time
        val arianaMaghrib = arianaTimetable.items.find { it.type == PrayerType.MAGHRIB }!!.time

        // Ariana (10.1956° E) is west of Mahdia (11.0622° E), so sunset occurs later in Ariana
        assertNotEquals(
            "Different coordinates must produce different prayer times",
            mahdiaMaghrib,
            arianaMaghrib
        )
        assertTrue(
            "Ariana Maghrib must occur after Mahdia Maghrib",
            arianaMaghrib.isAfter(mahdiaMaghrib)
        )
    }

    /**
     * Aladhan API method 18 ("Tunisia", Fajr 18°, Isha 18°, school=0) was used as
     * an independent reference. Times are rounded to the nearest minute.
     * Adhan's Asr can differ from Aladhan by up to two minutes because of the
     * shadow-angle implementation/rounding; all other events match within rounding.
     */
    @Test
    fun referenceTimetables_matchAladhanMethod18WithinTwoMinutes() {
        val references = listOf(
            Reference("Mahdia", mahdia, "2026-10-29", "05:09", "06:35", "11:59", "15:01", "17:24", "18:50"),
            Reference("Mahdia", mahdia, "2026-04-15", "04:14", "05:43", "12:16", "15:56", "18:49", "20:19"),
            Reference("Mahdia", mahdia, "2026-12-31", "05:54", "07:25", "12:19", "14:55", "17:13", "18:44"),
            Reference("Mahdia", mahdia, "2027-01-01", "05:54", "07:25", "12:19", "14:55", "17:14", "18:45"),
            Reference("Ariana", ariana, "2026-10-29", "05:13", "06:40", "12:03", "15:02", "17:25", "18:53"),
            Reference("Ariana", ariana, "2026-04-15", "04:14", "05:45", "12:19", "16:00", "18:54", "20:26"),
            Reference("Ariana", ariana, "2026-12-31", "05:59", "07:32", "12:22", "14:54", "17:13", "18:45"),
            Reference("Ariana", ariana, "2027-01-01", "05:59", "07:32", "12:23", "14:55", "17:13", "18:46"),
            Reference("Kelibia", kelibia, "2026-10-29", "05:09", "06:36", "11:59", "14:59", "17:22", "18:49"),
            Reference("Kelibia", kelibia, "2026-04-15", "04:10", "05:42", "12:16", "15:56", "18:50", "20:22"),
            Reference("Kelibia", kelibia, "2026-12-31", "05:55", "07:28", "12:19", "14:51", "17:09", "18:42"),
            Reference("Kelibia", kelibia, "2027-01-01", "05:56", "07:28", "12:19", "14:52", "17:10", "18:43")
        )

        references.forEach { reference ->
            val timetable = PrayerTimeCalculator.calculateTimetable(
                reference.location,
                LocalDate.parse(reference.date)
            )
            val label = "${reference.city} ${reference.date}"

            assertTimeNear("$label Fajr", reference.fajr, timetable.prayerDateTimes[PrayerType.FAJR]!!)
            assertTimeNear("$label Sunrise", reference.sunrise, timetable.sunrise)
            assertTimeNear("$label Dhuhr", reference.dhuhr, timetable.prayerDateTimes[PrayerType.DHUHR]!!)
            assertTimeNear("$label Asr", reference.asr, timetable.prayerDateTimes[PrayerType.ASR]!!)
            assertTimeNear("$label Maghrib", reference.maghrib, timetable.prayerDateTimes[PrayerType.MAGHRIB]!!)
            assertTimeNear("$label Isha", reference.isha, timetable.prayerDateTimes[PrayerType.ISHA]!!)
        }
    }

    @Test
    fun yearBoundary_usesTheRequestedLocalDateAndKeepsChronology() {
        val lastDay = LocalDate.of(2026, 12, 31)
        val firstDay = LocalDate.of(2027, 1, 1)

        val lastTimetable = PrayerTimeCalculator.calculateTimetable(mahdia, lastDay)
        val firstTimetable = PrayerTimeCalculator.calculateTimetable(mahdia, firstDay)

        assertEquals(lastDay, lastTimetable.date)
        assertEquals(firstDay, firstTimetable.date)
        assertEquals(lastDay, lastTimetable.prayerDateTimes[PrayerType.FAJR]!!.toLocalDate())
        assertEquals(firstDay, firstTimetable.prayerDateTimes[PrayerType.FAJR]!!.toLocalDate())
        assertTrue(
            lastTimetable.prayerDateTimes[PrayerType.ISHA]!!.isBefore(
                firstTimetable.prayerDateTimes[PrayerType.FAJR]!!
            )
        )
    }

    @Test
    fun monthBoundary_calculatesBothSidesIndependently() {
        val january = LocalDate.of(2027, 1, 31)
        val february = LocalDate.of(2027, 2, 1)

        val januaryTimetable = PrayerTimeCalculator.calculateTimetable(mahdia, january)
        val februaryTimetable = PrayerTimeCalculator.calculateTimetable(mahdia, february)

        assertEquals(31, januaryTimetable.prayerDateTimes[PrayerType.FAJR]!!.dayOfMonth)
        assertEquals(1, februaryTimetable.prayerDateTimes[PrayerType.FAJR]!!.dayOfMonth)
        assertEquals(2, februaryTimetable.prayerDateTimes[PrayerType.FAJR]!!.monthValue)
        assertTrue(
            januaryTimetable.prayerDateTimes[PrayerType.ISHA]!!.isBefore(
                februaryTimetable.prayerDateTimes[PrayerType.FAJR]!!
            )
        )
    }

    @Test
    fun dstTransition_usesTheIanaZoneRulesInsteadOfAFixedOffset() {
        // EU summer time starts on 2026-03-29.
        val paris = SavedLocation(
            name = "Paris",
            latitude = 48.8566,
            longitude = 2.3522,
            timeZoneId = "Europe/Paris"
        )

        val before = PrayerTimeCalculator.calculateTimetable(paris, LocalDate.of(2026, 3, 28))
        val after = PrayerTimeCalculator.calculateTimetable(paris, LocalDate.of(2026, 3, 29))

        assertEquals(ZoneOffset.ofHours(1), before.prayerDateTimes[PrayerType.FAJR]!!.offset)
        assertEquals(ZoneOffset.ofHours(2), after.prayerDateTimes[PrayerType.FAJR]!!.offset)
        assertEquals(ZoneId.of("Europe/Paris"), after.locationZoneId)
        assertEquals(ZoneOffset.ofHours(2), after.sunrise.offset)
    }

    @Test
    fun invalidTimeZone_fallsBackToTunisiaButReportsTheResolvedZone() {
        val broken = mahdia.copy(timeZoneId = "Not/AZone")

        val timetable = PrayerTimeCalculator.calculateTimetable(broken, testDate)

        assertEquals(ZoneId.of("Africa/Tunis"), timetable.locationZoneId)
        assertEquals(ZoneId.of("Africa/Tunis"), timetable.prayerDateTimes[PrayerType.FAJR]!!.zone)
    }

    @Test
    fun computeNextPrayerAndCountdown_beforeFajr() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val fajrZdt = timetable.prayerDateTimes[PrayerType.FAJR]!!

        // Set 'now' to 1 hour and 30 minutes before Fajr
        val now = fajrZdt.minusHours(1).minusMinutes(30).toInstant()

        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = mahdia,
            currentDate = testDate,
            now = now,
            todayTimetable = timetable
        )

        assertNotNull(countdown.nextPrayerItem)
        assertEquals(PrayerType.FAJR, countdown.nextPrayerItem?.type)
        assertEquals(1L, countdown.remainingHours)
        assertEquals(30L, countdown.remainingMinutes)
        assertEquals(0L, countdown.remainingSeconds)
        assertFalse(countdown.isPastAllPrayersForDay)
    }

    @Test
    fun computeNextPrayerAndCountdown_betweenDhuhrAndAsr() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val asrZdt = timetable.prayerDateTimes[PrayerType.ASR]!!

        // Set 'now' to exactly 45 minutes and 15 seconds before Asr
        val now = asrZdt.minusMinutes(45).minusSeconds(15).toInstant()

        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = mahdia,
            currentDate = testDate,
            now = now,
            todayTimetable = timetable
        )

        assertEquals(PrayerType.ASR, countdown.nextPrayerItem?.type)
        assertEquals(0L, countdown.remainingHours)
        assertEquals(45L, countdown.remainingMinutes)
        assertEquals(15L, countdown.remainingSeconds)
    }

    @Test
    fun computeNextPrayerAndCountdown_lessThanOneMinute_preservesSeconds() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val fajrZdt = timetable.prayerDateTimes[PrayerType.FAJR]!!
        val now = fajrZdt.minusSeconds(59).toInstant()

        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = mahdia,
            currentDate = testDate,
            now = now,
            todayTimetable = timetable
        )

        assertEquals(0L, countdown.remainingHours)
        assertEquals(0L, countdown.remainingMinutes)
        assertEquals(59L, countdown.remainingSeconds)
    }

    @Test
    fun computeNextPrayerAndCountdown_atPrayerBoundary_advancesToNextPrayer() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val dhuhrZdt = timetable.prayerDateTimes[PrayerType.DHUHR]!!
        val now = dhuhrZdt.toInstant()

        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = mahdia,
            currentDate = testDate,
            now = now,
            todayTimetable = timetable
        )

        assertEquals(PrayerType.ASR, countdown.nextPrayerItem?.type)
        assertTrue(countdown.remainingHours >= 0)
    }

    @Test
    fun computeNextPrayerAndCountdown_afterIsha_rollsOverToTomorrowFajrWithoutChangingTodayRows() {
        val timetable = PrayerTimeCalculator.calculateTimetable(mahdia, testDate)
        val ishaZdt = timetable.prayerDateTimes[PrayerType.ISHA]!!
        val todayFajrTime = timetable.prayerDateTimes[PrayerType.FAJR]!!.toLocalTime()

        // Set 'now' to 10 minutes after Isha
        val now = ishaZdt.plusMinutes(10).toInstant()

        val countdown = PrayerTimeCalculator.computeNextPrayerAndCountdown(
            location = mahdia,
            currentDate = testDate,
            now = now,
            todayTimetable = timetable
        )

        // After Isha, next prayer is Fajr tomorrow
        assertEquals(PrayerType.FAJR, countdown.nextPrayerItem?.type)
        assertTrue(countdown.isPastAllPrayersForDay)
        assertTrue(countdown.remainingHours > 0)

        val tomorrowFajr = PrayerTimeCalculator.calculateTimetable(mahdia, testDate.plusDays(1))
            .prayerDateTimes[PrayerType.FAJR]!!.toLocalTime()
        assertEquals(tomorrowFajr.hour, countdown.nextPrayerItem?.time?.hour)
        assertEquals(tomorrowFajr.minute, countdown.nextPrayerItem?.time?.minute)
        assertEquals(tomorrowFajr.second, countdown.nextPrayerItem?.time?.second)

        // Today's timetable rows are immutable history: no row is highlighted and
        // today's Fajr time is not replaced by tomorrow's time.
        assertTrue(timetable.items.none { it.isNextPrayer })
        assertEquals(todayFajrTime, timetable.items.first { it.type == PrayerType.FAJR }.time)
    }

    @Test fun everyMethod_isChronological() {
        PrayerCalculationMethod.values().forEach { m ->
            val t = PrayerTimeCalculator.calculateTimetable(mahdia, testDate, m)
            val order = listOf(PrayerType.FAJR, PrayerType.DHUHR, PrayerType.ASR, PrayerType.MAGHRIB, PrayerType.ISHA)
                .map { t.prayerDateTimes[it]!! }
            assertEquals(order.sorted(), order)
        }
    }
    @Test fun hanafiAsr_isLaterThanStandard() {
        val std = PrayerTimeCalculator.calculateTimetable(mahdia, testDate).prayerDateTimes[PrayerType.ASR]!!
        val han = PrayerTimeCalculator.calculateTimetable(mahdia, testDate, madhab = com.batoulapps.adhan.Madhab.HANAFI).prayerDateTimes[PrayerType.ASR]!!
        assertTrue(han.isAfter(std))
    }
    @Test fun methodChoice_changesFajr() {
        val a = PrayerTimeCalculator.calculateTimetable(mahdia, testDate, PrayerCalculationMethod.EGYPTIAN)
        val b = PrayerTimeCalculator.calculateTimetable(mahdia, testDate, PrayerCalculationMethod.NORTH_AMERICA)
        assertTrue(a.prayerDateTimes[PrayerType.FAJR]!!.isBefore(b.prayerDateTimes[PrayerType.FAJR]!!))
    }
private fun assertTimeNear(
        label: String,
        expected: LocalTime,
        actual: java.time.ZonedDateTime,
        toleranceMinutes: Long = 2
    ) {
        val differenceSeconds = abs(Duration.between(expected, actual.toLocalTime()).seconds)
        assertTrue(
            "$label expected $expected (±${toleranceMinutes}m) but was ${actual.toLocalTime()}",
            differenceSeconds <= toleranceMinutes * 60
        )
    }

    private data class Reference(
        val city: String,
        val location: SavedLocation,
        val date: String,
        val fajr: LocalTime,
        val sunrise: LocalTime,
        val dhuhr: LocalTime,
        val asr: LocalTime,
        val maghrib: LocalTime,
        val isha: LocalTime
    ) {
        constructor(
            city: String,
            location: SavedLocation,
            date: String,
            fajr: String,
            sunrise: String,
            dhuhr: String,
            asr: String,
            maghrib: String,
            isha: String
        ) : this(
            city,
            location,
            date,
            LocalTime.parse(fajr),
            LocalTime.parse(sunrise),
            LocalTime.parse(dhuhr),
            LocalTime.parse(asr),
            LocalTime.parse(maghrib),
            LocalTime.parse(isha)
        )
    }
}
