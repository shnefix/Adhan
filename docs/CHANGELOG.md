# Changelog

All notable changes, fixes, and milestones across the 10 stages of the Adhan development lifecycle will be recorded here.

---

## [Locations: exact times per city] — 2026-11-03

### Added
- **Per-location minute adjustments** (`PrayerTimeAdjustments`): signed offsets per prayer (±60 min) so a city's times can match its mosque or official timetable. Applied once in `PrayerTimeCalculator`, so the home screen, countdown notification and Fajr alarms agree.
- **Per-location calculation method**: optional override of the Settings method (`SavedLocation.calculationMethod`, NULL = follow Settings).
- **Location form**: "Use my current location" (LocationManager, approximate permission is enough), city search (platform `Geocoder`, no API key), timezone suggestions while typing, the device timezone as default, and a live preview of today's times.
- **Decimal commas** accepted in coordinates (`34,7406`).
- Room schema export (`app/schemas/`) and on-device migration tests (`androidTest/.../PrayerAlarmDatabaseMigrationTest.kt`).

### Changed
- Database version 3: `MIGRATION_2_3` adds five `*_adjustment` columns (default 0) and a nullable `calculation_method` column.
- `kotlin.daemon.jvmargs=-Xmx2048m` in `gradle.properties` (the compiler ran out of memory).

### Fixed
- Prayer times carried random wall-clock milliseconds from the adhan-java library, so identical inputs gave slightly different instants (countdowns/alarms off by up to a minute). Times are now truncated to whole seconds.
- After Isha, the fallback calculation of tomorrow's Fajr ignored the user's calculation method and madhab.
- Polar day/night (no sunrise or sunset) produced 12:00 placeholders. Times now come from the nearest latitude where the sun rises and sets (65°), flagged by `CalculatedTimetable.usedNearestLatitude`.

### Removed
- Committed build logs, dumps and temp files; they are now covered by `.gitignore`.

---

## [Stage 3: Location Management with Room] — 2026-10-29

### Status: Complete & Verified
- **Build Status**: Verified compiling with Room KSP, DataStore, and Unit Tests.
- **Unit Tests**: 11 unit tests passed across validation and repository behavior.
- **Architectural Scope**: Complete implementation of data persistence and location management.

### Added
- **Room SQLite Persistence**:
  - `data/local/entity/LocationEntity.kt`: Room `@Entity(tableName = "saved_locations")` mapping `id`, `name`, `latitude`, `longitude`, and `timeZoneId` with bidirectional mapper extensions to `SavedLocation`.
  - `data/local/dao/LocationDao.kt`: `@Dao` defining reactive `getAllLocations(): Flow<List<LocationEntity>>` queries and coroutine `suspend` CRUD methods.
  - `data/local/PrayerAlarmDatabase.kt`: Room database builder seeding Mahdia, Kelibia, and Ariana on a fresh database, with migration support for existing installations.
- **DataStore Active Location Management**:
  - `data/preferences/UserPreferencesManager.kt`: Key-value persistence using Jetpack DataStore Preferences for decoupled, atomic `selected_location_id` tracking.
- **Repository Abstraction**:
  - `data/repository/LocationRepository.kt`: Repository interface defining business contracts.
  - `data/repository/LocationRepositoryImpl.kt`: Implemented repository combining Room `Flow` and DataStore `Flow` using Kotlin's `combine` operator.
- **Input Validation**:
  - `util/LocationValidator.kt`: Enforces latitude `[-90.0, 90.0]`, longitude `[-180.0, 180.0]`, name lengths, and IANA timezone ID resolution via `java.time.ZoneId`.
- **Location Management Presentation**:
  - `ui/locations/LocationsUiState.kt`: Immutable UI state container for locations, active selection, dialogs, and deletion confirmations.
  - `ui/locations/LocationsViewModel.kt`: ViewModel handling coordinate validation, CRUD operations, active selection, and dialog state.
  - `ui/locations/LocationsScreen.kt`: Material 3 Compose screen with Floating Action Button, LazyColumn of saved locations, and empty state placeholder.
  - `ui/locations/components/LocationItemRow.kt`: Individual location card with active gold badge, radio selection indicator, edit, and delete action triggers.
  - `ui/locations/components/AddEditLocationDialog.kt`: Add/Edit dialog with validation error messages.
  - `ui/locations/components/DeleteConfirmationDialog.kt`: Confirmation prompt preventing accidental deletion.
- **App Service Locator / Dependency Injection**:
  - `PrayerAlarmApplication.kt`: Application class providing lazy singleton instances of database, preferences, and repository.
  - `ui/navigation/MainScreen.kt`: Integrated `LocationsScreen` into the navigation graph and wired ViewModel factories.
  - `ui/home/HomeViewModel.kt`: Updated to dynamically observe the active `SavedLocation` from `LocationRepository` and update the Home header location pill.
- **Unit Tests**:
  - `util/LocationValidatorTest.kt`: Unit tests verifying coordinate limits, IANA timezone parsing, and name boundaries.
  - `data/repository/LocationRepositoryTest.kt`: Unit tests verifying add, update, delete, default fallback, and active selection.
- **Comprehensive Documentation Suite**:
  - Updated `DATA_AND_PERSISTENCE.md`, `ARCHITECTURE.md`, `PROJECT_STRUCTURE.md`, `TESTING_AND_DEBUGGING.md`, and `LEARNING_NOTES.md`.

---

## [Stage 2: Home Screen UI] — 2026-10-29

### Status: Complete & Verified
- **Build Status**: Verified passing (`BUILD SUCCESSFUL in 1m 7s`, 36 tasks executed).
- **Output Artifact**: Generated updated `app-debug.apk` (17.58 MB).

### Added
- **Domain Models**:
  - `domain/model/PrayerType.kt`: Enum defining Fajr, Dhuhr, Asr, Maghrib, Isha with English and Arabic names.
  - `domain/model/SavedLocation.kt`: Domain model for coordinates and timezones.
- **Home Screen Presentation Components**:
  - `ui/home/PrayerScheduleItem.kt`: UI timetable item model with notification toggle status.
  - `ui/home/HomeUiState.kt`: Complete immutable UI state model with sample data disclosure flag.
  - `ui/home/HomeViewModel.kt`: ViewModel exposing `StateFlow<HomeUiState>` with active countdown coroutine loop.
  - `ui/home/components/HomeHeader.kt`: Header with interactive location pill, Gregorian & Hijri dates, and settings shortcut.
  - `ui/home/components/NextPrayerCard.kt`: Hero gradient card displaying next prayer name, Arabic text, exact time badge, and 3-unit live countdown.
  - `ui/home/components/PrayerTimetable.kt`: Daily 5-prayer timetable highlighting upcoming prayer with gold accents, badge, and alert toggles.
  - `ui/home/HomeScreen.kt`: Scrollable container integrating all home components with Compose `@Preview` support.
- **Navigation Architecture**:
  - `ui/navigation/Screen.kt`: Navigation routes for Home, Locations, Alarms, and Settings.
  - `ui/navigation/MainScreen.kt`: `Scaffold` hosting `NavigationBar` and `NavHost` with placeholder screens for future stages.

---

## [Stage 1: Project Initialization] — 2026-10-29

### Status: Complete & Verified
- Initial Gradle setup, AGP 8.7.3, Kotlin 2.0.21, KSP, Material 3 Dark Navy & Gold theme, and `docs/` documentation suite.
# Changelog

## 2026-10-29

- Added Room migration `1 -> 2` for the preferred Mahdia, Kelibia, and Ariana
  default locations.
- Preserved existing saved locations and the DataStore active-location selection
  during migration.
- Fresh databases now select Mahdia and seed the three preferred locations.
- Exact duplicate location inserts are now reused, and deleting the active
  location selects a remaining location or clears the selection when empty.
- Added a deterministic, Android-independent Fajr alarm-plan generator with
  before, at/after, combined, disabled, validation, and midnight coverage.
- Added a permission-aware exact-alarm scheduler, notification channel setup,
  and a manifest-registered prayer alarm receiver.
- Replaced the Alarms placeholder with a live Fajr alarm-plan preview using
  Compose state and the calculated Home timetable.
- Persisted alarm-plan enabled state, count, interval, and timing mode through
  DataStore and moved authoritative UI state into `AlarmPlanViewModel`.
- Added a deterministic `PrayerAlarmCoordinator` that filters past alarms,
  cancels obsolete identities, and delegates desired schedules to a fakeable
  scheduler.
- Connected active-location and persisted alarm-setting flows to scheduling for
  today and tomorrow, with explicit scheduler failure results.
- Added broadcast recovery for boot, date/time/timezone changes, and package
  replacement using the bounded reconciliation horizon.
- Replaced the Settings placeholder with exact-alarm and notification
  permission status and actionable system-settings links.
