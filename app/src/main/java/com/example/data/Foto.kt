package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "foto",
    foreignKeys = [
        ForeignKey(
            entity = Kunjungan::class,
            parentColumns = ["id"],
            childColumns = ["kunjunganId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["kunjunganId"]),
        Index(value = ["tripId"])
    ]
)
data class Foto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kunjunganId: String,
    val tripId: String,
    val filePath: String,
    val mediaStoreUri: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
