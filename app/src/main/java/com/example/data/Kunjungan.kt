package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "kunjungan",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripId"]),
        Index(value = ["visitDateEpochDay", "visitHour", "visitMinute"])
    ]
)
data class Kunjungan(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val locationName: String,
    val tripTitle: String = "",
    val visitDateEpochDay: Long,
    val visitHour: Int = 9,
    val visitMinute: Int = 0,
    val planNotes: String = "",
    val memoryNotes: String = "",
    val isCompletedManually: Boolean = false,
    val createdByName: String = "",
    val remoteDocId: String? = null,
    val remoteTripId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        fun sharedVisitUuid(remoteTripId: String, remoteDocId: String): String {
            return UUID.nameUUIDFromBytes("tripbuddy_shared_visit_${remoteTripId}_$remoteDocId".toByteArray()).toString()
        }

        fun legacyLocalVisitUuid(legacyId: Long): String {
            return UUID.nameUUIDFromBytes("tripbuddy_legacy_local_$legacyId".toByteArray()).toString()
        }
    }
}
