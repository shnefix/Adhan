# Technology Stack & Selection Rationale

Every technology and library selected for the Adhan project was chosen based on stability, battery impact, modern Android engineering practices, and adherence to Android platform limits.

---

## 1. Core Languages & Platform Tools

| Tool / Component | Version in Project | Purpose | Technical Selection Rationale |
| :--- | :---: | :--- | :--- |
| **Kotlin** | `2.0.21` | Primary language | First-class Android language offering type safety, null safety, coroutines, and seamless Jetpack Compose integration. |
| **Java JVM Target** | `17` | Compilation target | Modern standard LTS Java version supported by Android Studio, Android Gradle Plugin 8.7+, and AGP build tasks. |
| **Android Compile SDK** | `35` (Android 15) | Compilation API surface | Enables the app to target the latest Android platform behaviors, permission rules, and edge-to-edge system standards. |
| **Android Min SDK** | `26` (Android 8.0) | Minimum supported OS | Covers >96% of active Android devices globally while providing native support for modern `java.time` APIs without cumbersome backport desugaring overhead. |
| **Android Target SDK** | `35` (Android 15) | Runtime behavior target | Ensures the app complies with modern privacy standards, background execution policies, and foreground restrictions. |

---

## 2. Build System & Plugins

| Plugin / Tool | Version | Where Declared | Rationale |
| :--- | :---: | :--- | :--- |
| **Gradle** | `8.11.1` | `gradle-wrapper.properties` | Build engine orchestrating compilation, packaging, and dependency resolution. |
| **Android Gradle Plugin (AGP)** | `8.7.3` | `gradle/libs.versions.toml` | Official Google plugin that understands Android resources, AAPT2, DEX compilation, and APK packaging. |
| **Kotlin Android Plugin** | `2.0.21` | `gradle/libs.versions.toml` | Compiles Kotlin source files into JVM bytecode. |
| **Kotlin Compose Compiler Plugin** | `2.0.21` | `gradle/libs.versions.toml` | Starting from Kotlin 2.0, Compose compiler is bundled directly into Kotlin, eliminating version mismatches. |
| **KSP (Kotlin Symbol Processing)** | `2.0.21-1.0.28` | `gradle/libs.versions.toml` | High-speed annotation processing for Room database generation, up to 2x faster than legacy `kapt`. |

---

## 3. UI & Design System

| Library | Version / BOM | Role | Rationale |
| :--- | :---: | :--- | :--- |
| **Jetpack Compose BOM** | `2024.11.00` | Dependency Bill-of-Materials | Guarantees that all Compose libraries (UI, Material 3, Animation, Foundation) are strictly mutually compatible. |
| **Material 3 (`androidx.compose.material3`)** | via BOM | UI Component system | Implements modern Material Design 3 (dynamic color support, rounded surfaces, accessible typography). |
| **Material Icons Extended** | `1.7.5` | Iconography | Provides crisp vectors for prayer times (sun, moon, sunset, alarm bells, settings, location). |
| **Navigation Compose** | `2.8.4` | In-app navigation | Standard declarative navigation system for moving between Home, Locations, Alarms, and Settings screens. |

---

## 4. Architecture, Async & Persistence

| Library | Version | Role | Rationale |
| :--- | :---: | :--- | :--- |
| **Lifecycle & ViewModel Compose** | `2.8.7` | UI State Management | Survives configuration changes (e.g. screen rotations) and manages asynchronous data streams safely using `viewModelScope`. |
| **Room Database** | `2.6.1` | Local SQLite ORM | Compile-time checked SQL queries, reactive SQLite observation with Kotlin `Flow`, and zero runtime reflection overhead. |
| **DataStore Preferences** | `1.1.1` | Key-value settings | Modern, asynchronous, type-safe replacement for legacy `SharedPreferences`, avoiding main-thread disk I/O freezes. |
| **Kotlinx Coroutines** | `1.9.0` | Asynchronous programming | Lightweight concurrency for database queries, timers, and background dispatchers (`Dispatchers.IO`, `Dispatchers.Default`). |

---

## 5. System Android APIs (No external dependencies)

| Android System Service | Purpose in Adhan | Why Not a 3rd-party Library? |
| :--- | :--- | :--- |
| **`AlarmManager`** | Exact wake-up alarms (`setExactAndAllowWhileIdle`) | Hardware-level RTC alarms wake the CPU from deep sleep. WorkManager is unsuitable because it is deferred and inexact. |
| **`BroadcastReceiver`** | Receiving alarm triggers & boot events | Android OS communicates system events (`ACTION_BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`) via broadcasts. |
| **`NotificationManager`** | Channels and status bar alerts | Official Android system API; allows users to fine-tune sound, vibration, and priority per channel. |
| **`java.time` (JSR-310)** | Date, time, timezones, durations | Built into Java 8+ / Android API 26+; timezone-aware, immutable, avoids legacy `java.util.Date` and `Calendar` bugs. |

---

## 6. What Was Explicitly Excluded & Why

1. **GPS / Location Services (`FusedLocationProviderClient`)**:
   - *Reason*: Excessive battery drain and unnecessary location privacy invasion. Prayer times depend only on the city's coordinates, which remain fixed.
2. **Third-party Backend / Cloud DB (Firebase, Supabase)**:
   - *Reason*: Violates user privacy, introduces network latency, and requires ongoing hosting fees. Prayer times can be computed 100% locally.
3. **WorkManager for Alarms**:
   - *Reason*: Android WorkManager is designed for deferrable background jobs (syncing, uploads) and can drift by 10 to 30 minutes in Doze mode. Fajr requires exact minute precision.

