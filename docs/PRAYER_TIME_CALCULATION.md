# Prayer Time Calculation, Time Zones, and Location Defaults

This document records the prayer-time accuracy audit, the calculation convention
used by the app, the time-zone rules, the default/upgrade behavior for locations,
and the reference comparison that was performed.

---

## 1. Calculation convention

The app uses one named convention, `PrayerCalculationMethod.TUNISIA`:

| Parameter | Value |
| --- | --- |
| Fajr angle | 18.0° below the horizon |
| Isha angle | 18.0° below the horizon |
| Asr | Standard shadow factor (`Madhab.SHAFI`, also used by the Maliki convention) |
| High-latitude rule | None (Tunisia is far from the polar circles) |
| Calculation library | Batoul Apps Adhan 1.2.1 (Jean Meeus algorithms) |

This preserves the convention that was already configured before the audit. It is
a named implementation convention, not a claim that it is the only timetable used
in Tunisia. The convention is displayed on the Settings screen so it is explicit
to the user.

The engine exposes six astronomical events. Sunrise is returned as
`CalculatedTimetable.sunrise`; it is not one of the five obligatory prayers, so it
is not added to `PrayerType` or to the alarm rows.

---

## 2. Time-zone and date handling

### Conversion path

Adhan returns `java.util.Date` values that represent UTC instants. The app converts
each one exactly once:

```kotlin
dateObj.toInstant().atZone(locationZoneId)
```

The app does **not** reinterpret a wall-clock value as UTC and does **not** apply a
hardcoded minute offset.

- The location's stored IANA zone (for example `Africa/Tunis`) is the only zone
  used for its timetable.
- The current local date is obtained with `LocalDate.now(clock.withZone(zoneId))`.
- Countdowns compare `Instant` values, so the device's default zone cannot shift a
  prayer by an hour.
- An invalid persisted zone falls back to `Africa/Tunis`; valid user input is
  rejected by `LocationValidator` before it is saved.

### Daylight saving time

`java.time.ZoneId` supplies the historical and future offset rules for each IANA
zone. No fixed UTC offset is stored. A regression test uses `Europe/Paris` across
the 2026-03-29 DST transition and verifies that the calculated Fajr offset changes
from `+01:00` to `+02:00`. Tunisia itself has not observed DST since 2009, but the
same code path supports locations that do.

### Date boundaries

Tests cover:

- the last day of a month → first day of the next month;
- 2026-12-31 → 2027-01-01;
- the post-Isha rollover to tomorrow's Fajr;
- prayer selection exactly at a prayer timestamp (the next prayer is selected).

After Isha, tomorrow's Fajr is shown in the next-prayer card/notification. The
rows in today's timetable are left untouched and no row is marked "next", so
today's Fajr time is never overwritten with tomorrow's time.

---

## 3. Default locations, seeding, and upgrades

A fresh install is seeded with exactly these three rows, in this order:

| Order | Name | Latitude | Longitude | IANA zone |
| --- | --- | --- | --- | --- |
| 1 | Mahdia | 35.5047 | 11.0622 | Africa/Tunis |
| 2 | Ariana | 36.8625 | 10.1956 | Africa/Tunis |
| 3 | Kelibia | 36.8476 | 11.0939 | Africa/Tunis |

Mahdia is selected after a fresh seed.

`LocationSeeder` runs at application startup and is intentionally conservative:

1. It inserts the defaults **only** when the location table is empty.
2. It never deletes, updates, or re-inserts existing rows.
3. It preserves a valid existing selection.
4. It repairs only an unset selection or a selection pointing at a deleted row.
5. A mutex prevents two concurrent startup passes from double-seeding.

The existing `MIGRATION_1_2` behavior is retained: on upgrade it inserts a default
row only when an exact identity match (name, latitude, longitude, time zone) does
not already exist. It does not delete or overwrite user data. Custom locations and
the user's selected location therefore survive an upgrade.

Users can add locations manually with a name, latitude, longitude, and IANA zone.
Coordinates must be finite and within ±90°/±180°. Offset-style zone IDs such as
`+01:00` or `Z` are rejected because they have no historical DST rules; only IANA
zone IDs known to `java.time` are accepted.

---

## 4. Reference comparison

### Reference source

The independent reference is the Aladhan API v1 `timings` endpoint using method
18 (labelled "Tunisia") and `school=0` (standard Asr):

```text
https://api.aladhan.com/v1/timings/DD-MM-YYYY?latitude=...&longitude=...&method=18&school=0
```

Reference values were captured during this audit and rounded to the nearest minute.
They are compared with the app's Adhan output for the same coordinates and dates.
This is a cross-check against a widely used published API, not a claim of exact
equivalence with an official Tunisian government timetable.

### Non-Asr events

For every row below, the app output matches the reference exactly to the minute.
The app's underlying values may include seconds; the table shows whole minutes.

| City | Date | Fajr | Sunrise | Dhuhr | Maghrib | Isha |
| --- | --- | --- | --- | --- | --- | --- |
| Mahdia | 2026-10-29 | 05:09 | 06:35 | 11:59 | 17:24 | 18:50 |
| Mahdia | 2026-04-15 | 04:14 | 05:43 | 12:16 | 18:49 | 20:19 |
| Mahdia | 2026-12-31 | 05:54 | 07:25 | 12:19 | 17:13 | 18:44 |
| Mahdia | 2027-01-01 | 05:54 | 07:25 | 12:19 | 17:13 | 18:45 |
| Ariana | 2026-10-29 | 05:13 | 06:40 | 12:03 | 17:25 | 18:53 |
| Ariana | 2026-04-15 | 04:14 | 05:45 | 12:19 | 18:54 | 20:26 |
| Ariana | 2026-12-31 | 05:59 | 07:32 | 12:22 | 17:13 | 18:45 |
| Ariana | 2027-01-01 | 05:59 | 07:32 | 12:23 | 17:13 | 18:46 |
| Kelibia | 2026-10-29 | 05:09 | 06:36 | 11:59 | 17:22 | 18:49 |
| Kelibia | 2026-04-15 | 04:10 | 05:42 | 12:16 | 18:50 | 20:22 |
| Kelibia | 2026-12-31 | 05:55 | 07:28 | 12:19 | 17:09 | 18:42 |
| Kelibia | 2027-01-01 | 05:56 | 07:28 | 12:19 | 17:10 | 18:43 |

### Asr

Asr differs by 0–2 minutes. This is an algorithm/rounding difference in the
shadow-angle calculation, not a time-zone error.

| City | Date | App | Aladhan | Difference |
| --- | --- | --- | --- | --- |
| Mahdia | 2026-10-29 | 15:00 | 15:01 | −1 min |
| Mahdia | 2026-04-15 | 15:56 | 15:56 | 0 |
| Mahdia | 2026-12-31 | 14:55 | 14:55 | 0 |
| Mahdia | 2027-01-01 | 14:56 | 14:55 | +1 min |
| Ariana | 2026-10-29 | 15:01 | 15:02 | −1 min |
| Ariana | 2026-04-15 | 16:01 | 16:00 | +1 min |
| Ariana | 2026-12-31 | 14:55 | 14:54 | +1 min |
| Ariana | 2027-01-01 | 14:56 | 14:55 | +1 min |
| Kelibia | 2026-10-29 | 14:57 | 14:59 | −2 min |
| Kelibia | 2026-04-15 | 15:57 | 15:56 | +1 min |
| Kelibia | 2026-12-31 | 14:51 | 14:51 | 0 |
| Kelibia | 2027-01-01 | 14:52 | 14:52 | 0 |

The automated reference test asserts a maximum difference of 2 minutes for every
event so that harmless library/rounding changes do not create a brittle build.

### Known limitations

- **Not an official timetable:** the app does not claim exact equivalence with any
  government-published Tunisian timetable. Official tables may use different
  rounding, safety margins, or a different Asr convention.
- **No elevation correction:** coordinates are used at sea level. Elevation can
  shift sunrise/sunset slightly at high altitude.
- **No high-latitude fallback rule:** this is acceptable for Tunisia but would
  need a policy for polar locations.
- **No geocoding:** locations are entered manually; there is no provider/API key.
- **Minute display:** the UI displays `HH:mm`; seconds are retained internally for
  countdowns but not shown in the timetable.
- **Asr convention:** the standard shadow factor is used. A Hanafi user would see
  a later Asr, and that choice is not currently user-configurable.

---

## 5. Countdown timing

The countdown uses prayer timestamps as the source of truth:

1. Prayer times are full `ZonedDateTime` values in the location's IANA zone.
2. Remaining time is `Duration.between(now, targetInstant)`.
3. The one-second ticker re-reads the real clock instead of decrementing counters,
   so scheduling latency does not accumulate as drift.
4. The current and following local day are cached; the ticker only recomputes when
   the local date changes.
5. After Isha the target is tomorrow's Fajr, with tomorrow's time shown in the
   next-prayer card and notification.

---

## 6. Tests and build

Relevant tests:

- `DefaultLocationsTest`: exact three defaults, coordinates, order, primary city,
  and identity matching.
- `PrayerTimeCalculatorTest`: all six events, reference comparison, chronological
  order, location differences, DST, invalid-zone fallback, month/year boundaries,
  and post-Isha behavior.
- `LocationSeederTest`: fresh seeding, idempotence, upgrade preservation, unset and
  dangling selection repair.
- `LocationRepositoryImplTest`: persistence, location switching, duplicate
  prevention, deletion fallback.
- `LocationValidatorTest`: coordinate bounds, non-finite values, IANA zones, and
  rejection of offset-style zones.
- `LocationRepositoryTest` / `PrayerAlarmCoordinatorTest`: existing repository and
  alarm scheduling behavior.

Commands used:

```bat
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The debug APK is produced at `app\build\outputs\apk\debug\Adhan-1.0-debug.apk`.
