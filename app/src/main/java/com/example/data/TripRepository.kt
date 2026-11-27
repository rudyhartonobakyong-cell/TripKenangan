package com.example.data

import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.flow.Flow

class TripRepository(
    private val tripVisitDao: TripVisitDao,
    private val memoryPhotoDao: MemoryPhotoDao
) {
    val allVisits: Flow<List<TripVisitEntity>> = tripVisitDao.getAllVisits()
    val allPhotos: Flow<List<MemoryPhotoEntity>> = memoryPhotoDao.getAllPhotos()

    suspend fun getPendingVisitsOnce(): List<TripVisitEntity> = tripVisitDao.getPendingVisitsOnce()

    suspend fun insertVisit(visit: TripVisitEntity): Long = tripVisitDao.insertVisit(visit)

    suspend fun updateVisit(visit: TripVisitEntity) = tripVisitDao.updateVisit(visit)

    suspend fun deleteVisitById(id: Long) = tripVisitDao.deleteVisitById(id)

    suspend fun insertPhoto(photo: MemoryPhotoEntity): Long = memoryPhotoDao.insertPhoto(photo)

    suspend fun deletePhoto(photo: MemoryPhotoEntity) {
        memoryPhotoDao.deletePhotoById(photo.id)
        PhotoStorageHelper.deleteFileQuietly(photo.filePath)
    }

    suspend fun deletePhotosForVisit(visitId: String) {
        val photos = memoryPhotoDao.getPhotosForVisitOnce(visitId)
        memoryPhotoDao.deletePhotosForVisit(visitId)
        photos.forEach { PhotoStorageHelper.deleteFileQuietly(it.filePath) }
    }
}
