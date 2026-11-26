package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_visits")
data class TripVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val locationName: String,
    val tripTitle: String = "",
    val visitDateEpochDay: Long,
    val visitHour: Int = 9,
    val visitMinute: Int = 0,
    val planNotes: String = "",
    val memoryNotes: String = "",
    val isCompletedManually: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
