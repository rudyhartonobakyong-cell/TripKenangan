package com.example.drive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.TripBuddyDatabase
import com.example.data.TripRepository
import com.example.notifications.TripNotificationHelper
import com.example.util.BackupManager

class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val userId = inputData.getString(DriveBackupScheduler.KEY_USER_ID)
            ?.takeIf { it.isNotBlank() }
            ?: DriveBackupPreferences.getActiveUserId(appContext)

        val connection = DriveBackupPreferences.loadConnection(appContext, userId)
        val schedule = DriveBackupPreferences.loadScheduleSettings(appContext, userId)
        if (!connection.isConnected || schedule.frequency == DriveBackupFrequency.OFF) {
            return Result.success()
        }

        val authOutcome = DriveAuthManager.authorizeDriveAccess(
            context = appContext,
            preferredEmail = connection.accountEmail
        )
        val accessToken = when (authOutcome) {
            is DriveAuthOutcome.Authorized -> authOutcome.accessToken
            is DriveAuthOutcome.NeedsResolution -> {
                val err = "Izin Google Drive perlu diberikan ulang di Pengaturan."
                recordFailureAndMaybeWarn(appContext, userId, err)
                return Result.failure()
            }
            is DriveAuthOutcome.Failed -> {
                recordFailureAndMaybeWarn(appContext, userId, authOutcome.message)
                return if (runAttemptCount < 2) Result.retry() else Result.failure()
            }
        }

        val db = TripBuddyDatabase.getInstance(appContext)
        val localRepository = TripRepository(
            tripDao = db.tripDao(),
            kunjunganDao = db.kunjunganDao(),
            fotoDao = db.fotoDao()
        )

        val backupResult = DriveBackupEngine.performIncrementalBackup(
            context = appContext,
            userId = userId,
            accessToken = accessToken,
            localRepository = localRepository,
            includeVideos = schedule.includeVideos
        )

        return backupResult.fold(
            onSuccess = { exec ->
                val formattedSize = BackupManager.formatByteSize(exec.totalBackupSizeBytes)
                TripNotificationHelper.showDriveBackupSuccessNotification(
                    context = appContext,
                    uploadedFilesCount = exec.uploadedFilesCount,
                    totalSizeFormatted = formattedSize
                )
                Result.success()
            },
            onFailure = { err ->
                val latestStatus = DriveBackupPreferences.loadLastStatus(appContext, userId)
                if (latestStatus.consecutiveFailures >= CONSECUTIVE_FAILURE_ALERT_THRESHOLD) {
                    TripNotificationHelper.showDriveBackupFailureWarningNotification(
                        context = appContext,
                        consecutiveFailures = latestStatus.consecutiveFailures,
                        reason = err.localizedMessage ?: latestStatus.lastErrorMessage
                    )
                }
                val isAuth = (err as? DriveApiException)?.isAuthError == true
                if (!isAuth && runAttemptCount < 2) Result.retry() else Result.failure()
            }
        )
    }

    private fun recordFailureAndMaybeWarn(
        context: Context,
        userId: String,
        message: String
    ) {
        val prev = DriveBackupPreferences.loadLastStatus(context, userId)
        val nextFailures = prev.consecutiveFailures + 1
        DriveBackupPreferences.saveLastStatus(
            context,
            userId,
            prev.copy(
                lastAttemptAtMillis = System.currentTimeMillis(),
                lastResultSuccess = false,
                lastErrorMessage = message,
                consecutiveFailures = nextFailures
            )
        )
        if (nextFailures >= CONSECUTIVE_FAILURE_ALERT_THRESHOLD) {
            TripNotificationHelper.showDriveBackupFailureWarningNotification(
                context = context,
                consecutiveFailures = nextFailures,
                reason = message
            )
        }
    }

    companion object {
        const val CONSECUTIVE_FAILURE_ALERT_THRESHOLD = 3
    }
}
