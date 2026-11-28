package com.example.data

import android.content.Context
import com.example.collab.SharedTrip
import com.example.collab.SharedVisit
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TripRepository(
    private val tripDao: TripDao,
    private val kunjunganDao: KunjunganDao,
    private val fotoDao: FotoDao
) {
    val allTrips: Flow<List<Trip>> = tripDao.observeAllTrips()
    val allKunjungan: Flow<List<Kunjungan>> = kunjunganDao.observeAllKunjungan()
    val personalKunjungan: Flow<List<Kunjungan>> = kunjunganDao.observeKunjunganForTrip(Trip.PERSONAL_TRIP_ID)
    val allFotos: Flow<List<Foto>> = fotoDao.observeAllFotos()

    fun observeMemoryKunjungan(todayEpochDay: Long): Flow<List<KunjunganWithTripAndPhotos>> {
        return kunjunganDao.observeMemoryKunjungan(todayEpochDay)
    }

    fun observeKunjunganDetailById(kunjunganId: String): Flow<KunjunganWithTripAndPhotos?> {
        return kunjunganDao.observeKunjunganDetailById(kunjunganId)
    }

    fun observeKunjunganForTrip(tripId: String): Flow<List<Kunjungan>> {
        return kunjunganDao.observeKunjunganForTrip(tripId)
    }

    suspend fun ensurePersonalTripExists() {
        if (tripDao.getTripByIdOnce(Trip.PERSONAL_TRIP_ID) == null) {
            tripDao.upsertTrip(Trip.personalTrip())
        }
    }

    suspend fun getPendingKunjunganOnce(): List<Kunjungan> = kunjunganDao.getPendingKunjunganOnce()

    suspend fun upsertKunjungan(kunjungan: Kunjungan) {
        ensurePersonalTripExists()
        kunjunganDao.upsertKunjungan(kunjungan)
    }

    suspend fun updateMemoryNotes(kunjunganId: String, memoryNotes: String) {
        kunjunganDao.updateMemoryNotes(kunjunganId, memoryNotes)
    }

    suspend fun deleteKunjunganById(kunjunganId: String) {
        val photos = fotoDao.getFotosForKunjunganOnce(kunjunganId)
        kunjunganDao.deleteKunjunganById(kunjunganId)
        photos.forEach { PhotoStorageHelper.deleteFileQuietly(it.filePath) }
    }

    suspend fun syncSharedTripsToRoom(sharedTrips: List<SharedTrip>) {
        if (sharedTrips.isEmpty()) return
        ensurePersonalTripExists()
        val tripEntities = sharedTrips.map { shared ->
            Trip(
                id = Trip.sharedTripUuid(shared.id),
                name = shared.title.ifBlank { "Trip Bersama" },
                isPersonal = false,
                remoteTripId = shared.id,
                createdAtMillis = shared.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            )
        }
        tripDao.upsertTrips(tripEntities)
    }

    suspend fun syncSharedTripToRoom(shared: SharedTrip) {
        ensurePersonalTripExists()
        tripDao.upsertTrip(
            Trip(
                id = Trip.sharedTripUuid(shared.id),
                name = shared.title.ifBlank { "Trip Bersama" },
                isPersonal = false,
                remoteTripId = shared.id,
                createdAtMillis = shared.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            )
        )
    }

    suspend fun syncSharedVisitsToRoom(
        sharedTrip: SharedTrip,
        remoteVisits: List<SharedVisit>
    ) {
        syncSharedTripToRoom(sharedTrip)
        val tripUuid = Trip.sharedTripUuid(sharedTrip.id)
        val mapped = remoteVisits.map { remote ->
            val visitUuid = Kunjungan.sharedVisitUuid(sharedTrip.id, remote.id)
            val existing = kunjunganDao.getKunjunganByIdOnce(visitUuid)
            val resolvedMemoryNotes = remote.memoryNotes.ifBlank { existing?.memoryNotes.orEmpty() }
            Kunjungan(
                id = visitUuid,
                tripId = tripUuid,
                locationName = remote.locationName,
                tripTitle = remote.tripTitle,
                visitDateEpochDay = remote.visitDateEpochDay,
                visitHour = remote.visitHour.toInt(),
                visitMinute = remote.visitMinute.toInt(),
                planNotes = remote.planNotes,
                memoryNotes = resolvedMemoryNotes,
                isCompletedManually = remote.completedManually,
                createdByName = remote.createdByName,
                remoteDocId = remote.id,
                remoteTripId = sharedTrip.id,
                createdAtMillis = remote.createdAt?.toDate()?.time
                    ?: existing?.createdAtMillis
                    ?: System.currentTimeMillis()
            )
        }
        if (mapped.isNotEmpty()) {
            kunjunganDao.upsertKunjunganList(mapped)
        }
    }

    suspend fun saveCapturedPhotoForKunjungan(
        context: Context,
        tempCaptureFile: File,
        kunjunganId: String
    ): Result<Foto> = withContext(Dispatchers.IO) {
        runCatching {
            val kunjungan = kunjunganDao.getKunjunganByIdOnce(kunjunganId)
                ?: throw IllegalStateException("Kunjungan tidak ditemukan di database lokal.")
            val persisted = PhotoStorageHelper.persistCapturedPhoto(
                context = context,
                tempFile = tempCaptureFile,
                tripId = kunjungan.tripId,
                kunjunganId = kunjungan.id
            )
            val foto = Foto(
                id = persisted.photoUuid,
                kunjunganId = kunjungan.id,
                tripId = kunjungan.tripId,
                filePath = persisted.privateFilePath,
                mediaStoreUri = persisted.mediaStoreUri,
                createdAtMillis = System.currentTimeMillis()
            )
            fotoDao.insertFoto(foto)
            foto
        }
    }

    suspend fun deleteFoto(foto: Foto) {
        fotoDao.deleteFotoById(foto.id)
        withContext(Dispatchers.IO) {
            PhotoStorageHelper.deleteFileQuietly(foto.filePath)
        }
    }

    suspend fun getKunjunganIdsForSharedTrip(remoteTripId: String): List<String> {
        val tripUuid = Trip.sharedTripUuid(remoteTripId)
        return kunjunganDao.getKunjunganForTripOnce(tripUuid).map { it.id }
    }

    suspend fun cleanupRemovedSharedTripCache(remoteTripId: String) {
        val tripUuid = Trip.sharedTripUuid(remoteTripId)
        val visits = kunjunganDao.getKunjunganForTripOnce(tripUuid)
        var remainingWithPhotos = 0
        visits.forEach { kunjungan ->
            val fotos = fotoDao.getFotosForKunjunganOnce(kunjungan.id)
            if (fotos.isEmpty()) {
                kunjunganDao.deleteKunjunganById(kunjungan.id)
            } else {
                remainingWithPhotos++
            }
        }
        if (remainingWithPhotos == 0) {
            tripDao.deleteTripById(tripUuid)
        }
    }
}
