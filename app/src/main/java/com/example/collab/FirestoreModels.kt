package com.example.collab

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SharedTrip(
    @DocumentId val id: String = "",
    val title: String = "",
    val ownerId: String = "",
    val inviteCode: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TripMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: String = "member",
    val inviteCodeUsed: String = "",
    @ServerTimestamp val joinedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SharedVisit(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val locationName: String = "",
    val tripTitle: String = "",
    val visitDateEpochDay: Long = 0L,
    val visitHour: Long = 9L,
    val visitMinute: Long = 0L,
    val planNotes: String = "",
    val memoryNotes: String = "",
    val completedManually: Boolean = false,
    val createdByUid: String = "",
    val createdByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TripDateComment(
    @DocumentId val id: String = "",
    val tripId: String = "",
    val dateEpochDay: Long = 0L,
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhotoUrl: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
