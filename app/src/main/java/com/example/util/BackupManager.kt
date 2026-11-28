package com.example.util

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.notifications.ReminderPreferences
import com.example.notifications.ReminderTimingOption
import com.example.notifications.UserReminderSettings
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.ThemePreferences
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupManifest(
    val app: String,
    val backupVersion: Int,
    val exportedAtMillis: Long,
    val includesVideos: Boolean,
    val kunjunganCount: Int,
    val memoryCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val mediaFileCount: Int,
    val totalMediaBytes: Long
)

data class BackupSettingsPayload(
    val themeId: String,
    val remindersEnabled: Boolean,
    val selectedTimingIds: List<String>,
    val annualMemoryReminderEnabled: Boolean
)

data class BackupFotoPayload(
    val id: String,
    val kunjunganId: String,
    val tripId: String,
    val relativePath: String,
    val mediaStoreUri: String?,
    val createdAtMillis: Long,
    val mediaType: String
)

data class BackupDataPayload(
    val backupVersion: Int,
    val personalTrip: Trip,
    val kunjunganList: List<Kunjungan>,
    val fotoList: List<BackupFotoPayload>,
    val settings: BackupSettingsPayload
)

data class BackupEstimate(
    val includeVideos: Boolean,
    val kunjunganCount: Int,
    val memoryCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val estimatedZipBytes: Long
)

data class BackupProgress(
    val isImport: Boolean,
    val stepLabel: String,
    val processedItems: Int,
    val totalItems: Int,
    val processedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalItems > 0 -> (processedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

data class BackupExportResult(
    val zipFile: File,
    val contentUri: Uri,
    val fileName: String,
    val zipSizeBytes: Long,
    val manifest: BackupManifest
)

data class BackupInspectionSummary(
    val sourceUri: Uri,
    val displayName: String,
    val manifest: BackupManifest,
    val newKunjunganCount: Int,
    val existingKunjunganCount: Int,
    val newFotoCount: Int,
    val existingFotoCount: Int,
    val estimatedRestoreBytes: Long
)

data class BackupParsedRestore(
    val manifest: BackupManifest,
    val newKunjungan: List<Kunjungan>,
    val skippedKunjunganCount: Int,
    val newFotos: List<Foto>,
    val skippedFotoCount: Int,
    val restoredMediaFilesCount: Int,
    val restoredSettings: BackupSettingsPayload
)

data class BackupRestoreResult(
    val restoredKunjunganCount: Int,
    val skippedKunjunganCount: Int,
    val restoredFotoCount: Int,
    val skippedFotoCount: Int,
    val restoredMediaFilesCount: Int,
    val restoredTheme: AppThemeOption?,
    val restoredReminderSettings: UserReminderSettings?
)

private data class PlannedMediaEntry(
    val sourceFile: File,
    val relativeZipPath: String,
    val isVideo: Boolean,
    val byteSize: Long
)

object BackupManager {

    const val BACKUP_APP_NAME = "TripBuddy"
    const val CURRENT_BACKUP_VERSION = 1
    const val ENTRY_MANIFEST = "manifest.json"
    const val ENTRY_DATA = "data.json"
    const val FOTOS_PREFIX = "fotos/"
    private const val BACKUP_CACHE_DIR = "backups"
    private const val MAX_JSON_ENTRY_BYTES = 32 * 1024 * 1024 // 32 MB cap
    private const val SPACE_SAFETY_BUFFER_BYTES = 5L * 1024L * 1024L

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "3gp", "mkv", "webm", "m4v")

    fun isVideoFileName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in VIDEO_EXTENSIONS
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb).replace('.', ',')
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb).replace('.', ',')
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    fun buildBackupFileName(timestampMillis: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(timestampMillis))
        return "TripBuddy_Cadangan_$stamp.zip"
    }

    fun availableSpaceBytes(directory: File): Long {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val stat = StatFs(directory.absolutePath)
        return stat.availableBytes
    }

    fun computeBackupEstimate(
        filesDir: File,
        personalKunjungan: List<Kunjungan>,
        personalFotos: List<Foto>,
        includeVideos: Boolean,
        todayEpochDay: Long = DateUtils.todayEpochDay()
    ): BackupEstimate {
        val allMedia = collectMediaEntries(
            filesDir = filesDir,
            personalFotos = personalFotos,
            includeVideos = true
        )
        val photoFiles = allMedia.filter { !it.isVideo }
        val videoFiles = allMedia.filter { it.isVideo }

        val photoBytes = photoFiles.sumOf { it.byteSize }
        val videoBytes = if (includeVideos) videoFiles.sumOf { it.byteSize } else 0L
        val jsonEstimateBytes = 4096L + (personalKunjungan.size * 520L) + (personalFotos.size * 260L)
        val includedMediaCount = photoFiles.size + if (includeVideos) videoFiles.size else 0
        val zipOverhead = 512L + (includedMediaCount * 160L)

        val memoryCount = countPersonalMemories(
            personalKunjungan = personalKunjungan,
            personalFotos = personalFotos,
            todayEpochDay = todayEpochDay
        )

        return BackupEstimate(
            includeVideos = includeVideos,
            kunjunganCount = personalKunjungan.size,
            memoryCount = memoryCount,
            photoCount = photoFiles.size,
            videoCount = if (includeVideos) videoFiles.size else 0,
            estimatedZipBytes = jsonEstimateBytes + photoBytes + videoBytes + zipOverhead
        )
    }

    fun createBackupZipToFile(
        filesDir: File,
        destZipFile: File,
        personalTrip: Trip,
        personalKunjungan: List<Kunjungan>,
        personalFotos: List<Foto>,
        settings: BackupSettingsPayload,
        includeVideos: Boolean,
        todayEpochDay: Long,
        exportedAtMillis: Long = System.currentTimeMillis(),
        availableDestBytesProvider: () -> Long = {
            availableSpaceBytes(destZipFile.parentFile ?: filesDir)
        },
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupManifest {
        val personalOnlyVisits = personalKunjungan.filter { it.tripId == Trip.PERSONAL_TRIP_ID }
        val personalVisitIds = personalOnlyVisits.map { it.id }.toSet()
        val personalOnlyFotos = personalFotos.filter {
            (it.tripId == Trip.PERSONAL_TRIP_ID || it.kunjunganId in personalVisitIds) &&
                (includeVideos || !it.isVideo)
        }

        val mediaEntries = collectMediaEntries(
            filesDir = filesDir,
            personalFotos = personalOnlyFotos,
            includeVideos = includeVideos
        )
        val totalMediaBytes = mediaEntries.sumOf { it.byteSize }
        val photoFileCount = mediaEntries.count { !it.isVideo }
        val videoFileCount = mediaEntries.count { it.isVideo }

        val dataPayload = BackupDataPayload(
            backupVersion = CURRENT_BACKUP_VERSION,
            personalTrip = personalTrip.copy(
                id = Trip.PERSONAL_TRIP_ID,
                name = personalTrip.name.ifBlank { "Pribadi" },
                isPersonal = true,
                remoteTripId = null
            ),
            kunjunganList = personalOnlyVisits.map {
                it.copy(
                    tripId = Trip.PERSONAL_TRIP_ID,
                    remoteDocId = null,
                    remoteTripId = null
                )
            },
            fotoList = personalOnlyFotos.map { foto ->
                BackupFotoPayload(
                    id = foto.id,
                    kunjunganId = foto.kunjunganId,
                    tripId = Trip.PERSONAL_TRIP_ID,
                    relativePath = resolveRelativeZipPath(filesDir, foto),
                    mediaStoreUri = foto.mediaStoreUri,
                    createdAtMillis = foto.createdAtMillis,
                    mediaType = foto.mediaType
                )
            },
            settings = settings
        )

        val memoryCount = countPersonalMemories(
            personalKunjungan = personalOnlyVisits,
            personalFotos = personalOnlyFotos,
            todayEpochDay = todayEpochDay
        )

        val manifest = BackupManifest(
            app = BACKUP_APP_NAME,
            backupVersion = CURRENT_BACKUP_VERSION,
            exportedAtMillis = exportedAtMillis,
            includesVideos = includeVideos,
            kunjunganCount = personalOnlyVisits.size,
            memoryCount = memoryCount,
            photoCount = photoFileCount,
            videoCount = videoFileCount,
            mediaFileCount = mediaEntries.size,
            totalMediaBytes = totalMediaBytes
        )

        val manifestBytes = encodeManifestJson(manifest).toByteArray(Charsets.UTF_8)
        val dataBytes = encodeDataJson(dataPayload).toByteArray(Charsets.UTF_8)
        val requiredBytes = manifestBytes.size.toLong() + dataBytes.size.toLong() + totalMediaBytes + SPACE_SAFETY_BUFFER_BYTES
        val availableBytes = availableDestBytesProvider()
        if (availableBytes < requiredBytes) {
            throw BackupInsufficientSpaceException(
                "Ruang penyimpanan HP kurang untuk membuat file cadangan. Dibutuhkan minimal ${formatByteSize(requiredBytes)}, tersedia ${formatByteSize(availableBytes)}."
            )
        }

        val parentDir = destZipFile.parentFile ?: throw IOException("Direktori tujuan cadangan tidak valid.")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = File(parentDir, "${destZipFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val totalBytes = manifestBytes.size.toLong() + dataBytes.size.toLong() + totalMediaBytes
        val totalItems = mediaEntries.size + 2
        var processedItems = 0
        var processedBytes = 0L

        onProgress(
            BackupProgress(
                isImport = false,
                stepLabel = "Menyiapkan data pribadi...",
                processedItems = 0,
                totalItems = totalItems,
                processedBytes = 0L,
                totalBytes = totalBytes
            )
        )

        try {
            ZipOutputStream(BufferedOutputStream(tempFile.outputStream())).use { zos ->
                zos.setLevel(Deflater.DEFAULT_COMPRESSION)
                zos.putNextEntry(ZipEntry(ENTRY_MANIFEST))
                zos.write(manifestBytes)
                zos.closeEntry()
                processedItems++
                processedBytes += manifestBytes.size
                onProgress(
                    BackupProgress(
                        isImport = false,
                        stepLabel = "Menulis manifest cadangan...",
                        processedItems = processedItems,
                        totalItems = totalItems,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes
                    )
                )

                zos.putNextEntry(ZipEntry(ENTRY_DATA))
                zos.write(dataBytes)
                zos.closeEntry()
                processedItems++
                processedBytes += dataBytes.size
                onProgress(
                    BackupProgress(
                        isImport = false,
                        stepLabel = "Menyimpan kunjungan & pengaturan...",
                        processedItems = processedItems,
                        totalItems = totalItems,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes
                    )
                )

                // Use fast/light deflate level for already-compressed JPEG/MP4 media while keeping exact original bytes
                zos.setLevel(Deflater.BEST_SPEED)
                val buffer = ByteArray(64 * 1024)
                mediaEntries.forEach { entry ->
                    val labelPrefix = if (entry.isVideo) "Menyalin video" else "Menyalin foto"
                    onProgress(
                        BackupProgress(
                            isImport = false,
                            stepLabel = "$labelPrefix (${processedItems - 1}/${mediaEntries.size})...",
                            processedItems = processedItems,
                            totalItems = totalItems,
                            processedBytes = processedBytes,
                            totalBytes = totalBytes
                        )
                    )
                    zos.putNextEntry(ZipEntry(entry.relativeZipPath))
                    entry.sourceFile.inputStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            zos.write(buffer, 0, read)
                            processedBytes += read
                        }
                    }
                    zos.closeEntry()
                    processedItems++
                }
            }

            if (destZipFile.exists()) {
                destZipFile.delete()
            }
            if (!tempFile.renameTo(destZipFile)) {
                tempFile.copyTo(destZipFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(
                BackupProgress(
                    isImport = false,
                    stepLabel = "Cadangan selesai dibuat",
                    processedItems = totalItems,
                    totalItems = totalItems,
                    processedBytes = totalBytes,
                    totalBytes = totalBytes
                )
            )

            return manifest
        } catch (e: IOException) {
            tempFile.delete()
            if (isOutOfSpaceError(e)) {
                throw BackupInsufficientSpaceException(
                    "Ruang penyimpanan HP penuh saat membuat file cadangan. Kosongkan sebagian memori lalu coba lagi."
                )
            }
            throw e
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    fun exportPersonalBackup(
        context: Context,
        personalTrip: Trip,
        personalKunjungan: List<Kunjungan>,
        personalFotos: List<Foto>,
        userId: String,
        includeVideos: Boolean,
        onProgress: (BackupProgress) -> Unit
    ): Result<BackupExportResult> = runCatching {
        val backupDir = File(context.cacheDir, BACKUP_CACHE_DIR)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        } else {
            // Remove previous cached backup zips to prevent wasted cache space
            backupDir.listFiles()?.forEach { old ->
                if (old.isFile) old.delete()
            }
        }

        val nowMillis = System.currentTimeMillis()
        val fileName = buildBackupFileName(nowMillis)
        val zipFile = File(backupDir, fileName)

        val currentTheme = ThemePreferences.loadTheme(context)
        val reminderSettings = ReminderPreferences.loadSettings(context, userId)
        val settingsPayload = BackupSettingsPayload(
            themeId = currentTheme.id,
            remindersEnabled = reminderSettings.enabled,
            selectedTimingIds = reminderSettings.selectedTimings.map { it.id }.sorted(),
            annualMemoryReminderEnabled = reminderSettings.annualMemoryReminderEnabled
        )

        val manifest = createBackupZipToFile(
            filesDir = context.filesDir,
            destZipFile = zipFile,
            personalTrip = personalTrip,
            personalKunjungan = personalKunjungan,
            personalFotos = personalFotos,
            settings = settingsPayload,
            includeVideos = includeVideos,
            todayEpochDay = DateUtils.todayEpochDay(nowMillis),
            exportedAtMillis = nowMillis,
            onProgress = onProgress
        )

        val uri = PhotoStorageHelper.getUriForFile(context, zipFile)
        BackupExportResult(
            zipFile = zipFile,
            contentUri = uri,
            fileName = fileName,
            zipSizeBytes = zipFile.length(),
            manifest = manifest
        )
    }

    fun inspectBackupZipFromStream(
        streamProvider: () -> InputStream?,
        sourceUri: Uri,
        displayName: String,
        existingKunjunganIds: Set<String>,
        existingFotoIds: Set<String>,
        availableDestBytesProvider: () -> Long
    ): BackupInspectionSummary {
        val rawStream = streamProvider()
            ?: throw BackupCorruptException("Tidak dapat membuka file cadangan yang dipilih.")

        var manifest: BackupManifest? = null
        var dataPayload: BackupDataPayload? = null
        var actualPhotoFiles = 0
        var actualVideoFiles = 0
        var actualMediaBytes = 0L

        try {
            val buffered = BufferedInputStream(rawStream)
            buffered.mark(8)
            val header = ByteArray(4)
            val headerRead = buffered.read(header)
            buffered.reset()
            if (headerRead < 4 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                throw BackupCorruptException("File yang dipilih bukan arsip ZIP cadangan TripBuddy yang valid.")
            }

            ZipInputStream(buffered).use { zis ->
                val drainBuf = ByteArray(32 * 1024)
                var entry = zis.nextEntry
                while (entry != null) {
                    val safeName = sanitizeZipEntryPath(entry.name)
                    if (!entry.isDirectory) {
                        when {
                            safeName == ENTRY_MANIFEST -> {
                                val text = readBoundedUtf8(zis, MAX_JSON_ENTRY_BYTES)
                                manifest = parseManifestJson(text)
                            }
                            safeName == ENTRY_DATA -> {
                                val text = readBoundedUtf8(zis, MAX_JSON_ENTRY_BYTES)
                                dataPayload = parseDataJson(text)
                            }
                            safeName.startsWith(FOTOS_PREFIX) -> {
                                var entryBytes = 0L
                                while (true) {
                                    val r = zis.read(drainBuf)
                                    if (r <= 0) break
                                    entryBytes += r
                                }
                                actualMediaBytes += entryBytes
                                if (isVideoFileName(safeName)) {
                                    actualVideoFiles++
                                } else {
                                    actualPhotoFiles++
                                }
                            }
                            else -> {
                                // Drain unknown entry safely to verify ZIP integrity
                                while (zis.read(drainBuf) > 0) {
                                    // no-op
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: BackupCorruptException) {
            throw e
        } catch (e: ZipException) {
            throw BackupCorruptException("File ZIP rusak atau tidak dapat dibaca (${e.localizedMessage ?: "ZIP error"}).", e)
        } catch (e: IOException) {
            throw BackupCorruptException("Gagal membaca file cadangan karena file rusak atau terpotong.", e)
        }

        val validManifest = manifest
            ?: throw BackupCorruptException("File manifest.json tidak ditemukan. Ini bukan cadangan TripBuddy yang valid.")
        val validData = dataPayload
            ?: throw BackupCorruptException("File data.json tidak ditemukan. Cadangan tidak lengkap atau rusak.")

        val requiredBytes = actualMediaBytes + SPACE_SAFETY_BUFFER_BYTES
        val availableBytes = availableDestBytesProvider()
        if (availableBytes < requiredBytes) {
            throw BackupInsufficientSpaceException(
                "Ruang penyimpanan HP kurang untuk memulihkan cadangan ini. Dibutuhkan minimal ${formatByteSize(requiredBytes)}, tersedia ${formatByteSize(availableBytes)}."
            )
        }

        val newKunjunganCount = validData.kunjunganList.count { it.id !in existingKunjunganIds }
        val existingKunjunganCount = validData.kunjunganList.size - newKunjunganCount
        val newFotoCount = validData.fotoList.count { it.id !in existingFotoIds }
        val existingFotoCount = validData.fotoList.size - newFotoCount

        val normalizedManifest = validManifest.copy(
            photoCount = maxOf(validManifest.photoCount, actualPhotoFiles),
            videoCount = maxOf(validManifest.videoCount, actualVideoFiles),
            mediaFileCount = maxOf(validManifest.mediaFileCount, actualPhotoFiles + actualVideoFiles),
            totalMediaBytes = maxOf(validManifest.totalMediaBytes, actualMediaBytes)
        )

        return BackupInspectionSummary(
            sourceUri = sourceUri,
            displayName = displayName,
            manifest = normalizedManifest,
            newKunjunganCount = newKunjunganCount,
            existingKunjunganCount = existingKunjunganCount,
            newFotoCount = newFotoCount,
            existingFotoCount = existingFotoCount,
            estimatedRestoreBytes = actualMediaBytes
        )
    }

    fun inspectBackupZip(
        context: Context,
        uri: Uri,
        existingKunjunganIds: Set<String>,
        existingFotoIds: Set<String>
    ): Result<BackupInspectionSummary> = runCatching {
        val displayName = resolveUriDisplayName(context, uri)
        inspectBackupZipFromStream(
            streamProvider = { context.contentResolver.openInputStream(uri) },
            sourceUri = uri,
            displayName = displayName,
            existingKunjunganIds = existingKunjunganIds,
            existingFotoIds = existingFotoIds,
            availableDestBytesProvider = { availableSpaceBytes(context.filesDir) }
        )
    }

    fun extractBackupZipFromStream(
        streamProvider: () -> InputStream?,
        filesDir: File,
        existingKunjunganIds: Set<String>,
        existingFotoIds: Set<String>,
        expectedTotalBytes: Long,
        expectedTotalMediaFiles: Int,
        availableDestBytesProvider: () -> Long = { availableSpaceBytes(filesDir) },
        onProgress: (BackupProgress) -> Unit = {}
    ): BackupParsedRestore {
        val rawStream = streamProvider()
            ?: throw BackupCorruptException("Tidak dapat membuka file cadangan untuk pemulihan.")

        var manifest: BackupManifest? = null
        var dataPayload: BackupDataPayload? = null
        var restoredMediaCount = 0
        var processedMediaIndex = 0
        var processedBytes = 0L

        val baseCanonicalPath = filesDir.canonicalPath
        val totalItems = (expectedTotalMediaFiles + 2).coerceAtLeast(2)

        onProgress(
            BackupProgress(
                isImport = true,
                stepLabel = "Memeriksa ruang penyimpanan & arsip...",
                processedItems = 0,
                totalItems = totalItems,
                processedBytes = 0L,
                totalBytes = expectedTotalBytes
            )
        )

        val availableBytes = availableDestBytesProvider()
        if (expectedTotalBytes > 0L && availableBytes < expectedTotalBytes + SPACE_SAFETY_BUFFER_BYTES) {
            throw BackupInsufficientSpaceException(
                "Ruang penyimpanan HP kurang untuk memulihkan cadangan. Dibutuhkan ${formatByteSize(expectedTotalBytes + SPACE_SAFETY_BUFFER_BYTES)}, tersedia ${formatByteSize(availableBytes)}."
            )
        }

        try {
            ZipInputStream(BufferedInputStream(rawStream)).use { zis ->
                val buffer = ByteArray(64 * 1024)
                var entry = zis.nextEntry
                while (entry != null) {
                    val safeName = sanitizeZipEntryPath(entry.name)
                    if (!entry.isDirectory) {
                        when {
                            safeName == ENTRY_MANIFEST -> {
                                manifest = parseManifestJson(readBoundedUtf8(zis, MAX_JSON_ENTRY_BYTES))
                            }
                            safeName == ENTRY_DATA -> {
                                dataPayload = parseDataJson(readBoundedUtf8(zis, MAX_JSON_ENTRY_BYTES))
                            }
                            safeName.startsWith(FOTOS_PREFIX) -> {
                                processedMediaIndex++
                                val targetFile = File(filesDir, safeName)
                                val targetCanonical = targetFile.canonicalPath
                                if (!targetCanonical.startsWith(baseCanonicalPath + File.separator)) {
                                    throw BackupCorruptException("Jalur file di dalam cadangan tidak aman: $safeName")
                                }

                                val isVideo = isVideoFileName(safeName)
                                onProgress(
                                    BackupProgress(
                                        isImport = true,
                                        stepLabel = if (isVideo) {
                                            "Memulihkan video ($processedMediaIndex)..."
                                        } else {
                                            "Memulihkan foto ($processedMediaIndex)..."
                                        },
                                        processedItems = processedMediaIndex,
                                        totalItems = totalItems,
                                        processedBytes = processedBytes,
                                        totalBytes = expectedTotalBytes
                                    )
                                )

                                if (targetFile.exists() && targetFile.length() > 0L) {
                                    // File already exists on device; skip overwrite while verifying stream
                                    while (true) {
                                        val r = zis.read(buffer)
                                        if (r <= 0) break
                                        processedBytes += r
                                    }
                                } else {
                                    targetFile.parentFile?.let { parent ->
                                        if (!parent.exists()) parent.mkdirs()
                                    }
                                    val tmpFile = File(targetFile.parentFile, "${targetFile.name}.restore_tmp")
                                    try {
                                        tmpFile.outputStream().use { out ->
                                            while (true) {
                                                val r = zis.read(buffer)
                                                if (r <= 0) break
                                                out.write(buffer, 0, r)
                                                processedBytes += r
                                            }
                                        }
                                        if (targetFile.exists()) targetFile.delete()
                                        if (!tmpFile.renameTo(targetFile)) {
                                            tmpFile.copyTo(targetFile, overwrite = true)
                                            tmpFile.delete()
                                        }
                                        restoredMediaCount++
                                    } catch (ioEx: IOException) {
                                        tmpFile.delete()
                                        if (isOutOfSpaceError(ioEx)) {
                                            throw BackupInsufficientSpaceException(
                                                "Ruang penyimpanan HP penuh saat memulihkan media. Kosongkan sebagian memori lalu coba lagi."
                                            )
                                        }
                                        throw ioEx
                                    }
                                }
                            }
                            else -> {
                                while (zis.read(buffer) > 0) {
                                    // drain
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: BackupCorruptException) {
            throw e
        } catch (e: BackupInsufficientSpaceException) {
            throw e
        } catch (e: ZipException) {
            throw BackupCorruptException("File cadangan ZIP rusak saat diekstrak (${e.localizedMessage ?: "ZIP error"}).", e)
        } catch (e: IOException) {
            if (isOutOfSpaceError(e)) {
                throw BackupInsufficientSpaceException(
                    "Ruang penyimpanan HP penuh saat memulihkan cadangan."
                )
            }
            throw BackupCorruptException("Gagal mengekstrak file cadangan karena arsip rusak atau terpotong.", e)
        }

        val validManifest = manifest
            ?: throw BackupCorruptException("Manifest cadangan tidak ditemukan.")
        val validData = dataPayload
            ?: throw BackupCorruptException("Data cadangan tidak ditemukan.")

        val newKunjungan = validData.kunjunganList
            .filter { it.id !in existingKunjunganIds }
            .map {
                it.copy(
                    tripId = Trip.PERSONAL_TRIP_ID,
                    remoteDocId = null,
                    remoteTripId = null
                )
            }
        val skippedKunjunganCount = validData.kunjunganList.size - newKunjungan.size

        val validKunjunganIds = existingKunjunganIds + newKunjungan.map { it.id }
        val restoredFotoEntities = ArrayList<Foto>()
        var skippedFotoCount = 0

        validData.fotoList.forEach { entry ->
            if (entry.id in existingFotoIds) {
                skippedFotoCount++
            } else if (entry.kunjunganId in validKunjunganIds) {
                val safeRel = sanitizeZipEntryPath(entry.relativePath)
                val targetFile = File(filesDir, safeRel)
                val canonicalOk = targetFile.canonicalPath.startsWith(baseCanonicalPath + File.separator)
                if (canonicalOk) {
                    restoredFotoEntities.add(
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

        onProgress(
            BackupProgress(
                isImport = true,
                stepLabel = "Menyimpan database & pengaturan...",
                processedItems = totalItems,
                totalItems = totalItems,
                processedBytes = expectedTotalBytes,
                totalBytes = expectedTotalBytes
            )
        )

        return BackupParsedRestore(
            manifest = validManifest,
            newKunjungan = newKunjungan,
            skippedKunjunganCount = skippedKunjunganCount,
            newFotos = restoredFotoEntities,
            skippedFotoCount = skippedFotoCount,
            restoredMediaFilesCount = restoredMediaCount,
            restoredSettings = validData.settings
        )
    }

    private fun collectMediaEntries(
        filesDir: File,
        personalFotos: List<Foto>,
        includeVideos: Boolean
    ): List<PlannedMediaEntry> {
        val entriesByZipPath = LinkedHashMap<String, PlannedMediaEntry>()
        val fotosRoot = File(filesDir, "fotos")
        if (fotosRoot.exists() && fotosRoot.isDirectory) {
            fotosRoot.walkTopDown()
                .filter { it.isFile && it.length() > 0L }
                .sortedBy { it.invariantSeparatorsPath }
                .forEach { file ->
                    val relUnderFotos = file.relativeTo(fotosRoot).invariantSeparatorsPath
                    val zipPath = "$FOTOS_PREFIX$relUnderFotos"
                    val isVideo = isVideoFileName(file.name)
                    if (includeVideos || !isVideo) {
                        entriesByZipPath[zipPath] = PlannedMediaEntry(
                            sourceFile = file,
                            relativeZipPath = zipPath,
                            isVideo = isVideo,
                            byteSize = file.length()
                        )
                    }
                }
        }

        // Also ensure any personal Foto records pointing outside filesDir/fotos/ are included cleanly
        personalFotos.forEach { foto ->
            if (includeVideos || !foto.isVideo) {
                val file = File(foto.filePath)
                if (file.exists() && file.isFile && file.length() > 0L) {
                    val zipPath = resolveRelativeZipPath(filesDir, foto)
                    if (!entriesByZipPath.containsKey(zipPath)) {
                        entriesByZipPath[zipPath] = PlannedMediaEntry(
                            sourceFile = file,
                            relativeZipPath = zipPath,
                            isVideo = foto.isVideo,
                            byteSize = file.length()
                        )
                    }
                }
            }
        }

        return entriesByZipPath.values.toList()
    }

    private fun resolveRelativeZipPath(filesDir: File, foto: Foto): String {
        val file = File(foto.filePath)
        val fotosDir = File(filesDir, "fotos")
        val fotosCanonical = runCatching { fotosDir.canonicalPath }.getOrElse { fotosDir.absolutePath }
        val fileCanonical = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (fileCanonical.startsWith(fotosCanonical + File.separator)) {
            val rel = fileCanonical.removePrefix(fotosCanonical + File.separator).replace(File.separatorChar, '/')
            return "$FOTOS_PREFIX$rel"
        }
        val ext = file.name.substringAfterLast('.', if (foto.isVideo) "mp4" else "jpg")
        val safeFileName = file.name.ifBlank { "${foto.id}.$ext" }
        return "$FOTOS_PREFIX${Trip.PERSONAL_TRIP_ID}/${foto.kunjunganId}/$safeFileName"
    }

    private fun countPersonalMemories(
        personalKunjungan: List<Kunjungan>,
        personalFotos: List<Foto>,
        todayEpochDay: Long
    ): Int {
        val visitIdsWithMedia = personalFotos.map { it.kunjunganId }.toSet()
        return personalKunjungan.count { k ->
            k.visitDateEpochDay < todayEpochDay ||
                k.isCompletedManually ||
                k.memoryNotes.isNotBlank() ||
                k.id in visitIdsWithMedia
        }
    }

    fun encodeManifestJson(manifest: BackupManifest): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val root = linkedMapOf<String, Any?>(
            "app" to manifest.app,
            "backupVersion" to manifest.backupVersion,
            "exportedAtMillis" to manifest.exportedAtMillis,
            "exportedAtIso" to isoFormat.format(Date(manifest.exportedAtMillis)),
            "includesVideos" to manifest.includesVideos,
            "kunjunganCount" to manifest.kunjunganCount,
            "memoryCount" to manifest.memoryCount,
            "photoCount" to manifest.photoCount,
            "videoCount" to manifest.videoCount,
            "mediaFileCount" to manifest.mediaFileCount,
            "totalMediaBytes" to manifest.totalMediaBytes
        )
        return BackupJsonCodec.encodePretty(root)
    }

    fun parseManifestJson(json: String): BackupManifest {
        val obj = BackupJsonCodec.decodeObject(json)
        val app = obj["app"] as? String
            ?: throw BackupCorruptException("Manifest tidak memiliki identitas aplikasi yang valid.")
        if (app != BACKUP_APP_NAME) {
            throw BackupCorruptException("File cadangan ini bukan berasal dari TripBuddy.")
        }
        val version = (obj["backupVersion"] as? Number)?.toInt()
            ?: throw BackupCorruptException("Versi cadangan di manifest tidak ditemukan.")
        if (version < 1) {
            throw BackupCorruptException("Versi cadangan tidak valid ($version).")
        }
        if (version > CURRENT_BACKUP_VERSION) {
            throw BackupCorruptException(
                "Cadangan dibuat oleh versi TripBuddy yang lebih baru (v$version). Perbarui aplikasi terlebih dahulu."
            )
        }
        val exportedAtMillis = (obj["exportedAtMillis"] as? Number)?.toLong() ?: 0L
        val includesVideos = obj["includesVideos"] as? Boolean ?: true
        val kunjunganCount = (obj["kunjunganCount"] as? Number)?.toInt() ?: 0
        val memoryCount = (obj["memoryCount"] as? Number)?.toInt() ?: 0
        val photoCount = (obj["photoCount"] as? Number)?.toInt() ?: 0
        val videoCount = (obj["videoCount"] as? Number)?.toInt() ?: 0
        val mediaFileCount = (obj["mediaFileCount"] as? Number)?.toInt() ?: (photoCount + videoCount)
        val totalMediaBytes = (obj["totalMediaBytes"] as? Number)?.toLong() ?: 0L

        return BackupManifest(
            app = app,
            backupVersion = version,
            exportedAtMillis = exportedAtMillis,
            includesVideos = includesVideos,
            kunjunganCount = kunjunganCount,
            memoryCount = memoryCount,
            photoCount = photoCount,
            videoCount = videoCount,
            mediaFileCount = mediaFileCount,
            totalMediaBytes = totalMediaBytes
        )
    }

    fun encodeDataJson(payload: BackupDataPayload): String {
        val root = linkedMapOf<String, Any?>(
            "backupVersion" to payload.backupVersion,
            "personalTrip" to linkedMapOf(
                "id" to payload.personalTrip.id,
                "name" to payload.personalTrip.name,
                "isPersonal" to payload.personalTrip.isPersonal,
                "createdAtMillis" to payload.personalTrip.createdAtMillis
            ),
            "kunjungan" to payload.kunjunganList.map { k ->
                linkedMapOf(
                    "id" to k.id,
                    "tripId" to Trip.PERSONAL_TRIP_ID,
                    "locationName" to k.locationName,
                    "tripTitle" to k.tripTitle,
                    "visitDateEpochDay" to k.visitDateEpochDay,
                    "visitHour" to k.visitHour,
                    "visitMinute" to k.visitMinute,
                    "planNotes" to k.planNotes,
                    "memoryNotes" to k.memoryNotes,
                    "isCompletedManually" to k.isCompletedManually,
                    "createdByName" to k.createdByName,
                    "createdAtMillis" to k.createdAtMillis
                )
            },
            "foto" to payload.fotoList.map { f ->
                linkedMapOf(
                    "id" to f.id,
                    "kunjunganId" to f.kunjunganId,
                    "tripId" to Trip.PERSONAL_TRIP_ID,
                    "relativePath" to f.relativePath,
                    "mediaStoreUri" to f.mediaStoreUri,
                    "createdAtMillis" to f.createdAtMillis,
                    "mediaType" to f.mediaType
                )
            },
            "settings" to linkedMapOf(
                "themeId" to payload.settings.themeId,
                "remindersEnabled" to payload.settings.remindersEnabled,
                "selectedTimingIds" to payload.settings.selectedTimingIds,
                "annualMemoryReminderEnabled" to payload.settings.annualMemoryReminderEnabled
            )
        )
        return BackupJsonCodec.encodePretty(root)
    }

    @Suppress("UNCHECKED_CAST")
    fun parseDataJson(json: String): BackupDataPayload {
        val obj = BackupJsonCodec.decodeObject(json)
        val version = (obj["backupVersion"] as? Number)?.toInt() ?: CURRENT_BACKUP_VERSION

        val tripObj = obj["personalTrip"] as? Map<String, Any?>
        val personalTrip = Trip(
            id = Trip.PERSONAL_TRIP_ID,
            name = (tripObj?.get("name") as? String)?.ifBlank { "Pribadi" } ?: "Pribadi",
            isPersonal = true,
            remoteTripId = null,
            createdAtMillis = (tripObj?.get("createdAtMillis") as? Number)?.toLong() ?: 0L
        )

        val rawKunjungan = obj["kunjungan"] as? List<*>
            ?: throw BackupCorruptException("Daftar kunjungan tidak ditemukan di data.json.")
        val kunjunganList = rawKunjungan.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: throw BackupCorruptException("Entri kunjungan rusak.")
            val id = (m["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw BackupCorruptException("Entri kunjungan tidak memiliki ID.")
            val locationName = (m["locationName"] as? String) ?: ""
            val visitDateEpochDay = (m["visitDateEpochDay"] as? Number)?.toLong()
                ?: throw BackupCorruptException("Tanggal kunjungan tidak valid untuk '$locationName'.")
            Kunjungan(
                id = id,
                tripId = Trip.PERSONAL_TRIP_ID,
                locationName = locationName,
                tripTitle = (m["tripTitle"] as? String) ?: "",
                visitDateEpochDay = visitDateEpochDay,
                visitHour = (m["visitHour"] as? Number)?.toInt() ?: 9,
                visitMinute = (m["visitMinute"] as? Number)?.toInt() ?: 0,
                planNotes = (m["planNotes"] as? String) ?: "",
                memoryNotes = (m["memoryNotes"] as? String) ?: "",
                isCompletedManually = (m["isCompletedManually"] as? Boolean) ?: false,
                createdByName = (m["createdByName"] as? String) ?: "",
                remoteDocId = null,
                remoteTripId = null,
                createdAtMillis = (m["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }

        val rawFotos = obj["foto"] as? List<*>
            ?: throw BackupCorruptException("Daftar foto tidak ditemukan di data.json.")
        val fotoList = rawFotos.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: throw BackupCorruptException("Entri foto rusak.")
            val id = (m["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw BackupCorruptException("Entri foto tidak memiliki ID.")
            val kunjunganId = (m["kunjunganId"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw BackupCorruptException("Entri foto tidak memiliki kunjunganId.")
            val relativePath = (m["relativePath"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw BackupCorruptException("Entri foto tidak memiliki jalur file.")
            BackupFotoPayload(
                id = id,
                kunjunganId = kunjunganId,
                tripId = Trip.PERSONAL_TRIP_ID,
                relativePath = relativePath,
                mediaStoreUri = m["mediaStoreUri"] as? String,
                createdAtMillis = (m["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                mediaType = (m["mediaType"] as? String) ?: Foto.MEDIA_TYPE_PHOTO
            )
        }

        val settingsObj = obj["settings"] as? Map<String, Any?>
        val timingIds = (settingsObj?.get("selectedTimingIds") as? List<*>)
            ?.mapNotNull { it as? String }
            ?: ReminderTimingOption.DEFAULT_TIMINGS.map { it.id }

        val settings = BackupSettingsPayload(
            themeId = (settingsObj?.get("themeId") as? String) ?: AppThemeOption.SAMUDRA.id,
            remindersEnabled = (settingsObj?.get("remindersEnabled") as? Boolean) ?: true,
            selectedTimingIds = timingIds,
            annualMemoryReminderEnabled = (settingsObj?.get("annualMemoryReminderEnabled") as? Boolean) ?: true
        )

        return BackupDataPayload(
            backupVersion = version,
            personalTrip = personalTrip,
            kunjunganList = kunjunganList,
            fotoList = fotoList,
            settings = settings
        )
    }

    fun sanitizeZipEntryPath(rawName: String): String {
        val normalized = rawName.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty() ||
            normalized.contains('\u0000') ||
            normalized.split('/').any { it == ".." || it == "." }
        ) {
            throw BackupCorruptException("Nama file dalam arsip cadangan tidak valid atau tidak aman: $rawName")
        }
        return normalized
    }

    private fun readBoundedUtf8(zis: ZipInputStream, maxBytes: Int): String {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val read = zis.read(buf)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw BackupCorruptException("Ukuran file metadata cadangan melebihi batas aman.")
            }
            baos.write(buf, 0, read)
        }
        return baos.toString(Charsets.UTF_8.name())
    }

    private fun resolveUriDisplayName(context: Context, uri: Uri): String {
        var resolved: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) resolved = cursor.getString(idx)
                }
            }
        } catch (_: Exception) {
        }
        return resolved?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Cadangan_TripBuddy.zip"
    }

    private fun isOutOfSpaceError(err: Throwable): Boolean {
        val msg = err.message?.lowercase(Locale.US).orEmpty()
        return msg.contains("enospc") ||
            msg.contains("no space left") ||
            msg.contains("not enough space") ||
            msg.contains("disk full")
    }
}
