package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TripVisitEntity::class, MemoryPhotoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TripBuddyDatabase : RoomDatabase() {
    abstract fun tripVisitDao(): TripVisitDao
    abstract fun memoryPhotoDao(): MemoryPhotoDao

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

        fun getInstance(context: Context): TripBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripBuddyDatabase::class.java,
                    "tripbuddy_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
