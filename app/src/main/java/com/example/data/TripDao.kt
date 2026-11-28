package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY isPersonal DESC, createdAtMillis ASC, name ASC")
    fun observeAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripByIdOnce(id: String): Trip?

    @Query("SELECT * FROM trips")
    suspend fun getAllTripsOnce(): List<Trip>

    @Upsert
    suspend fun upsertTrip(trip: Trip)

    @Upsert
    suspend fun upsertTrips(trips: List<Trip>)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTripById(id: String)
}
