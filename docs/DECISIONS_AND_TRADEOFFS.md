# Engineering Decisions & Tradeoffs

Every architecture involves tradeoffs. This document records critical design choices made in Adhan, the alternatives considered, and the technical justification.

---

## 1. On-Demand Location vs. Background Geolocation

- **Decision** (revised 2026-11-03): No background or continuous location. The location form has an optional, one-shot **"Use my current location"** button; manual coordinates and the preloaded Tunisian cities remain the default path.
- **Original decision**: Zero location permission, manual entry only.
- **Alternatives Considered**: Google Play Services `FusedLocationProviderClient` with continuous tracking.
- **Reasoning**:
  - *Battery Life*: a single fix when the user taps the button costs nothing measurable; nothing runs in the background.
  - *Privacy*: the permission is requested only when the button is tapped, approximate location is enough (prayer times barely change within a few km), and the user can still decline and type coordinates.
  - *No Play Services*: platform `LocationManager` via `LocationManagerCompat`, so de-Googled phones work too.
  - *Determinism*: the fix only fills the form; saved locations still never move on their own.
- **Tradeoff**: When traveling, the user still adds or selects the new city manually (now in two taps).

---

## 2. On-Device Astronomical Math vs. Cloud Prayer APIs (e.g. Aladhan API)

- **Decision**: 100% on-device astronomical calculation.
- **Alternatives Considered**: Fetching daily timings from REST APIs.
- **Reasoning**:
  - Cloud APIs introduce network latency, require internet access, can suffer outages, and can change their backend algorithms without notice.
  - An alarm must wake the user up even if their phone has no internet connection or is in flight mode during the night.
- **Tradeoff**: We must implement, verify, and unit-test the spherical trigonometry algorithms directly in Kotlin.

---

## 3. Jetpack Compose vs. Legacy XML Views

- **Decision**: 100% Jetpack Compose with Material 3.
- **Alternatives Considered**: XML layouts with `ConstraintLayout`, `RecyclerView`, and `ViewBinding`.
- **Reasoning**:
  - Compose is Google's modern, recommended declarative framework.
  - Far fewer lines of code, seamless dynamic theme switching, eliminates view-binding boilerplate, and provides first-class state observation with Kotlin `StateFlow`.
- **Tradeoff**: Requires team familiarity with declarative UI and recomposition lifecycle.

---

## 4. Native `AlarmManager` vs. Android `WorkManager` for Wakeups

- **Decision**: Use `AlarmManager.setExactAndAllowWhileIdle()` for waking alarms.
- **Alternatives Considered**: `WorkManager.enqueueUniquePeriodicWork()`.
- **Reasoning**:
  - Google's official documentation explicitly specifies that `WorkManager` is for deferrable background jobs. In deep Doze mode, periodic workers are batched and delayed by up to 30+ minutes.
  - A Fajr alarm scheduled for 05:10 must not ring at 05:42.
- **Tradeoff**: Requires exact alarm permissions and explicit handling of phone reboots via `RECEIVE_BOOT_COMPLETED`.

---

## 6. Location Search & Geocoding (Phase 3)

> **Implemented differently (2026-11-03):** city search uses the platform `android.location.Geocoder`
> (no HTTP client, User-Agent, rate limiting or attribution UI to maintain, no extra dependency).
> The timezone comes from the geocoded country via ICU (`android.icu.util.TimeZone.getAvailableIDs(country)`);
> for multi-zone countries the zone whose UTC offset is closest to longitude/15 h is picked
> (`util/TimeZoneGuesser.kt`), and the user can edit it with live suggestions. Tradeoff: some devices
> ship without a geocoder backend — the search field is then hidden and manual entry still works.
> The Nominatim/timezonemap plan below is kept as the fallback option if Geocoder proves unreliable.

### 6.1 Geocoding provider: OpenStreetMap Nominatim

- **Decision**: Use OpenStreetMap's Nominatim service for city search (query → coordinates + place details).
- **Official policy consulted**: https://operations.osmfoundation.org/policies/nominatim/ (Nominatim Usage Policy / Geocoding Policy).
- **Why it was selected**:
  - Free to use, no paid plan, no mandatory API key, no account, no backend.
  - Permits end-user-triggered search from a mobile app for a moderate number of users.
  - Returns coordinates (latitude/longitude) plus address components (city, admin area, country) needed to distinguish similarly named places.
- **Usage restrictions that the implementation must respect**:
  - Absolute maximum of **1 request per second** (per application, summed across all users).
  - A **custom HTTP User-Agent** identifying the application must be sent (stock HTTP library user-agents are rejected).
  - **Attribution** must be displayed (data is under the ODbL license).
  - **Autocomplete / per-keystroke search is strictly forbidden** — searches must be explicit (search action) and debounced; never one request per keystroke.
  - Requests must be user-triggered, not automatic polling.
  - Results must be cached on the client; the app must be able to switch providers without a software update.
  - No personal data beyond the location query is sent.
- **Privacy**: Only the city/search query is sent to Nominatim. No GPS, no device identifiers, no user account information.

### 6.2 Timezone resolution

- **Decision**: Resolve the IANA timezone ID offline using `us.dustinj.timezonemap:timezonemap:4.5` (Maven Central), with a **manual IANA timezone picker** as fallback when resolution returns no result.
- **Why it was selected**:
  - Free, no API key, no account, no backend, works offline.
  - Returns canonical IANA identifiers (e.g., `Europe/London`, `America/New_York`, `Africa/Tunis`) derived from OpenStreetMap time-zone boundaries.
  - Android SDK 19+ compatible.
- **Known limitations (documented)**:
  - The library's latest release (4.5) dates from 2021 and is no longer actively maintained; it is treated as a non-critical, replaceable component.
  - It initializes per bounding-box region; the app initializes it with world bounds so any searched city can be resolved.
  - It adds transitive dependencies (Esri geometry API, Apache Commons Compress) and an optional native zstd component.
- **Fallback behavior**: If a searched location's timezone cannot be resolved (ocean, unindexed, or library failure), the result is **rejected**. The user is shown an error and offered a manual picker populated from `java.time.ZoneId.getAvailableZoneIds()`. The location is **never** silently saved with `Africa/Tunis` or any guessed timezone.
- **Alternatives considered and rejected**:
  - Free web timezone-by-coordinates services (e.g., TimeZoneDB, GeoNames): require a mandatory API key / account, violating the "no mandatory API key" rule.
  - Android's location time-zone detection: resolves only the device's own location, not an arbitrary searched city.
  - Deriving a timezone from longitude alone: inaccurate (ignores time-zone boundaries and daylight-saving rules); explicitly prohibited.

### 6.3 Duplicate-location identity

- **Decision**: Duplicate detection uses normalized name + latitude + longitude + timeZoneId (exact match on all four), reusing the existing row when an equivalent saved location already exists. Distinct coordinates or timezones are treated as different locations, so legitimately similar places (e.g., same city name in different countries) are not merged.

## 7. Decision Revision History

| Date | Topic | Decision | Status |
| :---: | :--- | :--- | :---: |
| 2026-10-29 | Build System | Use Gradle 8.11.1 + AGP 8.7.3 with Kotlin 2.0.21 | **Verified & Passing** |
| 2026-10-29 | Java Compatibility | Target Java 17 bytecode | **Verified & Passing** |
| 2026-10-29 | SDK Targets | compileSdk 35, minSdk 26, targetSdk 35 | **Verified & Passing** |

