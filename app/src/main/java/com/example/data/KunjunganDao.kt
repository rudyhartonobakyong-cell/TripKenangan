package com.example.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KunjunganDao {
    @Query("SELECT * FROM kunjungan ORDER BY visitDateEpochDay ASC, visitHour ASC, visitMinute ASC, createdAtMillis ASC")
    fun observeAllKunjungan(): Flow<List<Kunjungan>>

    @Query("SELECT * FROM kunjungan WHERE tripId = :tripId ORDER BY visitDateEpochDay ASC, visitHour ASC, visitMinute ASC, createdAtMillis ASC")
    fun observeKunjunganForTrip(tripId: String): Flow<List<Kunjungan>>

    @Query("SELECT * FROM kunjungan WHERE visitDateEpochDay = :todayEpochDay ORDER BY visitHour ASC, visitMinute ASC, createdAtMillis ASC")
    fun observeKunjunganOnDate(todayEpochDay: Long): Flow<List<Kunjungan>>

    @Transaction
    @Query(
        """
        SELECT k.* FROM kunjungan k
        WHERE k.visitDateEpochDay < :todayEpochDay
           OR k.isCompletedManually = 1
           OR EXISTS (SELECT 1 FROM foto f WHERE f.kunjunganId = k.id)
        ORDER BY k.visitDateEpochDay DESC, k.visitHour DESC, k.visitMinute DESC, k.createdAtMillis DESC
        """
    )
    fun observeMemoryKunjungan(todayEpochDay: Long): Flow<List<KunjunganWithTripAndPhotos>>

    @Transaction
    @Query("SELECT * FROM kunjungan WHERE id = :kunjunganId LIMIT 1")
    fun observeKunjunganDetailById(kunjunganId: String): Flow<KunjunganWithTripAndPhotos?>

    @Query("SELECT * FROM kunjungan WHERE id = :id LIMIT 1")
    suspend fun getKunjunganByIdOnce(id: String): Kunjungan?

    @Query("SELECT * FROM kunjungan WHERE tripId = :tripId")
    suspend fun getKunjunganForTripOnce(tripId: String): List<Kunjungan>

    @Query("SELECT * FROM kunjungan WHERE isCompletedManually = 0")
    suspend fun getPendingKunjunganOnce(): List<Kunjungan>

    @Query("SELECT * FROM kunjungan")
    suspend fun getAllKunjunganOnce(): List<Kunjungan>

    @Upsert
    suspend fun upsertKunjungan(kunjungan: Kunjungan)

    @Upsert
    suspend fun upsertKunjunganList(list: List<Kunjungan>)

    @Query("UPDATE kunjungan SET memoryNotes = :memoryNotes WHERE id = :kunjunganId")
    suspend fun updateMemoryNotes(kunjunganId: String, memoryNotes: String)

    @Query("DELETE FROM kunjungan WHERE id = :id")
    suspend fun deleteKunjunganById(id: String)
}
