package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class KunjunganWithTripAndPhotos(
    @Embedded val kunjungan: Kunjungan,
    @Relation(
        parentColumn = "tripId",
        entityColumn = "id"
    )
    val trip: Trip?,
    @Relation(
        parentColumn = "id",
        entityColumn = "kunjunganId"
    )
    val fotos: List<Foto>
)
