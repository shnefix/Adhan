# Prayer Alarm (adhan)

An Android app that calculates the five daily prayer times on the device and rings Fajr alarms,
with no account, no backend, and no internet required for the times themselves.

## Features

- **Prayer times on-device** using the [Adhan](https://github.com/batoulapps/adhan-java) astronomical
  library, with a choice of calculation method (Tunisia 18°/18° by default, MWL, Egyptian, Karachi,
  Umm al-Qura, ISNA) and Shafi'i/Hanafi Asr.
- **Saved locations** (Mahdia, Ariana and Kelibia preloaded). Add one by:
  - tapping **Use my current location** (one-shot, approximate location is enough),
  - **searching a city** by name, or
  - typing latitude, longitude (a decimal comma works too) and an IANA timezone, with suggestions.
- **Exact times per location**: each location can have its own calculation method and minute
  adjustments per prayer (e.g. Fajr +2, Isha −3) to match your mosque or official timetable.
  A live preview shows today's times while you edit.
- **Fajr alarms**: before Fajr, Qiyam al-Layl, or a series around Fajr, using exact alarms that
  survive reboots.
- Next-prayer countdown on the home screen and in an optional persistent notification.
- Polar day/night handling: when the sun doesn't rise or set, times follow the nearest latitude
  where it does (65°).

## Build and run

Requirements: Android Studio (or the Android SDK) and JDK 17. Minimum Android version: 8.0 (API 26).

```bash
./gradlew assembleDebug
```

```bash
./gradlew installDebug
```

## Tests

```bash
./gradlew testDebugUnitTest
```

On-device tests (database migrations) need an emulator or a connected phone:

```bash
./gradlew connectedDebugAndroidTest
```

## Project layout

```
app/src/main/java/com/example/prayeralarm/
  domain/       prayer time calculation, alarm planning, models
  data/         Room database, DataStore preferences, repositories, location lookup
  alarm/        AlarmManager scheduling, ringing service, notifications, boot recovery
  ui/           Jetpack Compose screens (home, locations, alarms, settings)
app/schemas/    exported Room schemas, one JSON file per database version (commit these)
docs/           detailed documentation, start at docs/README.md
```

When you change a Room entity, bump the database version in `PrayerAlarmDatabase`, add a
`Migration`, and extend `PrayerAlarmDatabaseMigrationTest`.

See [docs/README.md](docs/README.md) for architecture, calculation details and design decisions,
and [docs/CHANGELOG.md](docs/CHANGELOG.md) for history.
