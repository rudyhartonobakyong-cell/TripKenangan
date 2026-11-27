package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [Trip::class, Kunjungan::class, Foto::class],
    version = 3,
    exportSchema = false
)
abstract class TripBuddyDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun kunjunganDao(): KunjunganDao
    abstract fun fotoDao(): FotoDao

    companion object {
        @Volatile
        private var INSTANCE: TripBuddyDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceKey TEXT NOT NULL,
                        visitId TEXT NOT NULL,
                        locationName TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trips (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        isPersonal INTEGER NOT NULL,
                        remoteTripId TEXT,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS kunjungan (
                        id TEXT NOT NULL PRIMARY KEY,
                        tripId TEXT NOT NULL,
                        locationName TEXT NOT NULL,
                        tripTitle TEXT NOT NULL,
                        visitDateEpochDay INTEGER NOT NULL,
                        visitHour INTEGER NOT NULL,
                        visitMinute INTEGER NOT NULL,
                        planNotes TEXT NOT NULL,
                        memoryNotes TEXT NOT NULL,
                        isCompletedManually INTEGER NOT NULL,
                        createdByName TEXT NOT NULL,
                        remoteDocId TEXT,
                        remoteTripId TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_kunjungan_tripId ON kunjungan(tripId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_kunjungan_visitDateEpochDay_visitHour_visitMinute ON kunjungan(visitDateEpochDay, visitHour, visitMinute)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS foto (
                        id TEXT NOT NULL PRIMARY KEY,
                        kunjunganId TEXT NOT NULL,
                        tripId TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        mediaStoreUri TEXT,
                        createdAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(kunjunganId) REFERENCES kunjungan(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_kunjunganId ON foto(kunjunganId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_foto_tripId ON foto(tripId)")

                db.execSQL(
                    """
                    INSERT OR IGNORE INTO trips (id, name, isPersonal, remoteTripId, createdAtMillis)
                    VALUES ('${Trip.PERSONAL_TRIP_ID}', 'Pribadi', 1, NULL, 0)
                    """.trimIndent()
                )

                // Migrate legacy local visits from trip_visits
                val visitCursor = db.query(
                    "SELECT id, locationName, tripTitle, visitDateEpochDay, visitHour, visitMinute, planNotes, memoryNotes, isCompletedManually, createdAtMillis FROM trip_visits"
                )
                val legacyIdToUuid = mutableMapOf<String, String>()
                visitCursor.use { c ->
                    while (c.moveToNext()) {
                        val oldId = c.getLong(0)
                        val newUuid = Kunjungan.legacyLocalVisitUuid(oldId)
                        legacyIdToUuid["local_$oldId"] = newUuid
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO kunjungan (
                                id, tripId, locationName, tripTitle, visitDateEpochDay, visitHour, visitMinute,
                                planNotes, memoryNotes, isCompletedManually, createdByName, remoteDocId, remoteTripId, createdAtMillis
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', NULL, NULL, ?)
                            """.trimIndent(),
                            arrayOf<Any>(
                                newUuid,
                                Trip.PERSONAL_TRIP_ID,
                                c.getString(1),
                                c.getString(2),
                                c.getLong(3),
                                c.getInt(4),
                                c.getInt(5),
                                c.getString(6),
                                c.getString(7),
                                c.getInt(8),
                                c.getLong(9)
                            )
                        )
                    }
                }

                // Migrate legacy photos from memory_photos if table exists
                val photoCursor = db.query(
                    "SELECT id, workspaceKey, visitId, locationName, dateEpochDay, filePath, createdAtMillis FROM memory_photos"
                )
                photoCursor.use { c ->
                    while (c.moveToNext()) {
                        val oldPhotoId = c.getLong(0)
                        val workspaceKey = c.getString(1)
                        val oldVisitId = c.getString(2)
                        val locationName = c.getString(3)
                        val dateEpochDay = c.getLong(4)
                        val filePath = c.getString(5)
                        val createdAt = c.getLong(6)

                        val isShared = workspaceKey.startsWith("shared_")
                        val remoteTripId = if (isShared) workspaceKey.removePrefix("shared_") else null
                        val tripUuid = if (remoteTripId != null) {
                            Trip.sharedTripUuid(remoteTripId)
                        } else {
                            Trip.PERSONAL_TRIP_ID
                        }

                        if (remoteTripId != null) {
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO trips (id, name, isPersonal, remoteTripId, createdAtMillis)
                                VALUES (?, 'Trip Bersama', 0, ?, ?)
                                """.trimIndent(),
                                arrayOf<Any>(tripUuid, remoteTripId, createdAt)
                            )
                        }

                        val remoteDocId = if (oldVisitId.startsWith("shared_")) {
                            oldVisitId.removePrefix("shared_")
                        } else null

                        val targetKunjunganUuid = legacyIdToUuid[oldVisitId] ?: if (remoteTripId != null && remoteDocId != null) {
                            Kunjungan.sharedVisitUuid(remoteTripId, remoteDocId)
                        } else {
                            UUID.nameUUIDFromBytes("tripbuddy_legacy_photo_visit_$oldVisitId".toByteArray()).toString()
                        }

                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO kunjungan (
                                id, tripId, locationName, tripTitle, visitDateEpochDay, visitHour, visitMinute,
                                planNotes, memoryNotes, isCompletedManually, createdByName, remoteDocId, remoteTripId, createdAtMillis
                            ) VALUES (?, ?, ?, '', ?, 9, 0, '', '', 0, '', ?, ?, ?)
                            """.trimIndent(),
                            arrayOf<Any?>(
                                targetKunjunganUuid,
                                tripUuid,
                                locationName.ifBlank { "Kenangan Perjalanan" },
                                dateEpochDay,
                                remoteDocId,
                                remoteTripId,
                                createdAt
                            )
                        )

                        val photoUuid = UUID.nameUUIDFromBytes("tripbuddy_legacy_photo_$oldPhotoId".toByteArray()).toString()
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO foto (id, kunjunganId, tripId, filePath, mediaStoreUri, createdAtMillis)
                            VALUES (?, ?, ?, ?, NULL, ?)
                            """.trimIndent(),
                            arrayOf<Any>(photoUuid, targetKunjunganUuid, tripUuid, filePath, createdAt)
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS memory_photos")
                db.execSQL("DROP TABLE IF EXISTS trip_visits")
            }
        }

        fun getInstance(context: Context): TripBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripBuddyDatabase::class.java,
                    "tripbuddy_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO trips (id, name, isPersonal, remoteTripId, createdAtMillis)
                                VALUES ('${Trip.PERSONAL_TRIP_ID}', 'Pribadi', 1, NULL, 0)
                                """.trimIndent()
                            )
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO trips (id, name, isPersonal, remoteTripId, createdAtMillis)
                                VALUES ('${Trip.PERSONAL_TRIP_ID}', 'Pribadi', 1, NULL, 0)
                                """.trimIndent()
                            )
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
