# Application Architecture & Data Flow

Adhan adheres to official Android application architecture guidelines, implementing a clean, reactive **MVVM (Model-View-ViewModel)** architecture with unidirectional data flow (UDF).

---

## 1. Architectural Layers in Stage 3

```
┌────────────────────────────────────────────────────────────────────────┐
│                               UI LAYER                                 │
│   • MainScreen: Root Scaffold, NavigationBar, NavHost                   │
│   • HomeScreen: Reactive prayer timetable, countdown, location header  │
│   • HomeViewModel: Manages countdown loop & observes active location    │
│   • LocationsScreen: Saved locations list, empty state, Add FAB        │
│   • LocationsViewModel: Validates inputs, handles CRUD & selection     │
│   • Dialogs: AddEditLocationDialog, DeleteConfirmationDialog           │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ StateFlow<UiState>
                                    │ User Actions & Events
┌───────────────────────────────────▼────────────────────────────────────┐
│                             DOMAIN LAYER                               │
│   • Domain Models: SavedLocation, PrayerType, ValidationResult         │
│   • Business Utilities: LocationValidator                              │
│   • [Stage 4: Astronomical Calculation Engine]                         │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                              DATA LAYER                                │
│   • LocationRepository & LocationRepositoryImpl                        │
│   • Room SQLite Database: PrayerAlarmDatabase, LocationDao             │
│   • SQLite Entities: LocationEntity                                    │
│   • DataStore Preferences: UserPreferencesManager (active location ID) │
│   • Manual DI: PrayerAlarmApplication (Service Locator)                │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Unidirectional Data Flow (UDF) in Stage 3

Stage 3 expands the UDF reactive loop from the UI directly down to SQLite disk storage:

```
  ┌────────────────────────────────────────────────────────┐
  │ 1. User taps "Tunis" on LocationsScreen                │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │ 2. LocationsViewModel.selectLocation(location)         │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │ 3. LocationRepository.setActiveLocation(location.id)   │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼
  ┌────────────────────────────────────────────────────────┐
  │ 4. UserPreferencesManager writes to DataStore          │
  └───────────────────────────┬────────────────────────────┘
                              │
                              ▼ (DataStore Flow emits updated ID)
  ┌────────────────────────────────────────────────────────┐
  │ 5. LocationRepositoryImpl combines Room + DataStore    │
  └─────────────┬────────────────────────────┬─────────────┘
                │                            │
                ▼                            ▼
  ┌───────────────────────────┐┌───────────────────────────┐
  │ LocationsViewModel        ││ HomeViewModel             │
  │ updates UI State          ││ updates active location   │
  │ (marks Tunis as ACTIVE)   ││ (header displays "Tunis") │
  └─────────────┬─────────────┘└─────────────┬─────────────┘
                │                            │
                ▼                            ▼
  ┌───────────────────────────┐┌───────────────────────────┐
  │ LocationsScreen           ││ HomeScreen                │
  │ recomposes row indicator  ││ recomposes location pill  │
  └───────────────────────────┘└───────────────────────────┘
```

---

## 3. Navigation Architecture

In `MainScreen.kt`, Jetpack Navigation Compose handles transitions between screens:
- **`Screen.Home`**: Observes `HomeViewModel.uiState`. Clicking the header location pill calls `navController.navigate(Screen.Locations.route)`.
- **`Screen.Locations`**: Observes `LocationsViewModel.uiState`. Provides full CRUD operations for saved locations.
- **`Screen.Alarms`**: Fajr alarms placeholder (scheduled for Stage 7).
- **`Screen.Settings`**: Calculation and notification settings (scheduled for Stage 10).

Both ViewModels receive their dependencies through factory patterns provided by `PrayerAlarmApplication`:
```kotlin
val context = LocalContext.current.applicationContext as PrayerAlarmApplication
val locationRepository = context.locationRepository

val homeViewModel: HomeViewModel = viewModel(
    factory = HomeViewModel.Factory(locationRepository)
)
val locationsViewModel: LocationsViewModel = viewModel(
    factory = LocationsViewModel.Factory(locationRepository)
)
```
