# Data and Persistence: Room & DataStore

Prayer Alarm is designed to work 100% offline without cloud accounts (location lookup and city search are optional conveniences). This makes robust local device persistence essential.

---

## 1. Storage Architecture Overview

The application utilizes two distinct persistence mechanisms:
1. **Room Database (SQLite)**: For structured, relational, queryable data (saved locations with coordinates, and upcoming custom alarm plans).
2. **DataStore Preferences**: For lightweight key-value application settings (active location ID selection, persistent notification toggle, prayer notification flags).

```
                      ┌─────────────────────────────────┐
                      │        Data Layer Storage       │
                      └───────────────┬─────────────────┘
                                      │
            ┌─────────────────────────┴─────────────────────────┐
            ▼                                                   ▼
┌─────────────────────────────────┐                 ┌───────────────────────┐
│     PrayerAlarmDatabase (Room)  │                 │ UserPreferencesManager│
│            (SQLite)             │                 │ (DataStore Preferences│
├─────────────────────────────────┤                 ├───────────────────────┤
│ • LocationEntity (@Entity)      │                 │ • selected_location_id│
│ • LocationDao (@Dao CRUD/Flow)  │                 │ • (Stage 6+ settings) │
└────────────────┬────────────────┘                 └───────────┬───────────┘
                 │                                              │
                 └──────────────────────┬───────────────────────┘
                                        ▼
                      ┌─────────────────────────────────┐
                      │    LocationRepositoryImpl       │
                      │  (Combines Room Flow + DataStore│
                      │   into Domain SavedLocation)    │
                      └─────────────────────────────────┘
```

---

## 2. Room SQLite Architecture

### Core Concepts Explained

#### SQLite
SQLite is an embedded, serverless, self-contained SQL database engine compiled directly into the Android operating system. Unlike client-server databases (e.g., PostgreSQL, MySQL), SQLite reads and writes directly to regular disk files stored in the app's private sandbox (`/data/data/com.example.prayeralarm/databases/`).

#### Room Persistence Library
Room is Google's official object-mapping abstraction layer over SQLite. It provides:
- Compile-time verification of raw SQL queries (using KSP).
- Elimination of boilerplate `Cursor` and `ContentValues` code.
- Native integration with Kotlin coroutines (`suspend`) and asynchronous streams (`Flow`).

#### Primary Components:

1. **Entity (`@Entity`)**:
   Represents a database table. In our app: `com.example.prayeralarm.data.local.entity.LocationEntity`:
   ```kotlin
   @Entity(tableName = "saved_locations")
   data class LocationEntity(
       @PrimaryKey(autoGenerate = true)
       val id: Long = 0L,
       @ColumnInfo(name = "name")
       val name: String,
       @ColumnInfo(name = "latitude")
       val latitude: Double,
       @ColumnInfo(name = "longitude")
       val longitude: Double,
       @ColumnInfo(name = "timezone_id")
       val timeZoneId: String
   )
   ```

2. **Primary Key (`@PrimaryKey(autoGenerate = true)`)**:
   A column or set of columns uniquely identifying each row in the table. Setting `autoGenerate = true` allows SQLite to assign an incrementing 64-bit integer automatically.

3. **Data Access Object (`@Dao`)**:
   The interface defining data interaction contracts (`com.example.prayeralarm.data.local.dao.LocationDao`). Room generates the concrete implementation at compile time:
   - **`suspend` functions**: Used for one-shot insert, update, and delete mutations. Executed off the main thread.
   - **`Flow<T>` queries**: Used for observable reads. Emits an updated list whenever the underlying table records are modified.

4. **Database (`@Database`)**:
   Abstract class extending `RoomDatabase` (`com.example.prayeralarm.data.local.PrayerAlarmDatabase`). Annotations define entity lists and versioning. Also handles database pre-population using `RoomDatabase.Callback`.

---

## 3. DataStore Preferences for Active Location Selection

### Why Not Store Active Selection in the Room Location Row?
If we stored an `is_active` boolean column directly in the `saved_locations` SQLite table:
1. Selecting a new active location would require a two-step transactional write: setting `is_active = 0` for all rows, then `is_active = 1` for the target row.
2. It mixes persistent city catalog metadata with dynamic user application state.
3. Decoupling the active selection to **Jetpack DataStore Preferences** (`UserPreferencesManager`) allows atomic updates via a single key (`selected_location_id: Long`), ensuring instant response and zero table locking.

---

## 4. The Repository Pattern

The `LocationRepository` interface acts as the single source of truth for the rest of the application:
```kotlin
interface LocationRepository {
    fun getAllLocations(): Flow<List<SavedLocation>>
    fun getActiveLocation(): Flow<SavedLocation?>
    suspend fun getLocationById(id: Long): SavedLocation?
    suspend fun addLocation(location: SavedLocation): Long
    suspend fun updateLocation(location: SavedLocation): Boolean
    suspend fun deleteLocation(location: SavedLocation): Boolean
    suspend fun setActiveLocation(locationId: Long)
}
```

In `LocationRepositoryImpl`, `combine` is used to marry Room's `Flow<List<LocationEntity>>` and DataStore's `Flow<Long?>`:
```kotlin
override fun getActiveLocation(): Flow<SavedLocation?> {
    return combine(
        locationDao.getAllLocations(),
        preferencesManager.selectedLocationId
    ) { entities, selectedId ->
        if (entities.isEmpty()) null
        else {
            val active = entities.find { it.id == selectedId } ?: entities.first()
            active.toDomain(isSelected = true)
        }
    }
}
```

---

## 5. Dependency Injection & Service Locator

Rather than introducing heavy external DI frameworks like Hilt or Dagger in early stages, the application utilizes clean, native **Manual Dependency Injection / Service Locator** inside `PrayerAlarmApplication`:
```kotlin
class PrayerAlarmApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { PrayerAlarmDatabase.getInstance(this, applicationScope) }
    val preferencesManager by lazy { UserPreferencesManager(this) }
    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(database.locationDao(), preferencesManager)
    }
}
```
ViewModels are instantiated via custom `ViewModelProvider.Factory` implementations passed to `viewModel(factory = ...)`.

---

## 6. Revision Questions

1. Why must all Room database mutations be marked as `suspend` or executed on background dispatchers?
2. How does Room's `Flow<List<T>>` know when to re-query SQLite and emit a new value?
3. What is the difference between `autoGenerate = true` and manually providing primary keys?
4. Why is DataStore Preferences preferred over storing the `is_active` flag in SQLite?

## 7. Default locations and migration

Fresh installations seed three benchmark locations: Mahdia, Kelibia, and Ariana.
Mahdia is selected only when the database starts empty. Existing user locations
and the existing DataStore selection are preserved.

Room database version 2 includes an explicit `MIGRATION_1_2`. It inserts each
preferred location only when the exact name, coordinates, and IANA timezone are
not already present. The migration does not delete rows and does not overwrite
`selected_location_id`, so upgrading an existing installation cannot silently
change the user's active city.

The migration uses `INSERT ... SELECT ... WHERE NOT EXISTS` because the original
schema has no uniqueness constraint for location identity. Future schema changes
should consider a deliberate identity constraint or a dedicated stable seed key.

The repository also checks this identity before inserting a user location. If an
exact duplicate is submitted, the existing row is reused. Deleting the active
location selects the first remaining saved location, or clears the DataStore
selection when the database becomes empty. This keeps the Home screen's active
location flow valid after destructive actions.

Alarm-plan preferences are stored in the same DataStore file using stable keys
for enabled state, count, interval, and timing mode. The alarm ViewModel owns
the authoritative `StateFlow<AlarmPlanConfig>` and persists only positive
count/interval values. The Compose screen renders that flow and sends actions
back to the ViewModel rather than keeping the durable settings in `remember`
state.
