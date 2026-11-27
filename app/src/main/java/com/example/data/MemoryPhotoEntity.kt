package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_photos")
data class MemoryPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val workspaceKey: String,
    val visitId: String,
    val locationName: String,
    val dateEpochDay: Long,
    val filePath: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
