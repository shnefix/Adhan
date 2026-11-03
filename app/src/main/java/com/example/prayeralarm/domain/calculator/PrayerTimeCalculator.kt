package com.example.prayeralarm.domain.calculator

import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.data.DateComponents
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.home.PrayerScheduleItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.math.abs
import kotlin.math.sign

/**
 * Result of timetable calculation for a specific location and calendar date.
 *
 * All instants are already converted to the location's IANA timezone. They are
 * never derived from the device's default timezone.
 *
 * @property date The local calendar date the timetable was calculated for
 * @property items The five daily prayer timetable rows
 * @property prayerDateTimes Map of PrayerType to full ZonedDateTime in the location's timezone
 * @property sunrise Sunrise (Shuruq) in the location's timezone. Sunrise is not one of the
 *   five obligatory prayers, so it is exposed separately instead of adding a [PrayerType].
 * @property locationZoneId The resolved IANA zone used for every timestamp in this result
 * @property calculationMethod The named convention actually used (the location's override, if any)
 * @property usedNearestLatitude True when the sun never rises or sets at this location on [date]
 *   (polar day/night) and times were taken from the nearest latitude where it does.
 */
data class CalculatedTimetable(
    val date: LocalDate,
    val items: List<PrayerScheduleItem>,
    val prayerDateTimes: Map<PrayerType, ZonedDateTime>,
    val sunrise: ZonedDateTime,
    val locationZoneId: ZoneId,
    val calculationMethod: PrayerCalculationMethod,
    val madhab: Madhab,
    val usedNearestLatitude: Boolean = false
)

/**
 * Information regarding the upcoming prayer and remaining duration.
 */
data class NextPrayerCountdown(
    val nextPrayerItem: PrayerScheduleItem?,
    val nextPrayerDateTime: ZonedDateTime?,
    val remainingHours: Long,
    val remainingMinutes: Long,
    val remainingSeconds: Long,
    val isPastAllPrayersForDay: Boolean = false
)

/**
 * Clean astronomical calculation engine for Islamic prayer times.
 *
 * Uses the Batoul Apps Adhan engine (Jean Meeus astronomical algorithms) and an
 * explicit [PrayerCalculationMethod]. All Adhan results are UTC instants; they
 * are converted exactly once with `Instant.atZone(locationZone)`. No wall-clock
 * value is reinterpreted as UTC and no fixed minute offset is applied.
 */
object PrayerTimeCalculator {

    /**
     * Latitude used instead of the real one during polar day/night ("aqrab al-bilad").
     * Just below the Arctic/Antarctic circle the sun rises and sets every day of the year.
     */
    const val NEAREST_LATITUDE = 65.0

    /** Safe fallback only for corrupt persisted data; valid user input is validated before saving. */
    private val FALLBACK_ZONE: ZoneId = ZoneId.of("Africa/Tunis")

    /**
     * Resolves the IANA zone stored with a location.
     *
     * A corrupt/unknown zone falls back to Africa/Tunis so the app remains usable,
     * while the returned [CalculatedTimetable.locationZoneId] makes the actual
     * zone visible to callers and tests.
     */
    fun zoneIdFor(location: SavedLocation): ZoneId = zoneIdFor(location.timeZoneId)

    fun zoneIdFor(timeZoneId: String): ZoneId =
        runCatching { ZoneId.of(timeZoneId) }.getOrDefault(FALLBACK_ZONE)

    /**
     * Calculates the five daily prayers and sunrise for a location and local date.
     *
     * @param location SavedLocation with latitude, longitude, IANA timezone ID and minute adjustments
     * @param date Local calendar date in the location's timezone
     * @param method App-wide calculation convention; the location's own
     *   [SavedLocation.calculationMethod] takes precedence when set
     * @return CalculatedTimetable containing prayer rows, exact ZonedDateTimes, sunrise, and metadata
     */
    fun calculateTimetable(
        location: SavedLocation,
        date: LocalDate,
        method: PrayerCalculationMethod = PrayerCalculationMethod.DEFAULT,
        madhab: Madhab = Madhab.SHAFI
    ): CalculatedTimetable {
        val zoneId = zoneIdFor(location)
        val effectiveMethod = location.calculationMethod ?: method
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)

        fun adhanTimesAt(latitude: Double) = com.batoulapps.adhan.PrayerTimes(
            Coordinates(latitude, location.longitude),
            dateComponents,
            effectiveMethod.createParameters(madhab)
        )

        // Fajr/Isha at high latitudes are already handled by Adhan's HighLatitudeRule; what it
        // cannot handle is a day with no sunrise or sunset at all, where it returns nulls.
        var adhanPrayerTimes = adhanTimesAt(location.latitude)
        val polar = adhanPrayerTimes.hasMissingTimes() && abs(location.latitude) > NEAREST_LATITUDE
        if (polar) {
            adhanPrayerTimes = adhanTimesAt(NEAREST_LATITUDE * sign(location.latitude))
        }

        fun dateToZonedDateTime(dateObj: Date?): ZonedDateTime {
            return if (dateObj != null) {
                // Adhan leaks wall-clock milliseconds into its results (06:35:00.457), which made
                // identical inputs produce different instants; drop them so times are stable.
                dateObj.toInstant().truncatedTo(ChronoUnit.SECONDS).atZone(zoneId)
            } else {
                // Last resort if even the nearest-latitude calculation has no value.
                date.atTime(12, 0).atZone(zoneId)
            }
        }

        // The location's manual offsets are applied here, once, so the home screen,
        // countdown notification and alarms all agree on the same tuned times.
        val adjustments = location.adjustments
        fun adjusted(dateObj: Date?, type: PrayerType): ZonedDateTime =
            dateToZonedDateTime(dateObj).plusMinutes(adjustments.minutesFor(type).toLong())

        val fajrZdt = adjusted(adhanPrayerTimes.fajr, PrayerType.FAJR)
        val sunriseZdt = dateToZonedDateTime(adhanPrayerTimes.sunrise)
        val dhuhrZdt = adjusted(adhanPrayerTimes.dhuhr, PrayerType.DHUHR)
        val asrZdt = adjusted(adhanPrayerTimes.asr, PrayerType.ASR)
        val maghribZdt = adjusted(adhanPrayerTimes.maghrib, PrayerType.MAGHRIB)
        val ishaZdt = adjusted(adhanPrayerTimes.isha, PrayerType.ISHA)

        val prayerMap = mapOf(
            PrayerType.FAJR to fajrZdt,
            PrayerType.DHUHR to dhuhrZdt,
            PrayerType.ASR to asrZdt,
            PrayerType.MAGHRIB to maghribZdt,
            PrayerType.ISHA to ishaZdt
        )

        val items = listOf(
            PrayerScheduleItem(type = PrayerType.FAJR, time = fajrZdt.toLocalTime()),
            PrayerScheduleItem(type = PrayerType.DHUHR, time = dhuhrZdt.toLocalTime()),
            PrayerScheduleItem(type = PrayerType.ASR, time = asrZdt.toLocalTime()),
            PrayerScheduleItem(type = PrayerType.MAGHRIB, time = maghribZdt.toLocalTime()),
            PrayerScheduleItem(type = PrayerType.ISHA, time = ishaZdt.toLocalTime())
        )

        return CalculatedTimetable(
            date = date,
            items = items,
            prayerDateTimes = prayerMap,
            sunrise = sunriseZdt,
            locationZoneId = zoneId,
            calculationMethod = effectiveMethod,
            madhab = madhab,
            usedNearestLatitude = polar
        )
    }

    private fun com.batoulapps.adhan.PrayerTimes.hasMissingTimes(): Boolean =
        listOf(fajr, sunrise, dhuhr, asr, maghrib, isha).any { it == null }

    /**
     * Determines the next upcoming prayer and computes the exact remaining countdown duration.
     *
     * Source of truth is the difference between [now] and the target prayer's [ZonedDateTime].
     * If all prayers for [currentDate] have passed, automatically calculates tomorrow's Fajr
     * so the countdown seamlessly points to tomorrow morning's dawn prayer.
     *
     * @param location The active SavedLocation
     * @param currentDate The current calendar date in the location's timezone
     * @param now The current instant
     * @param todayTimetable The calculated timetable for today
     * @return NextPrayerCountdown with highlighted item, target timestamp, and remaining H:M:S
     */
    fun computeNextPrayerAndCountdown(
        location: SavedLocation,
        currentDate: LocalDate,
        now: Instant,
        todayTimetable: CalculatedTimetable,
        tomorrowTimetable: CalculatedTimetable? = null
    ): NextPrayerCountdown {
        // Ordered list of today's prayers
        val prayersToday = listOf(
            Pair(PrayerType.FAJR, todayTimetable.prayerDateTimes[PrayerType.FAJR]!!),
            Pair(PrayerType.DHUHR, todayTimetable.prayerDateTimes[PrayerType.DHUHR]!!),
            Pair(PrayerType.ASR, todayTimetable.prayerDateTimes[PrayerType.ASR]!!),
            Pair(PrayerType.MAGHRIB, todayTimetable.prayerDateTimes[PrayerType.MAGHRIB]!!),
            Pair(PrayerType.ISHA, todayTimetable.prayerDateTimes[PrayerType.ISHA]!!)
        )

        // Find the first prayer strictly in the future relative to 'now'
        val upcoming = prayersToday.firstOrNull { (_, zdt) ->
            zdt.toInstant().isAfter(now)
        }

        val targetPrayerType: PrayerType
        val targetZdt: ZonedDateTime
        val isTomorrowFajr: Boolean

        if (upcoming != null) {
            targetPrayerType = upcoming.first
            targetZdt = upcoming.second
            isTomorrowFajr = false
        } else {
            // All prayers for today have passed. Next prayer is tomorrow's Fajr!
            val tomorrow = currentDate.plusDays(1)
            // Reuse today's method and madhab: falling back to the defaults here used to make
            // the after-Isha countdown disagree with the user's Settings.
            val nextDayTimetable = tomorrowTimetable ?: calculateTimetable(
                location,
                tomorrow,
                todayTimetable.calculationMethod,
                todayTimetable.madhab
            )
            targetPrayerType = PrayerType.FAJR
            targetZdt = nextDayTimetable.prayerDateTimes[PrayerType.FAJR]!!
            isTomorrowFajr = true
        }

        // Compute exact non-negative remaining seconds
        val targetInstant = targetZdt.toInstant()
        val totalSecondsRemaining = if (targetInstant.isAfter(now)) {
            java.time.Duration.between(now, targetInstant).seconds
        } else {
            0L
        }

        val hours = totalSecondsRemaining / 3600
        val minutes = (totalSecondsRemaining % 3600) / 60
        val seconds = totalSecondsRemaining % 60

        // Item shown by the "next prayer" card/notification.
        //
        // After Isha the target is tomorrow's Fajr. That timestamp must NOT be
        // written into today's Fajr row: today's timetable is historical data and
        // the UI highlights rows by PrayerType. Callers therefore keep the today
        // rows untouched and show this standalone item only in the next-prayer card.
        val highlightedItem = if (!isTomorrowFajr) {
            todayTimetable.items.find { it.type == targetPrayerType }?.copy(isNextPrayer = true)
        } else {
            PrayerScheduleItem(
                type = PrayerType.FAJR,
                time = targetZdt.toLocalTime(),
                isNextPrayer = true
            )
        }

        return NextPrayerCountdown(
            nextPrayerItem = highlightedItem,
            nextPrayerDateTime = targetZdt,
            remainingHours = hours,
            remainingMinutes = minutes,
            remainingSeconds = seconds,
            isPastAllPrayersForDay = isTomorrowFajr
        )
    }
}
