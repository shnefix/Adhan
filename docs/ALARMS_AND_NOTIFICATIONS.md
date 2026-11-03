# Alarms and Notifications: Native Android Execution

The core value proposition of Adhan is its deterministic waking alarm around Fajr and its persistent next-prayer countdown. On modern Android, background reliability requires strict adherence to system constraints.

---

## 1. The Alarm System Architecture

```
                               ┌────────────────────────┐
                               │   AlarmPlanGenerator   │
                               │  (Computes Schedules)  │
                               └───────────┬────────────┘
                                           │ List<PlannedAlarm>
                                           ▼
                               ┌────────────────────────┐
                               │   AlarmScheduler API   │
                               └───────────┬────────────┘
                                           │
                ┌──────────────────────────┴──────────────────────────┐
                ▼                                                     ▼
┌───────────────────────────────┐                     ┌───────────────────────────────┐
│     NativeAlarmScheduler      │                     │   SystemClockAlarmScheduler   │
│  (Hardware RTC Exact Alarms)  │                     │   (AlarmClock.ACTION_SET)     │
└───────────────┬───────────────┘                     └───────────────────────────────┘
                │ AlarmManager.setExactAndAllowWhileIdle
                ▼
┌───────────────────────────────┐
│      Android AlarmManager     │
└───────────────┬───────────────┘
                │ Triggers PendingIntent
                ▼
┌───────────────────────────────┐
│      PrayerAlarmReceiver      │
└───────────────┬───────────────┘
                │ Displays
                ▼
┌───────────────────────────────┐
│   Alarm Ringing UI / Audio    │
└───────────────────────────────┘
```

---

## 2. Why `AlarmManager` with `setExactAndAllowWhileIdle()`?

Android offers several scheduling mechanisms:
- `WorkManager`: Designed for deferrable background jobs (syncing files, sending logs). Batched by Doze Mode; can be delayed by 10 to 45 minutes. **Unusable for prayer alarms.**
- `AlarmManager.setInexactRepeating()`: Batched by OS to conserve battery. Times can drift significantly.
- **`AlarmManager.setExactAndAllowWhileIdle()`**: Specifically intended for alarms and calendar events that must trigger at a precise millisecond, even if the device is in deep Doze mode.

---

## 3. Permissions for Exact Alarms (Android 12+)

- **`android.permission.SCHEDULE_EXACT_ALARM`**: Declared in `AndroidManifest.xml`.
- Starting on Android 13/14, users or battery management tools can revoke exact alarm access in App Info.
- **Defensive API Check**:
  ```kotlin
  val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (!alarmManager.canScheduleExactAlarms()) {
          // Direct user to system settings to re-enable permission
      }
  }
  ```

---

## 4. Boot Recovery (`RECEIVE_BOOT_COMPLETED`)

When an Android phone turns off or restarts, **all scheduled alarms in `AlarmManager` are cleared by the Linux kernel.**

To ensure the user never misses Fajr after a reboot:
1. We declare `RECEIVE_BOOT_COMPLETED` permission.
2. We register `BootReceiver : BroadcastReceiver` in the manifest.
3. When the device finishes booting, the OS broadcasts `android.intent.action.BOOT_COMPLETED`.
4. Our `BootReceiver` runs, reads active alarm plans from the Room database, recomputes the next Fajr, and reschedules the exact hardware alarms immediately.

---

## 5. Notification Channels (Android 8.0+ / API 26+)

All notifications on modern Android must belong to a channel:
1. **`prayer_countdown_channel`**: Low importance, silent, no vibration. Displays the ongoing countdown to the next prayer.
2. **`prayer_arrival_channel`**: High importance, sound and vibration enabled. Notifies the user when a prayer time arrives.
3. **`fajr_alarm_channel`**: Max importance, loud audio, bypasses DND where permitted.

---

## 6. Persistent countdown lifecycle

The next-prayer notification is maintained by
`CountdownNotificationService`, a foreground service declared with
`stopWithTask="false"`. The service recalculates the target prayer from the
selected location's timezone and the current system `Instant` every second; it
does not decrement a counter owned by a screen or `ViewModel`. This keeps the
notification current when the activity is recreated, the screen is locked, or
the activity task is removed from Recents. The stable notification ID and
existing countdown channel are reused, so updates replace rather than
duplicate the notification.

The collapsed and expanded notification use the platform text layout with the
prayer name as the title and a compact `countdown    scheduled time` content
line. Android may truncate or reflow that line depending on the device's
system notification template. The service still requires a visible foreground
notification and `POST_NOTIFICATIONS` where applicable; force-stop and
manufacturer-specific battery restrictions remain platform limitations.

## 7. Revision Questions

1. Why does `AlarmManager` lose all scheduled alarms when a device reboots?
2. What is the difference between `WorkManager` and `AlarmManager`?
3. What happens if an app attempts to post a notification on Android 13 without requesting `POST_NOTIFICATIONS`?

## Alarm plan generation

The pure `AlarmPlanGenerator` creates a deterministic list of `LocalDateTime`
values from a Fajr timestamp and an `AlarmPlanConfig`. The alarm count is the
total number of alarms, including Fajr when the generated schedule reaches it.

- `BEFORE_FAJR`: alarms are spaced backwards and the final alarm is at Fajr.
- `AT_OR_AFTER_FAJR`: the first alarm is at Fajr and later alarms are after it.
- `BOTH`: currently means the combined pre-Fajr sequence ending at Fajr. This
  matches the product example of five alarms at 04:20, 04:30, 04:40, 04:50,
  and 05:00 for a 05:00 Fajr. It does not silently create more than the
  configured count.

The generator is independent of Android scheduling APIs, so invalid counts,
intervals, disabled plans, duplicate timestamps, and midnight date changes can
be tested using fixed values before any `AlarmManager` integration is added.

## Native scheduling boundary

`AndroidAlarmScheduler` is the Android boundary for a tested alarm plan. Each
request carries an explicit non-negative `requestCode`, trigger timestamp,
prayer name, and location name. The request code is used as the
`PendingIntent` identity so different alarms do not overwrite each other.
Scheduling uses `setExactAndAllowWhileIdle` only when Android reports exact
alarm access; otherwise it returns `ExactAlarmPermissionRequired` for the UI to
explain and remediate.

`PrayerAlarmReceiver` posts a high-importance notification and exits when
`POST_NOTIFICATIONS` is denied. Notification channels are created from the
Application and defensively by the receiver. This checkpoint does not yet
persist alarm plans, expose alarm settings, reconcile obsolete schedules, or
reschedule after reboot; those require the settings and persistence layers.

The Alarms destination now exposes a Compose preview backed by the live Fajr
time from `HomeUiState`. It validates count and interval through the same pure
generator used by scheduling.

Alarm settings are now persisted through `UserPreferencesManager`. Defaults are
five alarms, ten-minute intervals, combined pre-Fajr mode, and disabled plan.
`AlarmPlanViewModel` exposes the settings as a `StateFlow`, reports DataStore
errors to the UI, and rejects non-positive count or interval values before
writing. The current checkpoint persists settings but does not yet reconcile
them with `AlarmManager`.

`PrayerAlarmCoordinator` is the reconciliation boundary for the next
integration step. It converts one or more timezone-aware Fajr timestamps into
future `ScheduledPrayerAlarm` values, cancels obsolete request codes, and
delegates scheduling to a fakeable `PrayerAlarmScheduler`. The current
coordinator is covered independently and is now observed by an application
ViewModel. Changes to the active location or persisted alarm settings trigger
reconciliation for today and tomorrow. The in-memory identity list is bounded
to that rolling horizon; durable identity storage remains future work.

`PrayerAlarmRecoveryReceiver` invokes the same bounded today/tomorrow
reconciliation after boot, date changes, manual time changes, timezone changes,
and package replacement. It uses `goAsync()` so Room/DataStore reads and
astronomical calculation run off the receiver callback. Recovery reconstructs
the bounded request-code set so a disabled plan can cancel alarms from the
previous horizon. Manufacturer battery restrictions and physical delivery
still require device verification.

The Settings destination exposes exact-alarm and notification permission status
with links to the relevant Android system settings pages. It does not claim
that a permission grant guarantees delivery on every manufacturer image.
