package com.example.prayeralarm.alarm

import android.content.Context
import java.time.LocalDate

/**
 * Remembers which alarm series the user already stopped ("I woke up"), so the rest of that
 * series is cancelled and is not rescheduled by a later refresh, boot or settings change.
 * Entries older than a couple of days are pruned automatically.
 */
object DismissedSeries {
    private const val PREFS = "fajr_alarm_dismissed"
    private const val KEY = "series"

    @Volatile
    private var appContext: Context? = null

    /** Idempotent; call from Application.onCreate and from any entry point. */
    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun add(series: Int) {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        val updated = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        updated.add(series.toString())
        prefs.edit().putStringSet(KEY, updated).apply()
    }

    fun contains(series: Int): Boolean = all().contains(series)

    fun all(today: LocalDate = LocalDate.now()): Set<Int> {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return emptySet()
        val stored = prefs.getStringSet(KEY, emptySet()).orEmpty()
        val keepFrom = today.toEpochDay() - 2
        val live = stored.mapNotNull { it.toIntOrNull() }
            .filter { PrayerAlarmCoordinator.epochDayOf(it) >= keepFrom }
            .toSet()
        if (live.size != stored.size) {
            prefs.edit().putStringSet(KEY, live.map { it.toString() }.toSet()).apply()
        }
        return live
    }
}
