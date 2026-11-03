package com.example.prayeralarm.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.prayeralarm.util.TimeZoneGuesser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume

/** A place found by GPS or by name, ready to fill the location form. */
data class LookupResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String
)

/**
 * Finds coordinates for the location form, using only the platform APIs
 * (LocationManager + Geocoder), so no Google Play Services or API key is required.
 *
 * Geocoding needs a network connection and a geocoder backend on the device; when either
 * is missing these calls return empty results and the user can still type coordinates.
 */
class LocationLookup(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    val canGeocode: Boolean get() = Geocoder.isPresent()

    fun hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The device's position, named by reverse geocoding when possible. The device timezone
     * is kept when it belongs to the country found there (it then knows the right zone in
     * multi-zone countries); otherwise, e.g. a phone left on its home zone, the zone is
     * derived from the country.
     */
    suspend fun currentLocation(): LookupResult? {
        if (!hasLocationPermission()) return null
        val location = freshLocation() ?: lastKnownLocation() ?: return null
        val address = reverseGeocode(location.latitude, location.longitude)
        return LookupResult(
            name = address?.displayName() ?: "My location",
            latitude = location.latitude.roundTo4(),
            longitude = location.longitude.roundTo4(),
            timeZoneId = address?.let { zoneFor(it, location.longitude) } ?: ZoneId.systemDefault().id
        )
    }

    /** Up to [maxResults] places matching [query], e.g. "Sousse" or "Paris, France". */
    suspend fun search(query: String, maxResults: Int = 5): List<LookupResult> {
        if (query.isBlank() || !canGeocode) return emptyList()
        val addresses = geocode { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query.trim(), maxResults) { callback(it) }
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocationName(query.trim(), maxResults).orEmpty())
            }
        }
        return addresses
            .filter { it.hasLatitude() && it.hasLongitude() }
            .map { address ->
                LookupResult(
                    name = address.displayName() ?: query.trim(),
                    latitude = address.latitude.roundTo4(),
                    longitude = address.longitude.roundTo4(),
                    timeZoneId = zoneFor(address, address.longitude) ?: ZoneId.systemDefault().id
                )
            }
            .distinctBy { Triple(it.name, it.latitude, it.longitude) }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): Address? {
        if (!canGeocode) return null
        return geocode { geocoder, callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { callback(it) }
            } else {
                @Suppress("DEPRECATION")
                callback(geocoder.getFromLocation(latitude, longitude, 1).orEmpty())
            }
        }.firstOrNull()
    }

    /**
     * Runs a Geocoder request on a background thread with a timeout. Geocoder throws
     * IOException when offline; that is reported as "no results" rather than a crash.
     */
    private suspend fun geocode(
        request: (Geocoder, (List<Address>) -> Unit) -> Unit
    ): List<Address> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val deliver: (List<Address>) -> Unit = {
                    if (continuation.isActive) continuation.resume(it)
                }
                runCatching { request(Geocoder(appContext, Locale.getDefault()), deliver) }
                    .onFailure { deliver(emptyList()) }
            }
        }.orEmpty()
    }

    @SuppressLint("MissingPermission") // Checked by hasLocationPermission() in currentLocation().
    private suspend fun freshLocation(): Location? {
        val provider = bestProvider() ?: return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(appContext)
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                }.onFailure { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? = locationManager.getProviders(true)
        .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }

    /** Network is fast and enough for prayer times; GPS is used only when it is all there is. */
    private fun bestProvider(): String? {
        val enabled = locationManager.getProviders(true)
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            // Before Android 12, GPS needs the precise-location permission.
            if (fineGranted || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.GPS_PROVIDER)
        }
        return preferred.firstOrNull { it in enabled }
    }

    private fun zoneFor(address: Address, longitude: Double): String? {
        val country = address.countryCode ?: return null
        val candidates = android.icu.util.TimeZone.getAvailableIDs(country).toList()
        val deviceZone = ZoneId.systemDefault().id
        if (deviceZone in candidates) return deviceZone
        return TimeZoneGuesser.pick(candidates, longitude)
    }

    private fun Address.displayName(): String? {
        val place = locality ?: subAdminArea ?: adminArea ?: featureName ?: return null
        return if (countryName != null && countryName != place) "$place, $countryName" else place
    }

    private fun Double.roundTo4(): Double = Math.round(this * 10_000) / 10_000.0

    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private const val GEOCODE_TIMEOUT_MS = 10_000L
    }
}
