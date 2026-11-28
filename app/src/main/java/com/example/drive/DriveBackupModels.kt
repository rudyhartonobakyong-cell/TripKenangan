package com.example.drive

import com.example.util.BackupCorruptException
import com.example.util.BackupJsonCodec
import com.example.util.BackupManager
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DriveMediaManifestEntry(
    val relativePath: String,
    val driveFileId: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val sha256: String,
    val isVideo: Boolean
)

data class DriveBackupManifest(
    val app: String = BackupManager.BACKUP_APP_NAME,
    val backupVersion: Int = BackupManager.CURRENT_BACKUP_VERSION,
    val createdAtMillis: Long,
    val includesVideos: Boolean,
    val kunjunganCount: Int,
    val memoryCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val totalMediaBytes: Long,
    val dataFileId: String,
    val dataSha256: String,
    val mediaFiles: List<DriveMediaManifestEntry>
) {
    val mediaFileCount: Int get() = mediaFiles.size
}

data class DriveRemoteBackupSummary(
    val manifestFileId: String,
    val fileName: String,
    val manifest: DriveBackupManifest
)

data class DriveRestoreConfirmationSummary(
    val remoteSummary: DriveRemoteBackupSummary,
    val newKunjunganCount: Int,
    val existingKunjunganCount: Int,
    val newFotoCount: Int,
    val existingFotoCount: Int,
    val missingMediaFilesCount: Int,
    val missingMediaBytes: Long
)

data class DriveBackupProgress(
    val isRestore: Boolean,
    val stepLabel: String,
    val processedFiles: Int,
    val totalFiles: Int,
    val processedBytes: Long,
    val totalBytes: Long
) {
    val fraction: Float
        get() = when {
            totalBytes > 0L -> (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            totalFiles > 0 -> (processedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

data class LocalMediaFileCandidate(
    val file: File,
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val sha256: String,
    val isVideo: Boolean
)

data class IncrementalUploadPlan(
    val reusedEntries: List<DriveMediaManifestEntry>,
    val filesToUpload: List<LocalMediaFileCandidate>
)

data class RetentionCleanupPlan(
    val keptManifests: List<DriveRemoteBackupSummary>,
    val manifestIdsToDelete: List<String>,
    val dataFileIdsToDelete: List<String>,
    val mediaFileIdsToDelete: List<String>
)

object DriveBackupModels {
    const val MAX_MANIFEST_VERSIONS = 3
    const val MANIFEST_FILE_PREFIX = "tripbuddy_manifest_"
    const val DATA_FILE_PREFIX = "tripbuddy_data_"

    fun buildManifestFileName(timestampMillis: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
        return "$MANIFEST_FILE_PREFIX$stamp.json"
    }

    fun buildDataFileName(timestampMillis: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestampMillis))
        return "$DATA_FILE_PREFIX$stamp.json"
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    fun encodeDriveManifestJson(manifest: DriveBackupManifest): String {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val root = linkedMapOf<String, Any?>(
            "app" to manifest.app,
            "backupVersion" to manifest.backupVersion,
            "createdAtMillis" to manifest.createdAtMillis,
            "createdAtIso" to isoFormat.format(Date(manifest.createdAtMillis)),
            "includesVideos" to manifest.includesVideos,
            "kunjunganCount" to manifest.kunjunganCount,
            "memoryCount" to manifest.memoryCount,
            "photoCount" to manifest.photoCount,
            "videoCount" to manifest.videoCount,
            "totalMediaBytes" to manifest.totalMediaBytes,
            "dataFileId" to manifest.dataFileId,
            "dataSha256" to manifest.dataSha256,
            "mediaFiles" to manifest.mediaFiles.map { entry ->
                linkedMapOf(
                    "relativePath" to entry.relativePath,
                    "driveFileId" to entry.driveFileId,
                    "sizeBytes" to entry.sizeBytes,
                    "lastModifiedMillis" to entry.lastModifiedMillis,
                    "sha256" to entry.sha256,
                    "isVideo" to entry.isVideo
                )
            }
        )
        return BackupJsonCodec.encodePretty(root)
    }

    fun parseDriveManifestJson(json: String): DriveBackupManifest {
        val obj = BackupJsonCodec.decodeObject(json)
        val app = obj["app"] as? String
            ?: throw BackupCorruptException("Manifest Drive tidak memiliki identitas aplikasi.")
        if (app != BackupManager.BACKUP_APP_NAME) {
            throw BackupCorruptException("Manifest Drive ini bukan milik TripBuddy.")
        }
        val version = (obj["backupVersion"] as? Number)?.toInt()
            ?: throw BackupCorruptException("Versi cadangan pada manifest Drive tidak valid.")
        if (version < 1 || version > BackupManager.CURRENT_BACKUP_VERSION) {
            throw BackupCorruptException("Versi cadangan Drive ($version) tidak didukung.")
        }
        val createdAtMillis = (obj["createdAtMillis"] as? Number)?.toLong() ?: 0L
        val includesVideos = obj["includesVideos"] as? Boolean ?: true
        val kunjunganCount = (obj["kunjunganCount"] as? Number)?.toInt() ?: 0
        val memoryCount = (obj["memoryCount"] as? Number)?.toInt() ?: 0
        val photoCount = (obj["photoCount"] as? Number)?.toInt() ?: 0
        val videoCount = (obj["videoCount"] as? Number)?.toInt() ?: 0
        val totalMediaBytes = (obj["totalMediaBytes"] as? Number)?.toLong() ?: 0L
        val dataFileId = (obj["dataFileId"] as? String).orEmpty()
        val dataSha256 = (obj["dataSha256"] as? String).orEmpty()

        val rawMedia = obj["mediaFiles"] as? List<*> ?: emptyList<Any?>()
        val mediaFiles = rawMedia.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val rel = (m["relativePath"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val fileId = (m["driveFileId"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val size = (m["sizeBytes"] as? Number)?.toLong() ?: 0L
            val mod = (m["lastModifiedMillis"] as? Number)?.toLong() ?: 0L
            val sha = (m["sha256"] as? String).orEmpty()
            val isVid = (m["isVideo"] as? Boolean) ?: BackupManager.isVideoFileName(rel)
            DriveMediaManifestEntry(
                relativePath = BackupManager.sanitizeZipEntryPath(rel),
                driveFileId = fileId,
                sizeBytes = size,
                lastModifiedMillis = mod,
                sha256 = sha,
                isVideo = isVid
            )
        }

        return DriveBackupManifest(
            app = app,
            backupVersion = version,
            createdAtMillis = createdAtMillis,
            includesVideos = includesVideos,
            kunjunganCount = kunjunganCount,
            memoryCount = memoryCount,
            photoCount = photoCount,
            videoCount = videoCount,
            totalMediaBytes = totalMediaBytes,
            dataFileId = dataFileId,
            dataSha256 = dataSha256,
            mediaFiles = mediaFiles
        )
    }

    fun planIncrementalMediaUpload(
        localFiles: List<LocalMediaFileCandidate>,
        previousManifest: DriveBackupManifest?,
        existingRemoteMediaFileIds: Set<String>
    ): IncrementalUploadPlan {
        val prevByPath = previousManifest?.mediaFiles?.associateBy { it.relativePath }.orEmpty()
        val reused = ArrayList<DriveMediaManifestEntry>()
        val toUpload = ArrayList<LocalMediaFileCandidate>()

        localFiles.forEach { local ->
            val prev = prevByPath[local.relativePath]
            val matchesUnchanged = prev != null &&
                prev.sha256 == local.sha256 &&
                prev.sizeBytes == local.sizeBytes &&
                prev.driveFileId in existingRemoteMediaFileIds
            if (matchesUnchanged && prev != null) {
                reused.add(prev)
            } else {
                toUpload.add(local)
            }
        }
        return IncrementalUploadPlan(
            reusedEntries = reused,
            filesToUpload = toUpload
        )
    }

    fun selectRetentionDeletions(
        manifestsNewestFirst: List<DriveRemoteBackupSummary>,
        allRemoteMediaFileIds: Set<String>,
        allRemoteDataFileIds: Set<String>,
        maxVersions: Int = MAX_MANIFEST_VERSIONS
    ): RetentionCleanupPlan {
        val kept = manifestsNewestFirst.take(maxVersions)
        val expired = manifestsNewestFirst.drop(maxVersions)

        val keptDataIds = kept.mapNotNull { it.manifest.dataFileId.takeIf(String::isNotBlank) }.toSet()
        val keptMediaIds = kept.flatMap { summary ->
            summary.manifest.mediaFiles.map { it.driveFileId }
        }.toSet()

        val manifestIdsToDelete = expired.map { it.manifestFileId }
        val expiredDataIds = expired.mapNotNull { it.manifest.dataFileId.takeIf(String::isNotBlank) }
        val dataIdsToDelete = (allRemoteDataFileIds + expiredDataIds)
            .filter { it.isNotBlank() && it !in keptDataIds }
            .distinct()
        val mediaIdsToDelete = allRemoteMediaFileIds
            .filter { it.isNotBlank() && it !in keptMediaIds }
            .distinct()

        return RetentionCleanupPlan(
            keptManifests = kept,
            manifestIdsToDelete = manifestIdsToDelete,
            dataFileIdsToDelete = dataIdsToDelete,
            mediaFileIdsToDelete = mediaIdsToDelete
        )
    }
}
