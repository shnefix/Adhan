package com.example.prayeralarm.util

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * Picks a timezone for a place found by search, where only the country and coordinates
 * are known. There is no offline coordinate-to-zone map on Android, so for countries
 * with several zones the one whose UTC offset is closest to the solar offset
 * (longitude / 15 hours) wins. The user can still edit the result.
 */
object TimeZoneGuesser {

    fun pick(candidates: List<String>, longitude: Double, now: Instant = Instant.now()): String? {
        val known = candidates.filter { it in ZoneId.getAvailableZoneIds() }
        // ICU also lists legacy aliases such as "ART" or "Egypt" for a country; keep
        // canonical Region/City IDs whenever there is one.
        val canonical = known.filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
        val valid = canonical.ifEmpty { known }
        if (valid.size <= 1) return valid.firstOrNull()

        val solarOffsetSeconds = longitude / 15.0 * 3600
        return valid.minByOrNull { id ->
            val offset = ZoneId.of(id).rules.getStandardOffset(now).totalSeconds
            abs(offset - solarOffsetSeconds)
        }
    }

    /** Zone IDs containing [query] (case-insensitive), canonical "Region/City" IDs first. */
    fun suggestions(query: String, limit: Int = 6): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return ZoneId.getAvailableZoneIds()
            .filter { it.contains(q, ignoreCase = true) && it != q }
            .sortedWith(compareBy({ !it.contains('/') }, { !it.startsWith(q, ignoreCase = true) }, { it }))
            .take(limit)
    }
}
