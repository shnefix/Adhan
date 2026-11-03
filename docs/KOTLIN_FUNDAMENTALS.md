# Kotlin Fundamentals in Adhan

This document outlines key Kotlin language patterns and idioms used throughout the Adhan codebase, accompanied by actual examples and step-by-step explanations.

---

## 1. Null Safety (`?`, `?.`, `?:`, `!!`)

### What it is
In Kotlin, the type system distinguishes between references that can hold `null` and those that cannot. This prevents `NullPointerException` (the billion-dollar mistake in software engineering).

### How it works
- Non-nullable type: `val name: String = "Tunis"` (`name = null` causes a compile error).
- Nullable type: `val nextPrayer: PrayerTime? = null`.
- Safe call: `val time = nextPrayer?.time` (evaluates to null if `nextPrayer` is null, without throwing an exception).
- Elvis operator: `val displayName = location.name ?: "Unknown Location"`.

### Where it appears in Adhan:
When calculating the next prayer, if all prayers for the current day have passed, the search returns a nullable result until the next day's Fajr is computed.

---

## 2. Data Classes (`data class`)

### What it is
A concise class whose main purpose is holding immutable data.

### Why we need it
Kotlin automatically generates:
- `equals()` and `hashCode()` (structural equality comparison).
- `toString()` (easy debugging).
- `copy()` (creating updated copies without mutating original instances).
- Component functions for destructuring declarations.

### Example in our project (`domain/model/SavedLocation.kt` upcoming):
```kotlin
data class SavedLocation(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
    val isDefault: Boolean = false
)
```

### Why immutability matters:
In Jetpack Compose, state should be immutable. When state changes, we emit a *new copy* (e.g., `location.copy(isDefault = true)`), which triggers Compose recomposition accurately.

---

## 3. Kotlin Coroutines & Asynchronous Flow

### What they are
Coroutines are lightweight threads that allow writing non-blocking asynchronous code in a sequential, readable style.

### Flow (`StateFlow` & `SharedFlow`)
A reactive asynchronous data stream that emits multiple values sequentially over time.

### How it works in MVVM:
```
┌─────────────────────────┐
│     Room Database       │  Emits Flow<List<SavedLocation>> when DB changes
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│    LocationRepository   │  Maps Entities to Domain Models
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│      HomeViewModel      │  Converts to StateFlow<HomeUiState> via .stateIn()
└────────────┬────────────┘
             │
┌────────────▼────────────┐
│      HomeScreen UI      │  Observes state via .collectAsStateWithLifecycle()
└─────────────────────────┘
```

---

## 4. Extension Functions

### What they are
Kotlin allows you to add new functions to an existing class without having to inherit from it or modify its source code.

### Example in Adhan (Formatting dates and durations):
```kotlin
fun Duration.toCountdownString(): String {
    val totalSeconds = this.seconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}
```
Now, any `Duration` instance can call `.toCountdownString()` directly!

---

## 5. Sealed Interfaces & Sealed Classes

### What they are
A restricted class hierarchy where all subclasses must be known at compile time.

### Why we need it for UI State:
It allows exhaustive `when` expressions without needing an unpredictable `else` branch.
```kotlin
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val location: SavedLocation,
        val prayerTimes: List<PrayerDisplayItem>,
        val nextPrayer: PrayerDisplayItem,
        val remainingDuration: Duration
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
```
In the UI composable:
```kotlin
when (uiState) {
    is HomeUiState.Loading -> LoadingSpinner()
    is HomeUiState.Success -> PrayerTimetableContent(uiState)
    is HomeUiState.Error -> ErrorBanner(uiState.message)
} // Compiler ensures all cases are handled!
```

---

## 6. Revision Questions

1. Why are data classes preferred over standard classes for holding state in Compose?
2. What happens if a new subclass is added to a `sealed interface`, but a `when` expression doesn't handle it?
3. What is the advantage of using `StateFlow` over a mutable callback interface?

