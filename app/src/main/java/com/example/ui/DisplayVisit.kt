package com.example.ui

import com.example.collab.SharedVisit
import com.example.data.TripVisitEntity

data class DisplayVisit(
    val id: String,
    val localRoomId: Long?,
    val sharedTripId: String?,
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

    fun toLocalEntity(): TripVisitEntity {
        return TripVisitEntity(
            id = localRoomId ?: 0L,
            locationName = locationName,
            tripTitle = tripTitle,
            visitDateEpochDay = visitDateEpochDay,
            visitHour = visitHour,
            visitMinute = visitMinute,
            planNotes = planNotes,
            memoryNotes = memoryNotes,
            isCompletedManually = completedManually,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        fun fromLocal(entity: TripVisitEntity): DisplayVisit {
            return DisplayVisit(
                id = "local_${entity.id}",
                localRoomId = entity.id,
                sharedTripId = null,
                sharedDocId = null,
                locationName = entity.locationName,
                tripTitle = entity.tripTitle,
                visitDateEpochDay = entity.visitDateEpochDay,
                visitHour = entity.visitHour,
                visitMinute = entity.visitMinute,
                planNotes = entity.planNotes,
                memoryNotes = entity.memoryNotes,
                completedManually = entity.isCompletedManually,
                createdByName = "",
                createdAtMillis = entity.createdAtMillis
            )
        }

        fun fromShared(shared: SharedVisit): DisplayVisit {
            return DisplayVisit(
                id = "shared_${shared.id}",
                localRoomId = null,
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
