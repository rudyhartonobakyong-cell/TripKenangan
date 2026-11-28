package com.example.drive

import android.content.Context
import com.example.util.BackupJsonCodec
import java.util.Calendar

enum class DriveBackupFrequency(
    val id: String,
    val label: String,
    val intervalDays: Long
) {
    OFF("off", "Mati", 0L),
    DAILY("daily", "Harian", 1L),
    WEEKLY("weekly", "Mingguan", 7L),
    MONTHLY("monthly", "Bulanan", 30L);

    companion object {
        fun fromId(id: String?): DriveBackupFrequency {
            return entries.firstOrNull { it.id == id } ?: OFF
        }
    }
}

data class DriveBackupScheduleSettings(
    val frequency: DriveBackupFrequency = DriveBackupFrequency.OFF,
    val dayOfWeek: Int = Calendar.SUNDAY,
    val dayOfMonth: Int = 1,
    val hour: Int = 2,
    val minute: Int = 0,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = true,
    val includeVideos: Boolean = true
) {
    fun formattedTime(): String = String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    companion object {
        fun dayOfWeekLabel(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.MONDAY -> "Senin"
            Calendar.TUESDAY -> "Selasa"
            Calendar.WEDNESDAY -> "Rabu"
            Calendar.THURSDAY -> "Kamis"
            Calendar.FRIDAY -> "Jumat"
            Calendar.SATURDAY -> "Sabtu"
            else -> "Minggu"
        }

        val orderedDaysOfWeek: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }
}

data class DriveAccountConnection(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val accountName: String = "",
    val connectedAtMillis: Long = 0L
)

data class DriveBackupLastStatus(
    val lastAttemptAtMillis: Long = 0L,
    val lastSuccessAtMillis: Long = 0L,
    val lastBackupSizeBytes: Long = 0L,
    val lastResultSuccess: Boolean? = null,
    val lastErrorMessage: String = "",
    val consecutiveFailures: Int = 0,
    val lastUploadedFilesCount: Int = 0,
    val lastSkippedFilesCount: Int = 0
)

data class ResumableUploadSession(
    val relativePath: String,
    val sha256: String,
    val fileSize: Long,
    val sessionUri: String,
    val createdAtMillis: Long
)

object DriveBackupPreferences {
    private const val PREFS_NAME = "tripbuddy_drive_prefs"
    private const val KEY_ACTIVE_UID = "drive_active_uid"

    private fun uidKey(userId: String) = userId.ifBlank { "default" }

    private fun keyConnected(u: String) = "drive_connected_$u"
    private fun keyEmail(u: String) = "drive_email_$u"
    private fun keyName(u: String) = "drive_name_$u"
    private fun keyConnectedAt(u: String) = "drive_connected_at_$u"

    private fun keyFrequency(u: String) = "drive_freq_$u"
    private fun keyDayOfWeek(u: String) = "drive_dow_$u"
    private fun keyDayOfMonth(u: String) = "drive_dom_$u"
    private fun keyHour(u: String) = "drive_hour_$u"
    private fun keyMinute(u: String) = "drive_minute_$u"
    private fun keyWifiOnly(u: String) = "drive_wifi_only_$u"
    private fun keyChargingOnly(u: String) = "drive_charging_only_$u"
    private fun keyIncludeVideos(u: String) = "drive_include_videos_$u"

    private fun keyLastAttempt(u: String) = "drive_last_attempt_$u"
    private fun keyLastSuccess(u: String) = "drive_last_success_$u"
    private fun keyLastSize(u: String) = "drive_last_size_$u"
    private fun keyLastResult(u: String) = "drive_last_result_$u"
    private fun keyLastError(u: String) = "drive_last_error_$u"
    private fun keyConsecutiveFailures(u: String) = "drive_consecutive_failures_$u"
    private fun keyLastUploadedCount(u: String) = "drive_last_uploaded_$u"
    private fun keyLastSkippedCount(u: String) = "drive_last_skipped_$u"

    private fun keyResumableSessions(u: String) = "drive_resumable_sessions_$u"

    fun setActiveUserId(context: Context, userId: String) {
        if (userId.isBlank()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_UID, userId)
            .apply()
    }

    fun getActiveUserId(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_UID, null) ?: "default"
    }

    fun loadConnection(context: Context, userId: String): DriveAccountConnection {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DriveAccountConnection(
            isConnected = prefs.getBoolean(keyConnected(u), false),
            accountEmail = prefs.getString(keyEmail(u), "") ?: "",
            accountName = prefs.getString(keyName(u), "") ?: "",
            connectedAtMillis = prefs.getLong(keyConnectedAt(u), 0L)
        )
    }

    fun saveConnection(context: Context, userId: String, connection: DriveAccountConnection) {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_UID, u)
            .putBoolean(keyConnected(u), connection.isConnected)
            .putString(keyEmail(u), connection.accountEmail)
            .putString(keyName(u), connection.accountName)
            .putLong(keyConnectedAt(u), connection.connectedAtMillis)
            .apply()
    }

    fun loadScheduleSettings(context: Context, userId: String): DriveBackupScheduleSettings {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DriveBackupScheduleSettings(
            frequency = DriveBackupFrequency.fromId(prefs.getString(keyFrequency(u), null)),
            dayOfWeek = prefs.getInt(keyDayOfWeek(u), Calendar.SUNDAY),
            dayOfMonth = prefs.getInt(keyDayOfMonth(u), 1).coerceIn(1, 28),
            hour = prefs.getInt(keyHour(u), 2).coerceIn(0, 23),
            minute = prefs.getInt(keyMinute(u), 0).coerceIn(0, 59),
            wifiOnly = prefs.getBoolean(keyWifiOnly(u), true),
            chargingOnly = prefs.getBoolean(keyChargingOnly(u), true),
            includeVideos = prefs.getBoolean(keyIncludeVideos(u), true)
        )
    }

    fun saveScheduleSettings(context: Context, userId: String, settings: DriveBackupScheduleSettings) {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVE_UID, u)
            .putString(keyFrequency(u), settings.frequency.id)
            .putInt(keyDayOfWeek(u), settings.dayOfWeek)
            .putInt(keyDayOfMonth(u), settings.dayOfMonth.coerceIn(1, 28))
            .putInt(keyHour(u), settings.hour.coerceIn(0, 23))
            .putInt(keyMinute(u), settings.minute.coerceIn(0, 59))
            .putBoolean(keyWifiOnly(u), settings.wifiOnly)
            .putBoolean(keyChargingOnly(u), settings.chargingOnly)
            .putBoolean(keyIncludeVideos(u), settings.includeVideos)
            .apply()
    }

    fun loadLastStatus(context: Context, userId: String): DriveBackupLastStatus {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawResult = prefs.getInt(keyLastResult(u), -1)
        return DriveBackupLastStatus(
            lastAttemptAtMillis = prefs.getLong(keyLastAttempt(u), 0L),
            lastSuccessAtMillis = prefs.getLong(keyLastSuccess(u), 0L),
            lastBackupSizeBytes = prefs.getLong(keyLastSize(u), 0L),
            lastResultSuccess = when (rawResult) {
                1 -> true
                0 -> false
                else -> null
            },
            lastErrorMessage = prefs.getString(keyLastError(u), "") ?: "",
            consecutiveFailures = prefs.getInt(keyConsecutiveFailures(u), 0),
            lastUploadedFilesCount = prefs.getInt(keyLastUploadedCount(u), 0),
            lastSkippedFilesCount = prefs.getInt(keyLastSkippedCount(u), 0)
        )
    }

    fun saveLastStatus(context: Context, userId: String, status: DriveBackupLastStatus) {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encodedResult = when (status.lastResultSuccess) {
            true -> 1
            false -> 0
            null -> -1
        }
        prefs.edit()
            .putLong(keyLastAttempt(u), status.lastAttemptAtMillis)
            .putLong(keyLastSuccess(u), status.lastSuccessAtMillis)
            .putLong(keyLastSize(u), status.lastBackupSizeBytes)
            .putInt(keyLastResult(u), encodedResult)
            .putString(keyLastError(u), status.lastErrorMessage)
            .putInt(keyConsecutiveFailures(u), status.consecutiveFailures)
            .putInt(keyLastUploadedCount(u), status.lastUploadedFilesCount)
            .putInt(keyLastSkippedCount(u), status.lastSkippedFilesCount)
            .apply()
    }

    fun getResumableSession(
        context: Context,
        userId: String,
        relativePath: String,
        sha256: String,
        fileSize: Long
    ): ResumableUploadSession? {
        val all = loadResumableSessions(context, userId)
        val found = all[relativePath] ?: return null
        // Google Drive resumable upload URIs remain valid for up to ~7 days
        val maxAgeMillis = 6L * 24L * 60L * 60L * 1000L
        val ageOk = System.currentTimeMillis() - found.createdAtMillis < maxAgeMillis
        return if (found.sha256 == sha256 && found.fileSize == fileSize && ageOk) {
            found
        } else {
            null
        }
    }

    fun saveResumableSession(
        context: Context,
        userId: String,
        session: ResumableUploadSession
    ) {
        val all = loadResumableSessions(context, userId).toMutableMap()
        all[session.relativePath] = session
        persistResumableSessions(context, userId, all)
    }

    fun removeResumableSession(
        context: Context,
        userId: String,
        relativePath: String
    ) {
        val all = loadResumableSessions(context, userId).toMutableMap()
        if (all.remove(relativePath) != null) {
            persistResumableSessions(context, userId, all)
        }
    }

    private fun loadResumableSessions(
        context: Context,
        userId: String
    ): Map<String, ResumableUploadSession> {
        val u = uidKey(userId)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(keyResumableSessions(u), null) ?: return emptyMap()
        return runCatching {
            val obj = BackupJsonCodec.decodeObject(raw)
            val list = obj["sessions"] as? List<*> ?: return@runCatching emptyMap()
            val result = LinkedHashMap<String, ResumableUploadSession>()
            list.forEach { item ->
                val m = item as? Map<*, *> ?: return@forEach
                val rel = m["relativePath"] as? String ?: return@forEach
                val sha = m["sha256"] as? String ?: return@forEach
                val size = (m["fileSize"] as? Number)?.toLong() ?: return@forEach
                val uri = m["sessionUri"] as? String ?: return@forEach
                val created = (m["createdAtMillis"] as? Number)?.toLong() ?: 0L
                result[rel] = ResumableUploadSession(
                    relativePath = rel,
                    sha256 = sha,
                    fileSize = size,
                    sessionUri = uri,
                    createdAtMillis = created
                )
            }
            result
        }.getOrElse { emptyMap() }
    }

    private fun persistResumableSessions(
        context: Context,
        userId: String,
        sessions: Map<String, ResumableUploadSession>
    ) {
        val u = uidKey(userId)
        val encoded = BackupJsonCodec.encodePretty(
            linkedMapOf(
                "sessions" to sessions.values.map { s ->
                    linkedMapOf(
                        "relativePath" to s.relativePath,
                        "sha256" to s.sha256,
                        "fileSize" to s.fileSize,
                        "sessionUri" to s.sessionUri,
                        "createdAtMillis" to s.createdAtMillis
                    )
                }
            )
        )
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyResumableSessions(u), encoded)
            .apply()
    }
}
