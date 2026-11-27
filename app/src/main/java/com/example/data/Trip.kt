package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPersonal: Boolean = false,
    val remoteTripId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val PERSONAL_TRIP_ID = "00000000-0000-4000-8000-000000000001"

        fun personalTrip(): Trip = Trip(
            id = PERSONAL_TRIP_ID,
            name = "Pribadi",
            isPersonal = true,
            remoteTripId = null,
            createdAtMillis = 0L
        )

        fun sharedTripUuid(remoteTripId: String): String {
            return UUID.nameUUIDFromBytes("tripbuddy_shared_trip_$remoteTripId".toByteArray()).toString()
        }
    }
}
