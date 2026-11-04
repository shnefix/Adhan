package com.example.prayeralarm.data.notification

/**
 * How the notification shows the time left until the next prayer: hours and minutes
 * ("9h 54m", "45m"), switching to a ticking MM:SS countdown for the last 10 minutes.
 */
object CountdownFormat {

    const val SECONDS_THRESHOLD_MILLIS = 10 * 60_000L
    private const val MINUTE_MILLIS = 60_000L

    /** True when the last minutes are shown with seconds (by the self-ticking chronometer). */
    fun showsSeconds(remainingMillis: Long): Boolean = remainingMillis <= SECONDS_THRESHOLD_MILLIS

    /** Rounds up, so the text never reads "0m" while time is left and matches the clock at the switch. */
    fun hoursMinutes(remainingMillis: Long): String {
        val totalMinutes = (remainingMillis.coerceAtLeast(0L) + MINUTE_MILLIS - 1) / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /**
     * How long until the notification must be re-posted: when the shown minute changes,
     * or, once the seconds countdown runs by itself, just after the prayer starts.
     */
    fun millisUntilNextUpdate(remainingMillis: Long): Long {
        if (showsSeconds(remainingMillis)) return remainingMillis.coerceAtLeast(0L) + 1_000L
        val intoMinute = remainingMillis % MINUTE_MILLIS
        return if (intoMinute == 0L) MINUTE_MILLIS else intoMinute
    }
}
