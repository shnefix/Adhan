package com.example.prayeralarm.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.prayeralarm.data.local.entity.toDomain
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.model.PrayerTimeAdjustments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrades a database written by an older app version and opens it with the current Room
 * schema. Room validates every table and column on open, so a migration that leaves the
 * schema different from the entities fails here instead of crashing users on update.
 *
 * Versions 1 and 2 predate schema export, so their table is recreated from the SQL Room
 * generated for them.
 */
@RunWith(AndroidJUnit4::class)
class PrayerAlarmDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    private fun createLegacyDatabase(version: Int, block: SQLiteDatabase.() -> Unit = {}) {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_locations` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `timezone_id` TEXT NOT NULL)"
        )
        db.block()
        db.version = version
        db.close()
    }

    private fun openCurrent(): PrayerAlarmDatabase = Room.databaseBuilder(
        context, PrayerAlarmDatabase::class.java, dbName
    )
        .addMigrations(PrayerAlarmDatabase.MIGRATION_1_2, PrayerAlarmDatabase.MIGRATION_2_3)
        .build()

    @Test
    fun migrate2To3_keepsLocationsWithNoAdjustmentsAndNoMethodOverride() = runBlocking {
        createLegacyDatabase(2) {
            execSQL(
                "INSERT INTO saved_locations (name, latitude, longitude, timezone_id) " +
                    "VALUES ('Sfax', 34.7406, 10.7603, 'Africa/Tunis')"
            )
        }

        val db = openCurrent()
        val sfax = db.locationDao().getAllLocations().first().single { it.name == "Sfax" }.toDomain()
        db.close()

        assertEquals(34.7406, sfax.latitude, 0.0)
        assertEquals(PrayerTimeAdjustments.NONE, sfax.adjustments)
        assertNull(sfax.calculationMethod)
    }

    @Test
    fun migrate1To3_seedsDefaultsAndNewColumnsRoundTrip() = runBlocking {
        createLegacyDatabase(1)

        val db = openCurrent()
        val dao = db.locationDao()
        assertEquals(DefaultLocations.all.size, dao.getLocationCount())

        val mahdia = dao.getAllLocations().first().single { it.name == "Mahdia" }
        dao.updateLocation(
            mahdia.copy(fajrAdjustment = 2, ishaAdjustment = -3, calculationMethod = PrayerCalculationMethod.EGYPTIAN.name)
        )
        val reloaded = dao.getLocationById(mahdia.id)!!.toDomain()
        db.close()

        assertEquals(PrayerTimeAdjustments(fajr = 2, isha = -3), reloaded.adjustments)
        assertEquals(PrayerCalculationMethod.EGYPTIAN, reloaded.calculationMethod)
    }
}
