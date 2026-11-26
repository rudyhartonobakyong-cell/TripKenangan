package com.example.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripVisitDao: TripVisitDao) {
    val allVisits: Flow<List<TripVisitEntity>> = tripVisitDao.getAllVisits()

    suspend fun getPendingVisitsOnce(): List<TripVisitEntity> = tripVisitDao.getPendingVisitsOnce()

    suspend fun insertVisit(visit: TripVisitEntity): Long = tripVisitDao.insertVisit(visit)

    suspend fun updateVisit(visit: TripVisitEntity) = tripVisitDao.updateVisit(visit)

    suspend fun deleteVisitById(id: Long) = tripVisitDao.deleteVisitById(id)
}
