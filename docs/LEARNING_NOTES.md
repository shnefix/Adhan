# Learning Notes & Common Android Pitfalls

Practical notes, core concept deep dives, and architectural tips encountered while building Adhan.

---

## 1. Concepts Encountered in Stage 3

### Room Database & SQLite
- **What**: Room is an abstraction over SQLite that verifies SQL syntax at compile-time via KSP.
- **Key Idea**: Database operations must be offloaded from the Android main thread (UI thread). Room enforces this by making DAO methods either `suspend` functions (for coroutines) or return reactive streams like `Flow<T>`.
- **Under the hood**: When a `@Query` returns `Flow<List<T>>`, Room tracks table invalidation. Whenever any thread writes to `saved_locations`, Room's SQLite invalidation tracker signals the Flow to run the query again on background threads and emit the fresh list to observers.

### Kotlin Flow & `combine` Operator
- **What**: A cold asynchronous data stream that produces values sequentially.
- **Why we use `combine`**:
  Our UI needs to know both the list of all locations (from Room) and which location ID is currently selected (from DataStore). Instead of storing this coupling in a database column, `combine(roomFlow, dataStoreFlow) { locations, selectedId -> ... }` merges two independent streams into a single consolidated domain model stream.

### Unidirectional Data Flow (UDF) & Compose State
- In Jetpack Compose, UI components do not store their own source-of-truth state. Instead, they receive state from ViewModel StateFlows and emit events upward.
- `collectAsState()` safely converts Kotlin `StateFlow` into Compose `State<T>`, triggering recomposition only when the emitted state reference changes.

### Jetpack DataStore Preferences
- Replaces legacy `SharedPreferences`.
- Provides transactional updates, avoids UI blocking (zero ANR risk), and natively emits asynchronous changes via Kotlin Coroutines `Flow`.

### Manual Dependency Injection / Service Locator
- Centralized in `PrayerAlarmApplication`.
- `by lazy` guarantees single-instance instantiation (singleton) upon first access without complex framework configuration.

---

## 2. Common Pitfalls & How to Avoid Them

| Pitfall | Why It Causes Bugs | Our Solution |
| :--- | :--- | :--- |
| **Executing Room queries on Main Thread** | Causes Android ANR (Application Not Responding) crash. | Used `suspend` functions and Room `Flow` dispatching on `Dispatchers.IO`. |
| **Storing UI selection state in SQLite tables** | Mixes transient app selection with catalog records, requiring table-wide updates. | Delegated active selection ID to Jetpack DataStore Preferences. |
| **Accepting raw unvalidated coordinates** | Can crash prayer calculations or provide invalid astronomical angles (e.g. lat > 90°). | Enforced `LocationValidator` verifying coordinates within `[-90, 90]` and `[-180, 180]`. |
| **Deleting records without confirmation** | High risk of accidental user data loss. | Added `DeleteConfirmationDialog` before executing DAO delete operations. |

---

## 3. Self-Test Quiz

1. **Why does Room reject executing queries on `Dispatchers.Main` by default?**
   - *Answer*: Disk I/O speed varies and can take tens or hundreds of milliseconds. Blocking the main thread for more than 16ms causes dropped frames (jank), and blocking for 5 seconds triggers an Android ANR dialog.

2. **What is the purpose of `exportSchema = false` in `@Database`?**
   - *Answer*: Tells Room not to export database schema JSON files during build. In production apps with database migrations across versions, schema exporting is enabled to track version history.

3. **How does `combine` behave when one of the input Flows hasn't emitted a value yet?**
   - *Answer*: `combine` will wait until every input Flow has emitted at least one value before calculating and emitting its first combined result.

---

## 4. Practical Hands-On Exercises

1. **Exercise 1 (Timezone exploration)**: Try entering an invalid timezone ID like `"Mars/Olympus"` in the Add Location dialog and verify the validation error catches it.
2. **Exercise 2 (Reactive selection)**: With both Home and Locations tabs available, add a new city, select it, and notice how quickly the Home header updates via the reactive Flow pipeline.
