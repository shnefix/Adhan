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
import java.util.Date

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
 * @property calculationMethod The named convention used for Fajr/Isha/Asr
 */
data class CalculatedTimetable(
    val date: LocalDate,
    val items: List<PrayerScheduleItem>,
    val prayerDateTimes: Map<PrayerType, ZonedDateTime>,
    val sunrise: ZonedDateTime,
    val locationZoneId: ZoneId,
    val calculationMethod: PrayerCalculationMethod,
    val madhab: Madhab
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
     * @param location SavedLocation with latitude, longitude, and IANA timezone ID
     * @param date Local calendar date in the location's timezone
     * @param method Named calculation convention (defaults to [PrayerCalculationMethod.DEFAULT])
     * @return CalculatedTimetable containing prayer rows, exact ZonedDateTimes, sunrise, and metadata
     */
    fun calculateTimetable(
        location: SavedLocation,
        date: LocalDate,
        method: PrayerCalculationMethod = PrayerCalculationMethod.DEFAULT,
        madhab: Madhab = Madhab.SHAFI
    ): CalculatedTimetable {
        val zoneId = zoneIdFor(location)

        val coordinates = Coordinates(location.latitude, location.longitude)
        val dateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)

        val adhanPrayerTimes = com.batoulapps.adhan.PrayerTimes(
            coordinates,
            dateComponents,
            method.createParameters(madhab)
        )

        fun dateToZonedDateTime(dateObj: Date?): ZonedDateTime {
            return if (dateObj != null) {
                dateObj.toInstant().atZone(zoneId)
            } else {
                // Fallback to approximate noon if an extreme-latitude event is undefined.
                date.atTime(12, 0).atZone(zoneId)
            }
        }

        val fajrZdt = dateToZonedDateTime(adhanPrayerTimes.fajr)
        val sunriseZdt = dateToZonedDateTime(adhanPrayerTimes.sunrise)
        val dhuhrZdt = dateToZonedDateTime(adhanPrayerTimes.dhuhr)
        val asrZdt = dateToZonedDateTime(adhanPrayerTimes.asr)
        val maghribZdt = dateToZonedDateTime(adhanPrayerTimes.maghrib)
        val ishaZdt = dateToZonedDateTime(adhanPrayerTimes.isha)

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
            calculationMethod = method,
            madhab = madhab
        )
    }

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
            val nextDayTimetable = tomorrowTimetable ?: calculateTimetable(location, tomorrow)
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
