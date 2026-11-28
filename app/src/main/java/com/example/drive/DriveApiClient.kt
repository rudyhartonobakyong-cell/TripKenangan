package com.example.drive

import com.example.util.BackupJsonCodec
import com.example.util.BackupManager
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DriveRemoteFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long,
    val appProperties: Map<String, String> = emptyMap()
)

data class DriveUserProfile(
    val email: String,
    val displayName: String
)

class DriveApiException(
    val httpCode: Int?,
    val isAuthError: Boolean,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)

object DriveApiClient {
    const val DRIVE_BACKUP_FOLDER_NAME = "TripBuddy Backup"
    const val DRIVE_MEDIA_FOLDER_NAME = "media"

    private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
    private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/files"
    private const val FOLDER_MIME = "application/vnd.google-apps.folder"
    private const val JSON_MIME = "application/json"
    private const val RESUMABLE_CHUNK_BYTES = 1024 * 1024 // 1 MB (multiple of 256 KB)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun fetchUserAbout(accessToken: String): DriveUserProfile {
        val url = "$DRIVE_API_BASE/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(emailAddress,displayName)")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        executeOrThrow(req) { body ->
            val root = BackupJsonCodec.decodeObject(body)
            val userObj = root["user"] as? Map<*, *>
            DriveUserProfile(
                email = (userObj?.get("emailAddress") as? String).orEmpty(),
                displayName = (userObj?.get("displayName") as? String).orEmpty()
            )
        }.let { return it }
    }

    fun findFolder(
        accessToken: String,
        folderName: String,
        parentId: String? = null
    ): String? {
        val parentClause = if (parentId.isNullOrBlank()) "'root' in parents" else "'${escapeDriveQuery(parentId)}' in parents"
        val query = "name = '${escapeDriveQuery(folderName)}' and mimeType = '$FOLDER_MIME' and $parentClause and trashed = false"
        val url = "$DRIVE_API_BASE/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "10")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return executeOrThrow(req) { body ->
            val obj = BackupJsonCodec.decodeObject(body)
            val files = obj["files"] as? List<*> ?: emptyList<Any?>()
            val first = files.firstOrNull() as? Map<*, *>
            first?.get("id") as? String
        }
    }

    fun findOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentId: String? = null
    ): String {
        val existingId = findFolder(accessToken, folderName, parentId)
        if (!existingId.isNullOrBlank()) return existingId

        val meta = linkedMapOf<String, Any?>(
            "name" to folderName,
            "mimeType" to FOLDER_MIME
        )
        if (!parentId.isNullOrBlank()) {
            meta["parents"] = listOf(parentId)
        }
        val jsonBody = BackupJsonCodec.encodePretty(meta)
        val req = Request.Builder()
            .url("$DRIVE_API_BASE/files?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(jsonBody.toRequestBody("$JSON_MIME; charset=utf-8".toMediaType()))
            .build()

        return executeOrThrow(req) { body ->
            val obj = BackupJsonCodec.decodeObject(body)
            (obj["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw DriveApiException(null, false, "Drive tidak mengembalikan ID folder $folderName.")
        }
    }

    fun listFilesInFolder(
        accessToken: String,
        folderId: String
    ): List<DriveRemoteFile> {
        val result = ArrayList<DriveRemoteFile>()
        var pageToken: String? = null
        do {
            val urlBuilder = "$DRIVE_API_BASE/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", "'${escapeDriveQuery(folderId)}' in parents and trashed = false")
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType,size,createdTime,appProperties)")
                .addQueryParameter("pageSize", "200")
            if (!pageToken.isNullOrBlank()) {
                urlBuilder.addQueryParameter("pageToken", pageToken)
            }
            val req = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val next = executeOrThrow(req) { body ->
                val obj = BackupJsonCodec.decodeObject(body)
                val files = obj["files"] as? List<*> ?: emptyList<Any?>()
                files.forEach { raw ->
                    val m = raw as? Map<*, *> ?: return@forEach
                    val id = (m["id"] as? String)?.takeIf { it.isNotBlank() } ?: return@forEach
                    val name = (m["name"] as? String).orEmpty()
                    val mime = (m["mimeType"] as? String).orEmpty()
                    val size = parseFlexibleLong(m["size"])
                    val appPropsRaw = m["appProperties"] as? Map<*, *>
                    val appProps = LinkedHashMap<String, String>()
                    appPropsRaw?.forEach { (k, v) ->
                        if (k is String && v is String) appProps[k] = v
                    }
                    result.add(
                        DriveRemoteFile(
                            id = id,
                            name = name,
                            mimeType = mime,
                            sizeBytes = size,
                            createdTimeMillis = 0L,
                            appProperties = appProps
                        )
                    )
                }
                obj["nextPageToken"] as? String
            }
            pageToken = next
        } while (!pageToken.isNullOrBlank())
        return result
    }

    fun downloadTextFile(
        accessToken: String,
        fileId: String
    ): String {
        val req = Request.Builder()
            .url("$DRIVE_API_BASE/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return executeOrThrow(req) { body -> body }
    }

    fun downloadMediaFileResumable(
        accessToken: String,
        fileId: String,
        destFile: File,
        expectedSizeBytes: Long,
        onBytesDelta: (Long) -> Unit = {}
    ) {
        destFile.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        val partFile = File(destFile.parentFile, "${destFile.name}.drive_dl_part")
        val existingOffset = if (partFile.exists()) partFile.length() else 0L

        if (expectedSizeBytes > 0L && existingOffset >= expectedSizeBytes) {
            finalizeTempFile(partFile, destFile)
            return
        }

        val reqBuilder = Request.Builder()
            .url("$DRIVE_API_BASE/files/$fileId?alt=media")
            .header("Authorization", "Bearer $accessToken")
            .get()
        if (existingOffset > 0L) {
            reqBuilder.header("Range", "bytes=$existingOffset-")
        }

        try {
            client.newCall(reqBuilder.build()).execute().use { response ->
                when (response.code) {
                    200, 206 -> {
                        val appendMode = response.code == 206 && existingOffset > 0L
                        val bodyStream = response.body?.byteStream()
                            ?: throw DriveApiException(response.code, false, "Respons unduhan kosong dari Google Drive.")
                        bodyStream.use { input ->
                            java.io.FileOutputStream(partFile, appendMode).use { out ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val read = input.read(buf)
                                    if (read <= 0) break
                                    out.write(buf, 0, read)
                                    onBytesDelta(read.toLong())
                                }
                            }
                        }
                        finalizeTempFile(partFile, destFile)
                    }
                    416 -> {
                        // Requested range not satisfiable -> partial file already complete or reset
                        if (expectedSizeBytes > 0L && partFile.exists() && partFile.length() >= expectedSizeBytes) {
                            finalizeTempFile(partFile, destFile)
                        } else {
                            partFile.delete()
                            throw DriveApiException(416, false, "Bagian unduhan tidak cocok, silakan ulangi pemulihan.")
                        }
                    }
                    else -> {
                        throw buildHttpException(response)
                    }
                }
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(null, false, mapIoExceptionMessage(e), e)
        }
    }

    fun uploadJsonMultipart(
        accessToken: String,
        parentFolderId: String,
        fileName: String,
        jsonText: String,
        appProperties: Map<String, String> = emptyMap()
    ): String {
        val boundary = "tripbuddy_boundary_${System.currentTimeMillis()}"
        val metadataMap = linkedMapOf<String, Any?>(
            "name" to fileName,
            "parents" to listOf(parentFolderId),
            "mimeType" to JSON_MIME
        )
        if (appProperties.isNotEmpty()) {
            metadataMap["appProperties"] = appProperties
        }
        val metadataJson = BackupJsonCodec.encodePretty(metadataMap)

        val bodyString = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(jsonText).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }

        val req = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyString.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        return executeOrThrow(req) { responseBody ->
            val obj = BackupJsonCodec.decodeObject(responseBody)
            (obj["id"] as? String)?.takeIf { it.isNotBlank() }
                ?: throw DriveApiException(null, false, "Google Drive tidak mengembalikan ID file untuk $fileName.")
        }
    }

    fun uploadMediaResumable(
        accessToken: String,
        mediaFolderId: String,
        file: File,
        relativePath: String,
        sha256: String,
        existingSession: ResumableUploadSession?,
        onSessionChanged: (ResumableUploadSession?) -> Unit,
        onBytesDelta: (Long) -> Unit = {}
    ): String {
        val totalSize = file.length()
        val mimeType = inferMediaMimeType(file.name)

        var sessionUri: String? = null
        var offset = 0L

        if (existingSession != null &&
            existingSession.sha256 == sha256 &&
            existingSession.fileSize == totalSize
        ) {
            when (val probe = probeResumableSession(accessToken, existingSession.sessionUri, totalSize)) {
                is SessionProbeResult.Completed -> {
                    onSessionChanged(null)
                    return probe.fileId
                }
                is SessionProbeResult.ResumeFrom -> {
                    sessionUri = existingSession.sessionUri
                    offset = probe.nextOffset
                    if (offset > 0L) {
                        onBytesDelta(offset.coerceAtMost(totalSize))
                    }
                }
                SessionProbeResult.Expired -> {
                    onSessionChanged(null)
                }
            }
        }

        if (sessionUri == null) {
            sessionUri = initiateResumableSession(
                accessToken = accessToken,
                parentFolderId = mediaFolderId,
                fileName = file.name,
                mimeType = mimeType,
                totalSize = totalSize,
                relativePath = relativePath,
                sha256 = sha256
            )
            onSessionChanged(
                ResumableUploadSession(
                    relativePath = relativePath,
                    sha256 = sha256,
                    fileSize = totalSize,
                    sessionUri = sessionUri,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            offset = 0L
        }

        val buffer = ByteArray(RESUMABLE_CHUNK_BYTES)
        RandomAccessFile(file, "r").use { raf ->
            while (offset < totalSize) {
                raf.seek(offset)
                val remaining = (totalSize - offset).coerceAtMost(RESUMABLE_CHUNK_BYTES.toLong()).toInt()
                var totalReadInChunk = 0
                while (totalReadInChunk < remaining) {
                    val read = raf.read(buffer, totalReadInChunk, remaining - totalReadInChunk)
                    if (read <= 0) break
                    totalReadInChunk += read
                }
                if (totalReadInChunk <= 0) {
                    throw DriveApiException(null, false, "Gagal membaca file ${file.name} untuk diunggah.")
                }

                val endInclusive = offset + totalReadInChunk - 1L
                val chunkBody = buffer.toRequestBody(mimeType.toMediaType(), 0, totalReadInChunk)
                val putReq = Request.Builder()
                    .url(sessionUri)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Range", "bytes $offset-$endInclusive/$totalSize")
                    .put(chunkBody)
                    .build()

                try {
                    client.newCall(putReq).execute().use { response ->
                        when (response.code) {
                            200, 201 -> {
                                onBytesDelta(totalReadInChunk.toLong())
                                val bodyText = response.body?.string().orEmpty()
                                val uploadedId = parseDriveFileIdFromJson(bodyText)
                                    ?: throw DriveApiException(response.code, false, "Drive tidak mengembalikan ID file ${file.name}.")
                                onSessionChanged(null)
                                return uploadedId
                            }
                            308 -> {
                                val confirmedNext = parseRangeHeaderNextOffset(response.header("Range"))
                                    ?: (offset + totalReadInChunk)
                                val delta = (confirmedNext - offset).coerceAtLeast(0L)
                                if (delta > 0L) onBytesDelta(delta)
                                offset = confirmedNext
                            }
                            404, 410 -> {
                                onSessionChanged(null)
                                throw DriveApiException(
                                    response.code,
                                    false,
                                    "Sesi unggah ${file.name} kedaluwarsa. Coba jalankan cadangan lagi."
                                )
                            }
                            else -> throw buildHttpException(response)
                        }
                    }
                } catch (e: DriveApiException) {
                    throw e
                } catch (e: IOException) {
                    throw DriveApiException(null, false, mapIoExceptionMessage(e), e)
                }
            }
        }

        throw DriveApiException(null, false, "Unggahan file ${file.name} berhenti sebelum selesai.")
    }

    fun deleteFileQuietly(accessToken: String, fileId: String) {
        if (fileId.isBlank()) return
        runCatching {
            val req = Request.Builder()
                .url("$DRIVE_API_BASE/files/$fileId")
                .header("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            client.newCall(req).execute().use { }
        }
    }

    private sealed interface SessionProbeResult {
        data class Completed(val fileId: String) : SessionProbeResult
        data class ResumeFrom(val nextOffset: Long) : SessionProbeResult
        data object Expired : SessionProbeResult
    }

    private fun probeResumableSession(
        accessToken: String,
        sessionUri: String,
        totalSize: Long
    ): SessionProbeResult {
        val emptyBody = ByteArray(0).toRequestBody(null, 0, 0)
        val req = Request.Builder()
            .url(sessionUri)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Range", "bytes */$totalSize")
            .put(emptyBody)
            .build()

        return try {
            client.newCall(req).execute().use { response ->
                when (response.code) {
                    200, 201 -> {
                        val id = parseDriveFileIdFromJson(response.body?.string().orEmpty())
                        if (id != null) SessionProbeResult.Completed(id) else SessionProbeResult.Expired
                    }
                    308 -> {
                        val next = parseRangeHeaderNextOffset(response.header("Range")) ?: 0L
                        SessionProbeResult.ResumeFrom(next)
                    }
                    404, 410 -> SessionProbeResult.Expired
                    else -> throw buildHttpException(response)
                }
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(null, false, mapIoExceptionMessage(e), e)
        }
    }

    private fun initiateResumableSession(
        accessToken: String,
        parentFolderId: String,
        fileName: String,
        mimeType: String,
        totalSize: Long,
        relativePath: String,
        sha256: String
    ): String {
        val metadata = BackupJsonCodec.encodePretty(
            linkedMapOf(
                "name" to fileName,
                "parents" to listOf(parentFolderId),
                "appProperties" to linkedMapOf(
                    "relativePath" to relativePath.take(110),
                    "sha256" to sha256
                )
            )
        )

        val req = Request.Builder()
            .url("$DRIVE_UPLOAD_BASE?uploadType=resumable&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Type", mimeType)
            .header("X-Upload-Content-Length", totalSize.toString())
            .post(metadata.toRequestBody("$JSON_MIME; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw buildHttpException(response)
                }
                return response.header("Location")?.takeIf { it.isNotBlank() }
                    ?: throw DriveApiException(response.code, false, "Google Drive tidak mengirim URL sesi unggah.")
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(null, false, mapIoExceptionMessage(e), e)
        }
    }

    private inline fun <T> executeOrThrow(request: Request, parser: (String) -> T): T {
        try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw buildHttpExceptionWithBody(response.code, bodyText)
                }
                return parser(bodyText)
            }
        } catch (e: DriveApiException) {
            throw e
        } catch (e: IOException) {
            throw DriveApiException(null, false, mapIoExceptionMessage(e), e)
        }
    }

    private fun buildHttpException(response: Response): DriveApiException {
        val bodyText = runCatching { response.body?.string().orEmpty() }.getOrElse { "" }
        return buildHttpExceptionWithBody(response.code, bodyText)
    }

    private fun buildHttpExceptionWithBody(code: Int, bodyText: String): DriveApiException {
        val lower = bodyText.lowercase(Locale.US)
        return when {
            code == 401 -> DriveApiException(
                httpCode = 401,
                isAuthError = true,
                message = "Sesi izin Google Drive berakhir atau dicabut. Hubungkan ulang Google Drive di Pengaturan."
            )
            code == 403 && lower.contains("storagequotaexceeded") -> DriveApiException(
                httpCode = 403,
                isAuthError = false,
                message = "Penyimpanan Google Drive Anda penuh. Kosongkan ruang Drive lalu coba lagi."
            )
            code == 403 && (lower.contains("insufficient") || lower.contains("permission")) -> DriveApiException(
                httpCode = 403,
                isAuthError = true,
                message = "Izin Google Drive tidak mencukupi. Hubungkan ulang Google Drive dengan izin yang diminta."
            )
            code == 404 -> DriveApiException(
                httpCode = 404,
                isAuthError = false,
                message = "File atau folder cadangan di Google Drive tidak ditemukan."
            )
            code == 429 || code in 500..599 -> DriveApiException(
                httpCode = code,
                isAuthError = false,
                message = "Layanan Google Drive sedang sibuk atau sementara terganggu (kode $code)."
            )
            else -> DriveApiException(
                httpCode = code,
                isAuthError = false,
                message = "Permintaan Google Drive gagal (kode $code)."
            )
        }
    }

    private fun mapIoExceptionMessage(e: IOException): String {
        val msg = e.message?.lowercase(Locale.US).orEmpty()
        return when {
            msg.contains("unknownhost") || msg.contains("failed to connect") || msg.contains("network is unreachable") ->
                "Tidak ada koneksi internet ke Google Drive."
            msg.contains("timeout") ->
                "Koneksi ke Google Drive habis waktu. Unggahan besar akan dilanjutkan otomatis saat tersambung kembali."
            msg.contains("enospc") || msg.contains("no space left") ->
                "Ruang penyimpanan HP penuh saat memproses file cadangan."
            else -> "Gangguan jaringan saat menghubungi Google Drive: ${e.localizedMessage ?: "terputus"}."
        }
    }

    private fun parseDriveFileIdFromJson(jsonText: String): String? {
        if (jsonText.isBlank()) return null
        return runCatching {
            val obj = BackupJsonCodec.decodeObject(jsonText)
            (obj["id"] as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun parseRangeHeaderNextOffset(rangeHeader: String?): Long? {
        if (rangeHeader.isNullOrBlank()) return null
        // Format: bytes=0-1048575
        val dashPart = rangeHeader.substringAfter("-", "")
        val endInclusive = dashPart.trim().toLongOrNull() ?: return null
        return endInclusive + 1L
    }

    private fun parseFlexibleLong(raw: Any?): Long {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun escapeDriveQuery(value: String): String {
        return value.replace("\\", "\\\\").replace("'", "\\'")
    }

    private fun inferMediaMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.US)) {
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun finalizeTempFile(partFile: File, destFile: File) {
        if (destFile.exists()) destFile.delete()
        if (!partFile.renameTo(destFile)) {
            partFile.copyTo(destFile, overwrite = true)
            partFile.delete()
        }
    }
}
