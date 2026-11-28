package com.example.drive

import android.content.Context
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.notifications.ReminderPreferences
import com.example.notifications.ReminderTimingOption
import com.example.notifications.UserReminderSettings
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.ThemePreferences
import com.example.util.BackupCorruptException
import com.example.util.BackupDataPayload
import com.example.util.BackupFotoPayload
import com.example.util.BackupInsufficientSpaceException
import com.example.util.BackupManager
import com.example.util.BackupSettingsPayload
import com.example.util.DateUtils
import com.example.util.PhotoStorageHelper
import java.io.File

data class DriveBackupExecutionResult(
    val manifest: DriveBackupManifest,
    val totalBackupSizeBytes: Long,
    val uploadedFilesCount: Int,
    val reusedFilesCount: Int,
    val uploadedBytes: Long
)

data class DriveRestoreExecutionResult(
    val restoredKunjunganCount: Int,
    val skippedKunjunganCount: Int,
    val restoredFotoCount: Int,
    val skippedFotoCount: Int,
    val downloadedMediaCount: Int,
    val restoredTheme: AppThemeOption,
    val restoredReminderSettings: UserReminderSettings
)

object DriveBackupEngine {
    private const val SPACE_SAFETY_BUFFER_BYTES = 5L * 1024L * 1024L

    suspend fun performIncrementalBackup(
        context: Context,
        userId: String,
        accessToken: String,
        localRepository: TripRepository,
        includeVideos: Boolean,
        onProgress: (DriveBackupProgress) -> Unit = {}
    ): Result<DriveBackupExecutionResult> {
        val appContext = context.applicationContext
        val nowMillis = System.currentTimeMillis()
        val prevStatus = DriveBackupPreferences.loadLastStatus(appContext, userId)

        return try {
            onProgress(
                DriveBackupProgress(
                    isRestore = false,
                    stepLabel = "Mengumpulkan data pribadi & media lokal...",
                    processedFiles = 0,
                    totalFiles = 1,
                    processedBytes = 0L,
                    totalBytes = 0L
                )
            )

            localRepository.ensurePersonalTripExists()
            val personalTrip = localRepository.getAllTripsOnce()
                .firstOrNull { it.id == Trip.PERSONAL_TRIP_ID }
                ?: Trip.personalTrip()
            val personalVisits = localRepository.getAllKunjunganOnce()
                .filter { it.tripId == Trip.PERSONAL_TRIP_ID }
            val personalVisitIds = personalVisits.map { it.id }.toSet()
            val personalFotos = localRepository.getAllFotosOnce()
                .filter {
                    (it.tripId == Trip.PERSONAL_TRIP_ID || it.kunjunganId in personalVisitIds) &&
                        (includeVideos || !it.isVideo)
                }

            val currentTheme = ThemePreferences.loadTheme(appContext)
            val reminderSettings = ReminderPreferences.loadSettings(appContext, userId)
            val settingsPayload = BackupSettingsPayload(
                themeId = currentTheme.id,
                remindersEnabled = reminderSettings.enabled,
                selectedTimingIds = reminderSettings.selectedTimings.map { it.id }.sorted(),
                annualMemoryReminderEnabled = reminderSettings.annualMemoryReminderEnabled
            )

            val localMediaFiles = collectLocalMediaCandidates(
                filesDir = appContext.filesDir,
                personalFotos = personalFotos,
                includeVideos = includeVideos
            )

            val dataPayload = BackupDataPayload(
                backupVersion = BackupManager.CURRENT_BACKUP_VERSION,
                personalTrip = personalTrip.copy(
                    id = Trip.PERSONAL_TRIP_ID,
                    name = personalTrip.name.ifBlank { "Pribadi" },
                    isPersonal = true,
                    remoteTripId = null
                ),
                kunjunganList = personalVisits.map {
                    it.copy(
                        tripId = Trip.PERSONAL_TRIP_ID,
                        remoteDocId = null,
                        remoteTripId = null
                    )
                },
                fotoList = personalFotos.map { foto ->
                    BackupFotoPayload(
                        id = foto.id,
                        kunjunganId = foto.kunjunganId,
                        tripId = Trip.PERSONAL_TRIP_ID,
                        relativePath = resolveRelativeFotosPath(appContext.filesDir, foto),
                        mediaStoreUri = foto.mediaStoreUri,
                        createdAtMillis = foto.createdAtMillis,
                        mediaType = foto.mediaType
                    )
                },
                settings = settingsPayload
            )
            val dataJsonText = BackupManager.encodeDataJson(dataPayload)
            val dataBytes = dataJsonText.toByteArray(Charsets.UTF_8)
            val dataSha256 = DriveBackupModels.computeSha256(dataBytes)

            onProgress(
                DriveBackupProgress(
                    isRestore = false,
                    stepLabel = "Memeriksa folder TripBuddy Backup di Google Drive...",
                    processedFiles = 0,
                    totalFiles = localMediaFiles.size + 2,
                    processedBytes = 0L,
                    totalBytes = localMediaFiles.sumOf { it.sizeBytes } + dataBytes.size
                )
            )

            val backupFolderId = DriveApiClient.findOrCreateFolder(
                accessToken = accessToken,
                folderName = DriveApiClient.DRIVE_BACKUP_FOLDER_NAME
            )
            val mediaFolderId = DriveApiClient.findOrCreateFolder(
                accessToken = accessToken,
                folderName = DriveApiClient.DRIVE_MEDIA_FOLDER_NAME,
                parentId = backupFolderId
            )

            val rootFiles = DriveApiClient.listFilesInFolder(accessToken, backupFolderId)
            val remoteMediaFiles = DriveApiClient.listFilesInFolder(accessToken, mediaFolderId)
            val existingRemoteMediaIds = remoteMediaFiles.map { it.id }.toSet()

            // Parse existing manifests (newest first) to calculate incremental diff
            val existingManifests = parseRemoteManifests(accessToken, rootFiles)
            val latestManifest = existingManifests.firstOrNull()?.manifest

            val diffPlan = DriveBackupModels.planIncrementalMediaUpload(
                localFiles = localMediaFiles,
                previousManifest = latestManifest,
                existingRemoteMediaFileIds = existingRemoteMediaIds
            )

            // Also match any already-uploaded Drive media from a previously interrupted run by (name, sha256, size)
            val remoteMediaByFingerprint = remoteMediaFiles.associateBy { remote ->
                val sha = remote.appProperties["sha256"].orEmpty()
                "${remote.name}|$sha|${remote.sizeBytes}"
            }

            val finalMediaEntries = ArrayList<DriveMediaManifestEntry>(localMediaFiles.size)
            finalMediaEntries.addAll(diffPlan.reusedEntries)

            val filesActuallyNeedingUpload = ArrayList<LocalMediaFileCandidate>()
            diffPlan.filesToUpload.forEach { candidate ->
                val fingerprint = "${candidate.file.name}|${candidate.sha256}|${candidate.sizeBytes}"
                val matchedRemote = remoteMediaByFingerprint[fingerprint]
                if (matchedRemote != null) {
                    finalMediaEntries.add(
                        DriveMediaManifestEntry(
                            relativePath = candidate.relativePath,
                            driveFileId = matchedRemote.id,
                            sizeBytes = candidate.sizeBytes,
                            lastModifiedMillis = candidate.lastModifiedMillis,
                            sha256 = candidate.sha256,
                            isVideo = candidate.isVideo
                        )
                    )
                } else {
                    filesActuallyNeedingUpload.add(candidate)
                }
            }

            val uploadBytesTotal = filesActuallyNeedingUpload.sumOf { it.sizeBytes } + dataBytes.size
            val totalUploadSteps = filesActuallyNeedingUpload.size + 2
            var completedSteps = 0
            var uploadedBytesSoFar = 0L

            filesActuallyNeedingUpload.forEach { candidate ->
                val mediaKind = if (candidate.isVideo) "video" else "foto"
                onProgress(
                    DriveBackupProgress(
                        isRestore = false,
                        stepLabel = "Mengunggah $mediaKind ${candidate.file.name} (${completedSteps + 1}/${filesActuallyNeedingUpload.size})...",
                        processedFiles = completedSteps,
                        totalFiles = totalUploadSteps,
                        processedBytes = uploadedBytesSoFar,
                        totalBytes = uploadBytesTotal
                    )
                )

                val cachedSession = DriveBackupPreferences.getResumableSession(
                    context = appContext,
                    userId = userId,
                    relativePath = candidate.relativePath,
                    sha256 = candidate.sha256,
                    fileSize = candidate.sizeBytes
                )

                val driveFileId = DriveApiClient.uploadMediaResumable(
                    accessToken = accessToken,
                    mediaFolderId = mediaFolderId,
                    file = candidate.file,
                    relativePath = candidate.relativePath,
                    sha256 = candidate.sha256,
                    existingSession = cachedSession,
                    onSessionChanged = { newSession ->
                        if (newSession != null) {
                            DriveBackupPreferences.saveResumableSession(appContext, userId, newSession)
                        } else {
                            DriveBackupPreferences.removeResumableSession(appContext, userId, candidate.relativePath)
                        }
                    },
                    onBytesDelta = { delta ->
                        uploadedBytesSoFar = (uploadedBytesSoFar + delta).coerceAtMost(uploadBytesTotal)
                        onProgress(
                            DriveBackupProgress(
                                isRestore = false,
                                stepLabel = "Mengunggah $mediaKind ${candidate.file.name} (${completedSteps + 1}/${filesActuallyNeedingUpload.size})...",
                                processedFiles = completedSteps,
                                totalFiles = totalUploadSteps,
                                processedBytes = uploadedBytesSoFar,
                                totalBytes = uploadBytesTotal
                            )
                        )
                    }
                )

                finalMediaEntries.add(
                    DriveMediaManifestEntry(
                        relativePath = candidate.relativePath,
                        driveFileId = driveFileId,
                        sizeBytes = candidate.sizeBytes,
                        lastModifiedMillis = candidate.lastModifiedMillis,
                        sha256 = candidate.sha256,
                        isVideo = candidate.isVideo
                    )
                )
                completedSteps++
            }

            onProgress(
                DriveBackupProgress(
                    isRestore = false,
                    stepLabel = "Mengunggah data kunjungan & pengaturan...",
                    processedFiles = completedSteps,
                    totalFiles = totalUploadSteps,
                    processedBytes = uploadedBytesSoFar,
                    totalBytes = uploadBytesTotal
                )
            )
            val dataFileName = DriveBackupModels.buildDataFileName(nowMillis)
            val uploadedDataFileId = DriveApiClient.uploadJsonMultipart(
                accessToken = accessToken,
                parentFolderId = backupFolderId,
                fileName = dataFileName,
                jsonText = dataJsonText,
                appProperties = mapOf("sha256" to dataSha256)
            )
            completedSteps++
            uploadedBytesSoFar = (uploadedBytesSoFar + dataBytes.size).coerceAtMost(uploadBytesTotal)

            val todayEpochDay = DateUtils.todayEpochDay(nowMillis)
            val visitIdsWithMedia = personalFotos.map { it.kunjunganId }.toSet()
            val memoryCount = personalVisits.count { k ->
                k.visitDateEpochDay < todayEpochDay ||
                    k.isCompletedManually ||
                    k.memoryNotes.isNotBlank() ||
                    k.id in visitIdsWithMedia
            }
            val totalMediaBytes = finalMediaEntries.sumOf { it.sizeBytes }
            val newManifest = DriveBackupManifest(
                app = BackupManager.BACKUP_APP_NAME,
                backupVersion = BackupManager.CURRENT_BACKUP_VERSION,
                createdAtMillis = nowMillis,
                includesVideos = includeVideos,
                kunjunganCount = personalVisits.size,
                memoryCount = memoryCount,
                photoCount = finalMediaEntries.count { !it.isVideo },
                videoCount = finalMediaEntries.count { it.isVideo },
                totalMediaBytes = totalMediaBytes,
                dataFileId = uploadedDataFileId,
                dataSha256 = dataSha256,
                mediaFiles = finalMediaEntries.sortedBy { it.relativePath }
            )

            onProgress(
                DriveBackupProgress(
                    isRestore = false,
                    stepLabel = "Menyimpan manifest versi terbaru...",
                    processedFiles = completedSteps,
                    totalFiles = totalUploadSteps,
                    processedBytes = uploadedBytesSoFar,
                    totalBytes = uploadBytesTotal
                )
            )
            val manifestFileName = DriveBackupModels.buildManifestFileName(nowMillis)
            val manifestJson = DriveBackupModels.encodeDriveManifestJson(newManifest)
            val uploadedManifestFileId = DriveApiClient.uploadJsonMultipart(
                accessToken = accessToken,
                parentFolderId = backupFolderId,
                fileName = manifestFileName,
                jsonText = manifestJson
            )

            // Enforce retention of at most 3 latest manifest versions and clean orphaned files
            val allManifestsNewestFirst = listOf(
                DriveRemoteBackupSummary(
                    manifestFileId = uploadedManifestFileId,
                    fileName = manifestFileName,
                    manifest = newManifest
                )
            ) + existingManifests

            val allRemoteDataIds = rootFiles
                .filter { it.name.startsWith(DriveBackupModels.DATA_FILE_PREFIX) && it.name.endsWith(".json") }
                .map { it.id }
                .toSet() + uploadedDataFileId

            val currentRemoteMediaIds = existingRemoteMediaIds + finalMediaEntries.map { it.driveFileId }
            val retentionPlan = DriveBackupModels.selectRetentionDeletions(
                manifestsNewestFirst = allManifestsNewestFirst,
                allRemoteMediaFileIds = currentRemoteMediaIds,
                allRemoteDataFileIds = allRemoteDataIds,
                maxVersions = DriveBackupModels.MAX_MANIFEST_VERSIONS
            )
            retentionPlan.manifestIdsToDelete.forEach { DriveApiClient.deleteFileQuietly(accessToken, it) }
            retentionPlan.dataFileIdsToDelete.forEach { DriveApiClient.deleteFileQuietly(accessToken, it) }
            retentionPlan.mediaFileIdsToDelete.forEach { DriveApiClient.deleteFileQuietly(accessToken, it) }

            val totalBackupSize = totalMediaBytes + dataBytes.size.toLong()
            val reusedCount = finalMediaEntries.size - filesActuallyNeedingUpload.size
            DriveBackupPreferences.saveLastStatus(
                appContext,
                userId,
                DriveBackupLastStatus(
                    lastAttemptAtMillis = nowMillis,
                    lastSuccessAtMillis = nowMillis,
                    lastBackupSizeBytes = totalBackupSize,
                    lastResultSuccess = true,
                    lastErrorMessage = "",
                    consecutiveFailures = 0,
                    lastUploadedFilesCount = filesActuallyNeedingUpload.size,
                    lastSkippedFilesCount = reusedCount
                )
            )

            onProgress(
                DriveBackupProgress(
                    isRestore = false,
                    stepLabel = "Cadangan Google Drive selesai",
                    processedFiles = totalUploadSteps,
                    totalFiles = totalUploadSteps,
                    processedBytes = uploadBytesTotal,
                    totalBytes = uploadBytesTotal
                )
            )

            Result.success(
                DriveBackupExecutionResult(
                    manifest = newManifest,
                    totalBackupSizeBytes = totalBackupSize,
                    uploadedFilesCount = filesActuallyNeedingUpload.size,
                    reusedFilesCount = reusedCount,
                    uploadedBytes = uploadBytesTotal
                )
            )
        } catch (t: Throwable) {
            val errorMsg = t.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Gagal mencadangkan data ke Google Drive."
            DriveBackupPreferences.saveLastStatus(
                appContext,
                userId,
                prevStatus.copy(
                    lastAttemptAtMillis = nowMillis,
                    lastResultSuccess = false,
                    lastErrorMessage = errorMsg,
                    consecutiveFailures = prevStatus.consecutiveFailures + 1
                )
            )
            Result.failure(t)
        }
    }

    suspend fun fetchAvailableDriveBackups(
        accessToken: String
    ): Result<List<DriveRemoteBackupSummary>> = runCatching {
        val backupFolderId = DriveApiClient.findFolder(
            accessToken = accessToken,
            folderName = DriveApiClient.DRIVE_BACKUP_FOLDER_NAME
        ) ?: return@runCatching emptyList()

        val rootFiles = DriveApiClient.listFilesInFolder(accessToken, backupFolderId)
        parseRemoteManifests(accessToken, rootFiles)
            .take(DriveBackupModels.MAX_MANIFEST_VERSIONS)
    }

    suspend fun inspectDriveBackupForRestore(
        context: Context,
        accessToken: String,
        summary: DriveRemoteBackupSummary,
        localRepository: TripRepository
    ): Result<DriveRestoreConfirmationSummary> = runCatching {
        val dataFileId = summary.manifest.dataFileId
        if (dataFileId.isBlank()) {
            throw BackupCorruptException("Data kunjungan tidak tertaut pada manifest Drive ini.")
        }
        val dataJson = DriveApiClient.downloadTextFile(accessToken, dataFileId)
        val payload = BackupManager.parseDataJson(dataJson)

        val existingKunjunganIds = localRepository.getAllKunjunganOnce().map { it.id }.toSet()
        val existingFotoIds = localRepository.getAllFotosOnce().map { it.id }.toSet()

        val newKunjunganCount = payload.kunjunganList.count { it.id !in existingKunjunganIds }
        val existingKunjunganCount = payload.kunjunganList.size - newKunjunganCount
        val newFotoCount = payload.fotoList.count { it.id !in existingFotoIds }
        val existingFotoCount = payload.fotoList.size - newFotoCount

        val filesDir = context.applicationContext.filesDir
        val missingEntries = summary.manifest.mediaFiles.filter { entry ->
            val target = File(filesDir, BackupManager.sanitizeZipEntryPath(entry.relativePath))
            !(target.exists() && target.length() > 0L)
        }

        DriveRestoreConfirmationSummary(
            remoteSummary = summary,
            newKunjunganCount = newKunjunganCount,
            existingKunjunganCount = existingKunjunganCount,
            newFotoCount = newFotoCount,
            existingFotoCount = existingFotoCount,
            missingMediaFilesCount = missingEntries.size,
            missingMediaBytes = missingEntries.sumOf { it.sizeBytes }
        )
    }

    suspend fun restoreFromDriveBackup(
        context: Context,
        userId: String,
        accessToken: String,
        localRepository: TripRepository,
        summary: DriveRemoteBackupSummary,
        onProgress: (DriveBackupProgress) -> Unit = {}
    ): Result<DriveRestoreExecutionResult> = runCatching {
        val appContext = context.applicationContext
        val filesDir = appContext.filesDir
        val baseCanonical = filesDir.canonicalPath

        onProgress(
            DriveBackupProgress(
                isRestore = true,
                stepLabel = "Mengunduh data pribadi dari Google Drive...",
                processedFiles = 0,
                totalFiles = summary.manifest.mediaFiles.size + 1,
                processedBytes = 0L,
                totalBytes = summary.manifest.totalMediaBytes
            )
        )

        val dataFileId = summary.manifest.dataFileId
        if (dataFileId.isBlank()) {
            throw BackupCorruptException("File data.json pada versi cadangan ini tidak ditemukan.")
        }
        val dataJson = DriveApiClient.downloadTextFile(accessToken, dataFileId)
        val parsedData = BackupManager.parseDataJson(dataJson)

        val filesToDownload = summary.manifest.mediaFiles.filter { entry ->
            val safeRel = BackupManager.sanitizeZipEntryPath(entry.relativePath)
            val targetFile = File(filesDir, safeRel)
            if (!targetFile.canonicalPath.startsWith(baseCanonical + File.separator)) {
                throw BackupCorruptException("Jalur file di manifest Drive tidak aman: $safeRel")
            }
            !(targetFile.exists() && targetFile.length() > 0L)
        }

        val requiredBytes = filesToDownload.sumOf { it.sizeBytes } + SPACE_SAFETY_BUFFER_BYTES
        val availableBytes = BackupManager.availableSpaceBytes(filesDir)
        if (availableBytes < requiredBytes) {
            throw BackupInsufficientSpaceException(
                "Ruang penyimpanan HP kurang untuk memulihkan dari Drive. Dibutuhkan ${BackupManager.formatByteSize(requiredBytes)}, tersedia ${BackupManager.formatByteSize(availableBytes)}."
            )
        }

        val totalDownloadBytes = filesToDownload.sumOf { it.sizeBytes }
        val totalFiles = filesToDownload.size + 1
        var downloadedBytes = 0L
        var downloadedCount = 0

        filesToDownload.forEach { entry ->
            val safeRel = BackupManager.sanitizeZipEntryPath(entry.relativePath)
            val targetFile = File(filesDir, safeRel)
            val label = if (entry.isVideo) "Mengunduh video" else "Mengunduh foto"
            onProgress(
                DriveBackupProgress(
                    isRestore = true,
                    stepLabel = "$label (${downloadedCount + 1}/${filesToDownload.size})...",
                    processedFiles = downloadedCount + 1,
                    totalFiles = totalFiles,
                    processedBytes = downloadedBytes,
                    totalBytes = totalDownloadBytes
                )
            )
            DriveApiClient.downloadMediaFileResumable(
                accessToken = accessToken,
                fileId = entry.driveFileId,
                destFile = targetFile,
                expectedSizeBytes = entry.sizeBytes,
                onBytesDelta = { delta ->
                    downloadedBytes = (downloadedBytes + delta).coerceAtMost(totalDownloadBytes)
                    onProgress(
                        DriveBackupProgress(
                            isRestore = true,
                            stepLabel = "$label (${downloadedCount + 1}/${filesToDownload.size})...",
                            processedFiles = downloadedCount + 1,
                            totalFiles = totalFiles,
                            processedBytes = downloadedBytes,
                            totalBytes = totalDownloadBytes
                        )
                    )
                }
            )
            downloadedCount++
        }

        onProgress(
            DriveBackupProgress(
                isRestore = true,
                stepLabel = "Memulihkan database lokal & pengaturan...",
                processedFiles = totalFiles,
                totalFiles = totalFiles,
                processedBytes = totalDownloadBytes,
                totalBytes = totalDownloadBytes
            )
        )

        localRepository.ensurePersonalTripExists()
        val existingKunjunganIds = localRepository.getAllKunjunganOnce().map { it.id }.toSet()
        val existingFotoIds = localRepository.getAllFotosOnce().map { it.id }.toSet()

        val newKunjungan = parsedData.kunjunganList
            .filter { it.id !in existingKunjunganIds }
            .map {
                it.copy(
                    tripId = Trip.PERSONAL_TRIP_ID,
                    remoteDocId = null,
                    remoteTripId = null
                )
            }
        val skippedKunjunganCount = parsedData.kunjunganList.size - newKunjungan.size

        val validKunjunganIds = existingKunjunganIds + newKunjungan.map { it.id }
        val newFotos = ArrayList<Foto>()
        var skippedFotoCount = 0

        parsedData.fotoList.forEach { entry ->
            if (entry.id in existingFotoIds) {
                skippedFotoCount++
            } else if (entry.kunjunganId in validKunjunganIds) {
                val safeRel = BackupManager.sanitizeZipEntryPath(entry.relativePath)
                val targetFile = File(filesDir, safeRel)
                if (targetFile.canonicalPath.startsWith(baseCanonical + File.separator)) {
                    newFotos.add(
                        Foto(
                            id = entry.id,
                            kunjunganId = entry.kunjunganId,
                            tripId = Trip.PERSONAL_TRIP_ID,
                            filePath = targetFile.absolutePath,
                            mediaStoreUri = entry.mediaStoreUri,
                            createdAtMillis = entry.createdAtMillis,
                            mediaType = if (entry.mediaType == Foto.MEDIA_TYPE_VIDEO) {
                                Foto.MEDIA_TYPE_VIDEO
                            } else {
                                Foto.MEDIA_TYPE_PHOTO
                            }
                        )
                    )
                }
            }
        }

        localRepository.restorePersonalBackupData(
            newKunjungan = newKunjungan,
            newFotos = newFotos
        )

        newFotos.filter { it.isVideo }.forEach { video ->
            PhotoStorageHelper.generateAndCacheVideoThumbnail(
                context = appContext,
                videoPath = video.filePath,
                mediaUuid = video.id
            )
        }

        val restoredTheme = AppThemeOption.fromId(parsedData.settings.themeId)
        ThemePreferences.saveTheme(appContext, restoredTheme)

        val restoredTimings = ReminderTimingOption.fromIds(
            parsedData.settings.selectedTimingIds.toSet()
        )
        val restoredReminder = UserReminderSettings(
            enabled = parsedData.settings.remindersEnabled,
            selectedTimings = restoredTimings,
            annualMemoryReminderEnabled = parsedData.settings.annualMemoryReminderEnabled
        )
        ReminderPreferences.saveSettings(appContext, userId, restoredReminder)

        DriveRestoreExecutionResult(
            restoredKunjunganCount = newKunjungan.size,
            skippedKunjunganCount = skippedKunjunganCount,
            restoredFotoCount = newFotos.size,
            skippedFotoCount = skippedFotoCount,
            downloadedMediaCount = downloadedCount,
            restoredTheme = restoredTheme,
            restoredReminderSettings = restoredReminder
        )
    }

    private fun parseRemoteManifests(
        accessToken: String,
        rootFiles: List<DriveRemoteFile>
    ): List<DriveRemoteBackupSummary> {
        val manifestFiles = rootFiles.filter {
            it.name.startsWith(DriveBackupModels.MANIFEST_FILE_PREFIX) && it.name.endsWith(".json")
        }
        return manifestFiles.mapNotNull { remote ->
            runCatching {
                val json = DriveApiClient.downloadTextFile(accessToken, remote.id)
                val parsed = DriveBackupModels.parseDriveManifestJson(json)
                DriveRemoteBackupSummary(
                    manifestFileId = remote.id,
                    fileName = remote.name,
                    manifest = parsed
                )
            }.getOrNull()
        }.sortedByDescending { it.manifest.createdAtMillis }
    }

    private fun collectLocalMediaCandidates(
        filesDir: File,
        personalFotos: List<Foto>,
        includeVideos: Boolean
    ): List<LocalMediaFileCandidate> {
        val byRelativePath = LinkedHashMap<String, LocalMediaFileCandidate>()
        val fotosRoot = File(filesDir, "fotos")
        if (fotosRoot.exists() && fotosRoot.isDirectory) {
            fotosRoot.walkTopDown()
                .filter { it.isFile && it.length() > 0L && !it.name.endsWith(".drive_dl_part") && !it.name.endsWith(".restore_tmp") }
                .sortedBy { it.invariantSeparatorsPath }
                .forEach { file ->
                    val relUnderFotos = file.relativeTo(fotosRoot).invariantSeparatorsPath
                    val relPath = "${BackupManager.FOTOS_PREFIX}$relUnderFotos"
                    val isVideo = BackupManager.isVideoFileName(file.name)
                    if (includeVideos || !isVideo) {
                        byRelativePath[relPath] = LocalMediaFileCandidate(
                            file = file,
                            relativePath = relPath,
                            sizeBytes = file.length(),
                            lastModifiedMillis = file.lastModified(),
                            sha256 = DriveBackupModels.computeSha256(file),
                            isVideo = isVideo
                        )
                    }
                }
        }

        personalFotos.forEach { foto ->
            if (includeVideos || !foto.isVideo) {
                val file = File(foto.filePath)
                if (file.exists() && file.isFile && file.length() > 0L) {
                    val relPath = resolveRelativeFotosPath(filesDir, foto)
                    if (!byRelativePath.containsKey(relPath)) {
                        byRelativePath[relPath] = LocalMediaFileCandidate(
                            file = file,
                            relativePath = relPath,
                            sizeBytes = file.length(),
                            lastModifiedMillis = file.lastModified(),
                            sha256 = DriveBackupModels.computeSha256(file),
                            isVideo = foto.isVideo
                        )
                    }
                }
            }
        }

        return byRelativePath.values.toList()
    }

    private fun resolveRelativeFotosPath(filesDir: File, foto: Foto): String {
        val file = File(foto.filePath)
        val fotosDir = File(filesDir, "fotos")
        val fotosCanonical = runCatching { fotosDir.canonicalPath }.getOrElse { fotosDir.absolutePath }
        val fileCanonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (fileCanonical.startsWith(fotosCanonical + File.separator)) {
            val rel = fileCanonical.removePrefix(fotosCanonical + File.separator).replace(File.separatorChar, '/')
            return "${BackupManager.FOTOS_PREFIX}$rel"
        }
        val ext = file.name.substringAfterLast('.', if (foto.isVideo) "mp4" else "jpg")
        val safeFileName = file.name.ifBlank { "${foto.id}.$ext" }
        return "${BackupManager.FOTOS_PREFIX}${Trip.PERSONAL_TRIP_ID}/${foto.kunjunganId}/$safeFileName"
    }
}
