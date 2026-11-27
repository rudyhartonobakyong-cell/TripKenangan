package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryPhotoDao {
    @Query("SELECT * FROM memory_photos ORDER BY createdAtMillis ASC")
    fun getAllPhotos(): Flow<List<MemoryPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: MemoryPhotoEntity): Long

    @Query("DELETE FROM memory_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("DELETE FROM memory_photos WHERE visitId = :visitId")
    suspend fun deletePhotosForVisit(visitId: String)

    @Query("SELECT * FROM memory_photos WHERE visitId = :visitId")
    suspend fun getPhotosForVisitOnce(visitId: String): List<MemoryPhotoEntity>
}
