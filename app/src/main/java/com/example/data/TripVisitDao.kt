package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripVisitDao {
    @Query(
        "SELECT * FROM trip_visits ORDER BY visitDateEpochDay ASC, visitHour ASC, visitMinute ASC, createdAtMillis ASC"
    )
    fun getAllVisits(): Flow<List<TripVisitEntity>>

    @Query("SELECT * FROM trip_visits WHERE isCompletedManually = 0")
    suspend fun getPendingVisitsOnce(): List<TripVisitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisit(visit: TripVisitEntity): Long

    @Update
    suspend fun updateVisit(visit: TripVisitEntity)

    @Query("DELETE FROM trip_visits WHERE id = :id")
    suspend fun deleteVisitById(id: Long)
}
