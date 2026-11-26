package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TripVisitEntity::class], version = 1, exportSchema = false)
abstract class TripBuddyDatabase : RoomDatabase() {
    abstract fun tripVisitDao(): TripVisitDao

    companion object {
        @Volatile
        private var INSTANCE: TripBuddyDatabase? = null

        fun getInstance(context: Context): TripBuddyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TripBuddyDatabase::class.java,
                    "tripbuddy_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
