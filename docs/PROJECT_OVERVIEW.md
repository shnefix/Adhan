# Project Overview: Adhan

## 1. High-Level Vision

**Adhan** is a native Android application engineered to help Muslims punctually observe the five daily prayers (Fajr, Dhuhr, Asr, Maghrib, Isha) with a special focus on customizable waking alarms for **Fajr**.

The project is built on three core pillars:
1. **Mathematical Autonomy**: All prayer times are computed on-device using astronomical algorithms and documented configurable assumptions. No background location, third-party backend servers, or paid APIs are used; location access is optional and one-shot (see Decisions §1).
2. **Deterministic Wake-up System**: The project contains a permission-aware exact-alarm boundary using Android's `AlarmManager`; persistence, reconciliation, reboot recovery, and device-specific delivery behavior are not yet complete.
3. **Transparent & Maintainable Architecture**: Built in Kotlin with Jetpack Compose, Material 3, and MVVM principles, serving as both an everyday utility and a reference educational codebase for modern Android engineering.

---

## 2. Core Requirements & Features

### A. Prayer Times Engine
- Computes daily timings for Fajr, Dhuhr, Asr, Maghrib, and Isha.
- Uses the current documented assumptions of 18° Fajr/Isha and standard-shadow Asr. Official Tunisian timetable equivalence has not yet been independently verified.
- Calculates the upcoming next prayer and live countdown down to the second while active.

### B. Location Management
- Seeds Mahdia, Kelibia, and Ariana on a fresh database, with capability to add, edit, select, or delete custom locations.
- Works entirely offline using coordinates (Latitude/Longitude) and standard IANA Timezone IDs (e.g., `Africa/Tunis`).
- No background location: the permission is only asked for when the user taps "Use my current location".

### C. Configurable Fajr Alarm System
- Multi-alarm configuration: supports setting any number of sequential alarms (not hardcoded to 1 or 2).
- Three operational timing modes:
  - **Mode A (Before Fajr)**: e.g., 3 alarms spaced by 10 minutes finishing before Fajr (for Qiyam / Tahajjud / Suhur).
  - **Mode B (At & After Fajr)**: First alarm at Fajr, followed by reminders after.
  - **Mode C (Combined)**: Alarms before, at, and after Fajr.
- Configurable intervals (in minutes).
- Previews the calculated alarm times before activation.

### D. System Clock & Native Fallback Scheduling
- Integrates with the phone's native Clock app (`AlarmClock.ACTION_SET_ALARM`) when appropriate.
- Robust native fallback using `AlarmManager` with `SCHEDULE_EXACT_ALARM`, `PendingIntent`, and foreground ringing to guarantee wake-up even if the device's clock app rejects automated schedule creation.

### E. Notifications & Battery-Conscious Background Work
- Low-power persistent-style notification indicating the next prayer and remaining duration.
- Optional timely alert notifications when prayer times arrive.
- Automatic recalculation on date change, location switch, time changes, and device reboot (`BOOT_COMPLETED`).

---

## 3. Operating Constraints & Privacy

```
                                  ┌──────────────────────────────┐
                                  │      PRAYER ALARM APP        │
                                  │ (100% Offline & Independent) │
                                  └──────────────┬───────────────┘
                                                 │
                  ┌──────────────────────────────┼──────────────────────────────┐
                  ▼                              ▼                              ▼
        [ NO GPS PERMISSION ]         [ NO BACKEND SERVER ]           [ NO PAID APIS ]
        Manual coordinate entry        All calculations are         No network dependency
        and pre-loaded cities          performed locally on-device  for core calculations
```

- **Zero Tracking**: No analytics, telemetry, or user identifiers.
- **Local Persistence Only**: All data is stored in a private SQLite database via Room and DataStore on the device.

---

## 4. Engineering Roadmap (10 Stages)

| Stage | Phase | Status |
| :---: | :--- | :---: |
| **Stage 1** | **Project Initialization & Core Setup** (Gradle, KSP, Compose theme, Base Activity) | **Current Stage** |
| **Stage 2** | **Home Screen UI** (Prayer timetable, Next prayer card, Live countdown mockup) | Pending Stage 1 verification |
| **Stage 3** | **Location Management** (Room database, Entities, DAOs, Location repository & UI) | Planned |
| **Stage 4** | **Tunisian Prayer Calculation Engine** (Astronomical calculations, unit tests, validation) | Planned |
| **Stage 5** | **Countdown & Next Prayer Logic** (Real-time updates, date transitions, state binding) | Planned |
| **Stage 6** | **Notifications System** (Channels, permissions, next-prayer banner, prayer arrival alerts) | Planned |
| **Stage 7** | **Alarm Planning Engine** (Validation, scheduling modes, multi-alarm preview) | Planned |
| **Stage 8** | **Native AlarmManager Scheduler** (Exact alarms, PendingIntents, BootReceiver, Ringing UI) | Planned |
| **Stage 9** | **System Clock App Integration** (Intent handling, fallback mechanism, honest reporting) | Planned |
| **Stage 10** | **Reliability, Edge Cases & Polishing** (Timezone shifts, testing on real devices, release build) | Planned |
