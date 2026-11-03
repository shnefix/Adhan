# Testing and Debugging Guide

Testing strategy, execution procedures, and debugging workflows for Adhan.

---

## 1. Test Levels

| Test Category | Target Layer | Execution Environment | Location |
| :--- | :--- | :--- | :--- |
| **Pure Unit Tests** | Domain validation & Repository logic | Local JVM (Fast execution, no device needed) | `app/src/test/java/` |
| **Room Integration Tests** | SQLite DAO & migrations | Android ART (Emulator or physical device) | `app/src/androidTest/java/` |
| **Compose UI Tests** | Screen rendering & interactions | Android ART with `createComposeRule()` | `app/src/androidTest/java/` |

---

## 2. Unit Tests in Stage 3

### `LocationValidatorTest`
Located at `app/src/test/java/com/example/prayeralarm/util/LocationValidatorTest.kt`.
Verifies:
- Valid location parameters (`36.8065`, `10.1815`, `Africa/Tunis`) return `isValid = true` with zero errors.
- Latitude boundary constraints: ensures values outside `[-90.0, 90.0]` or non-numeric strings fail with explanatory messages.
- Longitude boundary constraints: ensures values outside `[-180.0, 180.0]` fail.
- Timezone validation: verifies IANA IDs via `java.time.ZoneId.getAvailableZoneIds()` and `ZoneId.of()`.
- Name constraints: enforces non-empty and 2-to-50 character boundaries.

### `LocationRepositoryTest`
Located at `app/src/test/java/com/example/prayeralarm/data/repository/LocationRepositoryTest.kt`.
Verifies:
- `addLocation()` inserts new records into the in-memory DAO and emits updated lists over `Flow`.
- `getActiveLocation()` defaults to the first available location when no specific preference is saved.
- `setActiveLocation()` updates the active location selection across observers.
- `deleteLocation()` removes records from the persistent store and triggers UI state emission.

---

## 3. How to Run Unit Tests

### Command Line
```powershell
.\gradlew.bat testDebugUnitTest
```

### In Android Studio
1. Navigate to `app/src/test/java/com/example/prayeralarm/`.
2. Right-click on `LocationValidatorTest` or `LocationRepositoryTest`.
3. Select **Run 'Tests in...'**.

---

## 4. How to Build the App APK

To assemble a fresh debug build for installation on an Android device:
```powershell
.\gradlew.bat assembleDebug
```
Output location:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 5. Step-by-Step Manual Verification Checklist

Follow these steps on your connected Android phone:

1. **Launch App**: Open Adhan.
2. **Observe Home Screen**: The header pill displays **"Mahdia"** (seeded default location).
3. **Navigate to Locations**:
   - Tap the location pill on Home, or tap the **Locations** icon in the bottom navigation bar.
   - Verify Mahdia, Kelibia, and Ariana appear.
   - "Mahdia" has a gold **ACTIVE** badge with a checked radio icon.
4. **Select a Location**:
   - Tap **"Ariana"**.
   - The ACTIVE badge and checked radio instantly move to "Ariana".
   - Tap the **Home** tab in the bottom bar.
   - Verify the Home header now displays **"Ariana"**.
5. **Add a Location**:
   - Return to the Locations tab.
   - Tap the gold **+** Floating Action Button.
   - Test validation: leave fields empty and tap "Add Location" — observe validation error messages under each field.
   - Enter valid details:
     - Name: `Monastir`
     - Latitude: `35.7779`
     - Longitude: `10.8262`
     - Timezone: `Africa/Tunis`
   - Tap "Add Location".
   - Verify "Monastir" appears in alphabetical order in the saved list.
6. **Edit a Location**:
   - Tap the pencil (Edit) icon next to "Monastir".
   - Change the name to `Monastir Port`.
   - Tap "Save Changes".
   - Verify the card updates to "Monastir Port".
7. **Delete with Confirmation**:
   - Tap the trash (Delete) icon next to "Monastir Port".
   - A dialog titled "Delete Location?" appears with Cancel and Delete buttons.
   - Tap "Cancel" — verify "Monastir Port" is NOT deleted.
   - Tap the trash icon again, then tap "Delete".
   - Verify "Monastir Port" is removed from the list.
8. **Verify Persistence Across App Restarts**:
   - Select "Ariana" as active.
   - Force-close the app from the Android App Switcher (swipe it away).
   - Re-launch Adhan.
   - Verify "Ariana" remains selected as the active location on both the Home Screen and the Locations Screen.

## 6. Alarm and recovery checks

1. Open **Alarms**, enable the plan, and verify the default five-alarm,
   ten-minute preview is based on the displayed Fajr time.
2. Change count, interval, and timing mode, close the app, and reopen it.
   Verify the settings remain unchanged.
3. Open **Settings** and verify exact-alarm and notification permission status.
4. With permission available, enable the plan and verify the scheduler is
   allowed to schedule future alarms. With permission denied, verify the
   app reports the limitation rather than claiming success.
5. After changing the active location or disabling the plan, verify obsolete
   alarms are canceled where the device exposes them.
6. Reboot the phone and verify the current/tomorrow bounded schedule is
   reconstructed. Repeat after changing the system timezone or date.

The repository currently has no Compose UI test suite and no connected-device
results. These checks must be performed on a physical or emulated Android
device before claiming end-to-end alarm delivery.
