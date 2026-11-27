package com.example.ui

import com.example.collab.SharedVisit
import com.example.data.Kunjungan
import com.example.data.Trip

data class DisplayVisit(
    val id: String,
    val tripId: String,
    val tripName: String = "Pribadi",
    val sharedTripId: String? = null,
    val sharedDocId: String? = null,
    val locationName: String,
    val tripTitle: String,
    val visitDateEpochDay: Long,
    val visitHour: Int,
    val visitMinute: Int,
    val planNotes: String,
    val memoryNotes: String,
    val completedManually: Boolean,
    val createdByName: String,
    val createdAtMillis: Long
) {
    val isShared: Boolean get() = sharedTripId != null

    fun toKunjunganEntity(): Kunjungan {
        return Kunjungan(
            id = id,
            tripId = tripId,
            locationName = locationName,
            tripTitle = tripTitle,
            visitDateEpochDay = visitDateEpochDay,
            visitHour = visitHour,
            visitMinute = visitMinute,
            planNotes = planNotes,
            memoryNotes = memoryNotes,
            isCompletedManually = completedManually,
            createdByName = createdByName,
            remoteDocId = sharedDocId,
            remoteTripId = sharedTripId,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        fun fromKunjungan(
            entity: Kunjungan,
            tripName: String = if (entity.tripId == Trip.PERSONAL_TRIP_ID) "Pribadi" else "Trip Bersama"
        ): DisplayVisit {
            return DisplayVisit(
                id = entity.id,
                tripId = entity.tripId,
                tripName = tripName,
                sharedTripId = entity.remoteTripId,
                sharedDocId = entity.remoteDocId,
                locationName = entity.locationName,
                tripTitle = entity.tripTitle,
                visitDateEpochDay = entity.visitDateEpochDay,
                visitHour = entity.visitHour,
                visitMinute = entity.visitMinute,
                planNotes = entity.planNotes,
                memoryNotes = entity.memoryNotes,
                completedManually = entity.isCompletedManually,
                createdByName = entity.createdByName,
                createdAtMillis = entity.createdAtMillis
            )
        }

        fun fromShared(
            shared: SharedVisit,
            tripName: String = "Trip Bersama"
        ): DisplayVisit {
            return DisplayVisit(
                id = Kunjungan.sharedVisitUuid(shared.tripId, shared.id),
                tripId = Trip.sharedTripUuid(shared.tripId),
                tripName = tripName,
                sharedTripId = shared.tripId,
                sharedDocId = shared.id,
                locationName = shared.locationName,
                tripTitle = shared.tripTitle,
                visitDateEpochDay = shared.visitDateEpochDay,
                visitHour = shared.visitHour.toInt(),
                visitMinute = shared.visitMinute.toInt(),
                planNotes = shared.planNotes,
                memoryNotes = shared.memoryNotes,
                completedManually = shared.completedManually,
                createdByName = shared.createdByName,
                createdAtMillis = shared.createdAt?.toDate()?.time ?: 0L
            )
        }
    }
}
