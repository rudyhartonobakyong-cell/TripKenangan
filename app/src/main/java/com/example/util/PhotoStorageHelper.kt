package com.example.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.data.Foto
import java.io.File
import java.util.UUID

data class PersistedPhotoResult(
    val photoUuid: String,
    val privateFilePath: String,
    val mediaStoreUri: String?
)

object PhotoStorageHelper {

    private const val CAMERA_TEMP_DIR = "camera_temp"
    private const val PRIVATE_FOTOS_DIR = "fotos"
    private const val VIDEO_THUMBS_DIR = "video_thumbs"
    private const val GALLERY_ALBUM_NAME = "TripBuddy"

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasVideoPermissions(context: Context): Boolean {
        val hasCamera = hasCameraPermission(context)
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return hasCamera && hasAudio
    }

    fun createTempCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_TEMP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "capture_${UUID.randomUUID()}.jpg")
    }

    fun createTempVideoFile(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_TEMP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "capture_video_${UUID.randomUUID()}.mp4")
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun getVideoThumbnailFile(context: Context, mediaUuid: String): File {
        val dir = File(context.filesDir, VIDEO_THUMBS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "$mediaUuid.jpg")
    }

    fun generateAndCacheVideoThumbnail(
        context: Context,
        videoPath: String,
        mediaUuid: String
    ): File? {
        val videoFile = File(videoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) return null
        val thumbFile = getVideoThumbnailFile(context, mediaUuid)
        val retriever = MediaMetadataRetriever()
        var extracted: Bitmap? = null
        var scaled: Bitmap? = null
        return try {
            retriever.setDataSource(videoPath)
            extracted = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.getFrameAtTime(0L)
            val frame = extracted ?: return null
            val maxSide = 1280
            val w = frame.width
            val h = frame.height
            val targetBitmap = if (w > maxSide || h > maxSide) {
                val ratio = minOf(maxSide.toFloat() / w.toFloat(), maxSide.toFloat() / h.toFloat())
                val targetW = (w * ratio).toInt().coerceAtLeast(1)
                val targetH = (h * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, targetW, targetH, true).also {
                    scaled = it
                }
            } else {
                frame
            }
            thumbFile.outputStream().use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (thumbFile.exists() && thumbFile.length() > 0L) thumbFile else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            if (scaled != null && scaled !== extracted) {
                scaled.recycle()
            }
            extracted?.recycle()
        }
    }

    fun resolveDisplayThumbnailPath(context: Context, foto: Foto): String? {
        if (!foto.isVideo) {
            val file = File(foto.filePath)
            return if (file.exists() && file.length() > 0L) file.absolutePath else null
        }
        val cachedThumb = getVideoThumbnailFile(context, foto.id)
        if (cachedThumb.exists() && cachedThumb.length() > 0L) {
            return cachedThumb.absolutePath
        }
        return generateAndCacheVideoThumbnail(context, foto.filePath, foto.id)?.absolutePath
    }

    fun persistCapturedPhoto(
        context: Context,
        tempFile: File,
        tripId: String,
        kunjunganId: String
    ): PersistedPhotoResult {
        val photoUuid = UUID.randomUUID().toString()
        val destDir = File(context.filesDir, "$PRIVATE_FOTOS_DIR/$tripId/$kunjunganId")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val destFile = File(destDir, "$photoUuid.jpg")

        tempFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        deleteFileQuietly(tempFile.absolutePath)

        val galleryUri = saveCopyToGalleryAlbum(
            context = context,
            sourceFile = destFile,
            photoUuid = photoUuid
        )

        return PersistedPhotoResult(
            photoUuid = photoUuid,
            privateFilePath = destFile.absolutePath,
            mediaStoreUri = galleryUri
        )
    }

    fun persistCapturedVideo(
        context: Context,
        tempFile: File,
        tripId: String,
        kunjunganId: String
    ): PersistedPhotoResult {
        val videoUuid = UUID.randomUUID().toString()
        val destDir = File(context.filesDir, "$PRIVATE_FOTOS_DIR/$tripId/$kunjunganId")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val destFile = File(destDir, "$videoUuid.mp4")

        tempFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        deleteFileQuietly(tempFile.absolutePath)

        generateAndCacheVideoThumbnail(
            context = context,
            videoPath = destFile.absolutePath,
            mediaUuid = videoUuid
        )

        val galleryUri = saveVideoCopyToGalleryAlbum(
            context = context,
            sourceFile = destFile,
            videoUuid = videoUuid
        )

        return PersistedPhotoResult(
            photoUuid = videoUuid,
            privateFilePath = destFile.absolutePath,
            mediaStoreUri = galleryUri
        )
    }

    private fun saveCopyToGalleryAlbum(
        context: Context,
        sourceFile: File,
        photoUuid: String
    ): String? {
        return try {
            val resolver = context.contentResolver
            val displayName = "TripBuddy_${System.currentTimeMillis()}_${photoUuid.take(6)}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM_NAME"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(insertedUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                resolver.delete(insertedUri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
            }
            insertedUri.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun saveVideoCopyToGalleryAlbum(
        context: Context,
        sourceFile: File,
        videoUuid: String
    ): String? {
        return try {
            val resolver = context.contentResolver
            val displayName = "TripBuddy_VID_${System.currentTimeMillis()}_${videoUuid.take(6)}.mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/$GALLERY_ALBUM_NAME"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(insertedUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: run {
                resolver.delete(insertedUri, null, null)
                return null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publishValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, publishValues, null, null)
            }
            insertedUri.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun deleteMediaAndThumbnail(foto: Foto, filesDir: File?) {
        deleteFileQuietly(foto.filePath)
        if (foto.isVideo && filesDir != null) {
            val thumbFile = File(File(filesDir, VIDEO_THUMBS_DIR), "${foto.id}.jpg")
            deleteFileQuietly(thumbFile.absolutePath)
        }
    }

    fun deleteFileQuietly(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }
}
