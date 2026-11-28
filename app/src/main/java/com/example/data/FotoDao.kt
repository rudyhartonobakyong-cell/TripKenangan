package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FotoDao {
    @Query("SELECT * FROM foto ORDER BY createdAtMillis ASC")
    fun observeAllFotos(): Flow<List<Foto>>

    @Query("SELECT * FROM foto ORDER BY createdAtMillis ASC")
    suspend fun getAllFotosOnce(): List<Foto>

    @Query("SELECT * FROM foto WHERE kunjunganId = :kunjunganId ORDER BY createdAtMillis ASC")
    fun observeFotosForKunjungan(kunjunganId: String): Flow<List<Foto>>

    @Query("SELECT * FROM foto WHERE kunjunganId = :kunjunganId ORDER BY createdAtMillis ASC")
    suspend fun getFotosForKunjunganOnce(kunjunganId: String): List<Foto>

    @Query("SELECT COUNT(*) FROM foto WHERE kunjunganId = :kunjunganId")
    suspend fun getFotoCountForKunjunganOnce(kunjunganId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoto(foto: Foto)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFotosIgnore(fotos: List<Foto>)

    @Query("DELETE FROM foto WHERE id = :id")
    suspend fun deleteFotoById(id: String)
}
