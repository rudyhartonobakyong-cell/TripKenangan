package com.example.drive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DriveBackupScheduler {
    const val UNIQUE_WORK_NAME = "tripbuddy_drive_periodic_backup"
    const val KEY_USER_ID = "drive_backup_user_id"
    private const val MIN_DELAY_MILLIS = 60_000L

    fun syncSchedule(context: Context, userId: String) {
        val appContext = context.applicationContext
        val cleanUserId = userId.ifBlank { DriveBackupPreferences.getActiveUserId(appContext) }
        val connection = DriveBackupPreferences.loadConnection(appContext, cleanUserId)
        val settings = DriveBackupPreferences.loadScheduleSettings(appContext, cleanUserId)

        val workManager = WorkManager.getInstance(appContext)
        if (!connection.isConnected || settings.frequency == DriveBackupFrequency.OFF) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val initialDelayMillis = computeInitialDelayMillis(settings, System.currentTimeMillis())
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(settings.chargingOnly)
            .build()

        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            repeatInterval = settings.frequency.intervalDays.coerceAtLeast(1L),
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_USER_ID to cleanUserId))
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelSchedule(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun computeInitialDelayMillis(
        settings: DriveBackupScheduleSettings,
        nowMillis: Long
    ): Long {
        if (settings.frequency == DriveBackupFrequency.OFF) return 0L

        val hour = settings.hour.coerceIn(0, 23)
        val minute = settings.minute.coerceIn(0, 59)
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (settings.frequency) {
            DriveBackupFrequency.DAILY -> {
                if (target.timeInMillis <= nowMillis + MIN_DELAY_MILLIS) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            DriveBackupFrequency.WEEKLY -> {
                target.set(Calendar.DAY_OF_WEEK, settings.dayOfWeek)
                // If setting DAY_OF_WEEK moved backward or landed too close in the past, step week by week
                while (target.timeInMillis <= nowMillis + MIN_DELAY_MILLIS) {
                    target.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            DriveBackupFrequency.MONTHLY -> {
                val targetDay = settings.dayOfMonth.coerceIn(1, 28)
                target.set(Calendar.DAY_OF_MONTH, targetDay)
                if (target.timeInMillis <= nowMillis + MIN_DELAY_MILLIS) {
                    target.add(Calendar.MONTH, 1)
                    target.set(Calendar.DAY_OF_MONTH, targetDay)
                }
            }

            DriveBackupFrequency.OFF -> return 0L
        }

        return (target.timeInMillis - nowMillis).coerceAtLeast(MIN_DELAY_MILLIS)
    }
}
