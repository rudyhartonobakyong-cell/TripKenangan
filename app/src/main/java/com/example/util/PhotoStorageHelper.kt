package com.example.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    private const val GALLERY_ALBUM_NAME = "TripBuddy"

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createTempCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_TEMP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "capture_${UUID.randomUUID()}.jpg")
    }

    fun getUriForFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
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
