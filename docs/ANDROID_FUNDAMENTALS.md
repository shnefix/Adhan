# Android Fundamentals for Adhan

This document breaks down the fundamental Android operating system concepts used in this application. It is tailored for students and engineers learning Android development.

---

## 1. The Android Application Component Model

An Android application is not a monolithic binary with a traditional `main()` function. Instead, it consists of distinct **components** instantiated and managed by the Android operating system:

| Component | Class Type | Role in Adhan |
| :--- | :--- | :--- |
| **Activity** | `MainActivity : ComponentActivity` | The user interface window. Hosts our Jetpack Compose UI. |
| **BroadcastReceiver** | `BroadcastReceiver` (upcoming) | Receives asynchronous system and hardware intents: exact alarm fires, phone reboots (`ACTION_BOOT_COMPLETED`), or timezone changes. |
| **Service** | `Service` (optional/fallback) | Runs background or foreground tasks when user-visible execution is required (such as alarm audio playback). |
| **Application** | `PrayerAlarmApplication : Application` | Global state container created when the app process is created; survives activity changes. |

```
                       ┌─────────────────────────┐
                       │       Android OS        │
                       └────────────┬────────────┘
         ┌──────────────────────────┼──────────────────────────┐
         │ (User taps icon)         │ (Alarm fires / Phone boots)
         ▼                          ▼
┌─────────────────┐       ┌──────────────────────┐
│  MainActivity   │       │ PrayerAlarmReceiver  │
│  (Compose UI)   │       │ (Broadcast Receiver) │
└─────────────────┘       └──────────────────────┘
         ▲                          ▲
         │                          │
         └──────────┬───────────────┘
                    │
        ┌───────────▼────────────┐
        │ PrayerAlarmApplication │  (Global Process Context)
        └────────────────────────┘
```

---

## 2. The Android Manifest (`AndroidManifest.xml`)

### What it is
The manifest is an XML file in `app/src/main/AndroidManifest.xml` that acts as the formal contract between your code and the Android OS. 

### Why we need it
Before Android runs any code in your package, it reads the manifest to know:
- Which permissions the app requires.
- Which activity should open when the user taps the launcher icon (`<category android:name="android.intent.category.LAUNCHER" />`).
- Which receivers or services exist so the OS can invoke them even if the app is currently closed.

### Code in Adhan:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".PrayerAlarmApplication"
        android:label="@string/app_name"
        android:theme="@style/Theme.PrayerAlarm">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## 3. The Android Permission Model

Permissions are security boundaries enforced by the Linux kernel underneath Android.

### Types of Permissions in our App:
1. **Normal Permissions** (Granted automatically at install time):
   - `RECEIVE_BOOT_COMPLETED`: Needed to reschedule alarms when the phone reboots.
   - `VIBRATE`: Needed to vibrate the phone when prayer time arrives or the alarm rings.
2. **Runtime Permissions** (Requires prompt to user on Android 13+ / API 33+):
   - `POST_NOTIFICATIONS`: Must be requested at runtime before posting the persistent countdown or prayer arrival notifications.
3. **Special App Access**:
   - `SCHEDULE_EXACT_ALARM`: Required on Android 12+ (API 31+) to schedule exact wakeups through `AlarmManager`. Starting on Android 13/14, the user or system can revoke this, so our code must check `alarmManager.canScheduleExactAlarms()` before scheduling.

---

## 4. Android Context: What is it?

`Context` is one of the most important—and often misunderstood—classes in Android.
- **What it is**: A handle to the system environment. It provides access to application resources (`strings.xml`, colors), system services (`AlarmManager`, `NotificationManager`), file storage, and database paths.
- **`ApplicationContext` vs `ActivityContext`**:
  - `ActivityContext`: Tied to the visual lifecycle of an Activity. If leaked into a background singleton, it causes a severe memory leak.
  - `ApplicationContext`: Lives as long as the entire process. Safe for long-lived singletons (like our Room Database or Alarm Scheduler).

---

## 5. Background Execution and Android Doze Mode

Starting in Android 6.0 (Marshmallow), Android introduced **Doze Mode** to conserve battery:
- When a phone is stationary, unplugged, and the screen is off, the OS enters deep sleep.
- Network access is disabled, wake locks are ignored, and standard timers (like `Handler`, `Thread.sleep`, or standard inexact `AlarmManager` calls) are deferred to periodic maintenance windows.
- **Why this matters for Adhan**: If we schedule a Fajr alarm at 05:00 using a regular timer, Doze mode might defer it until 05:25 when the user moves the phone!
- **Our Solution**: We use `AlarmManager.setExactAndAllowWhileIdle()`, which instructs the OS power manager to wake up the device even from deep Doze mode to ring the Fajr alarm at the exact minute requested.

---

## 6. Revision Questions

1. Why must `android:exported="true"` be set on `MainActivity`?
2. What happens to scheduled alarms when an Android device is rebooted?
3. Why can't a prayer alarm rely on a simple `Thread.sleep()` or Kotlin coroutine `delay()` running in the background?
4. What is the difference between `ActivityContext` and `ApplicationContext`?

