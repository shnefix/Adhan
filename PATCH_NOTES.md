# Fajr alarm redesign patch

Copy the contents of this folder over your project root (same relative paths).

Also edit PrayerAlarmApplication.onCreate: inside the existing applicationScope.launch { ... } add
    LegacyAlarms.cancel(this@PrayerAlarmApplication)
and import com.example.prayeralarm.alarm.LegacyAlarms.

Requires increment 1 (PrayerCalculationSettings, calculationSettings flow). Update the
PrayerAlarmSchedulingViewModel.Factory call in MainScreen to pass:
(locationRepository, alarmPreferences, scheduler, preferencesManager.calculationSettings, clock)

Delete/rewrite app/src/test/.../AlarmPlanViewModelTest.kt (uses removed count/interval API).
