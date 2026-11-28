package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.util.DateUtils

enum class ReminderKind {
    H_MINUS_1,
    THREE_HOURS_BEFORE,
    ONE_HOUR_BEFORE,
    ANNUAL_MEMORY,
    TEST
}

data class AlarmScheduleResult(
    val scheduled: Boolean,
    val usedExactAlarm: Boolean
)

class TripAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun setActiveUser(userId: String) {
        ReminderPreferences.setLastActiveUserId(context, userId)
    }

    fun getUserReminderSettings(userId: String): UserReminderSettings {
        return ReminderPreferences.loadSettings(context, userId)
    }

    fun saveUserReminderSettings(userId: String, settings: UserReminderSettings) {
        ReminderPreferences.saveSettings(context, userId, settings)
    }

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleVisitReminder(
        visit: Kunjungan,
        groupOrSpaceName: String = "Pribadi",
        settings: UserReminderSettings = ReminderPreferences.loadActiveUserSettings(context),
        nowMillis: Long = System.currentTimeMillis()
    ): AlarmScheduleResult {
        cancelVisitReminder(visit.id)

        if (!settings.enabled) {
            return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)
        }

        val resolvedGroupName = groupOrSpaceName.ifBlank {
            if (visit.remoteTripId != null) "Trip Bersama" else "Pribadi"
        }
        val timeLabel = DateUtils.formatTime(visit.visitHour, visit.visitMinute)

        var anyScheduled = false
        var allUsedExact = true

        if (!visit.isCompletedManually) {
            val kindsToSchedule = buildList {
                if (ReminderTimingOption.H_MINUS_1 in settings.selectedTimings) add(ReminderKind.H_MINUS_1)
                if (ReminderTimingOption.THREE_HOURS_BEFORE in settings.selectedTimings) add(ReminderKind.THREE_HOURS_BEFORE)
                if (ReminderTimingOption.ONE_HOUR_BEFORE in settings.selectedTimings) add(ReminderKind.ONE_HOUR_BEFORE)
            }

            kindsToSchedule.forEach { kind ->
                val triggerAt = computeReminderTriggerMillis(
                    visitDateEpochDay = visit.visitDateEpochDay,
                    visitHour = visit.visitHour,
                    visitMinute = visit.visitMinute,
                    kind = kind,
                    nowMillis = nowMillis
                )
                if (triggerAt != null) {
                    val result = scheduleSingleKindAlarm(
                        kunjunganId = visit.id,
                        visitDateEpochDay = visit.visitDateEpochDay,
                        locationName = visit.locationName,
                        groupOrSpaceName = resolvedGroupName,
                        timeLabel = timeLabel,
                        kind = kind,
                        triggerAtMillis = triggerAt
                    )
                    if (result.scheduled) {
                        anyScheduled = true
                        if (!result.usedExactAlarm) allUsedExact = false
                    }
                }
            }
        }

        if (settings.annualMemoryReminderEnabled) {
            val annualResult = scheduleNextAnnualMemoryAlarm(
                kunjunganId = visit.id,
                visitDateEpochDay = visit.visitDateEpochDay,
                locationName = visit.locationName,
                groupOrSpaceName = resolvedGroupName,
                timeLabel = timeLabel,
                nowMillis = nowMillis
            )
            if (annualResult.scheduled) {
                anyScheduled = true
                if (!annualResult.usedExactAlarm) allUsedExact = false
            }
        }

        if (anyScheduled) {
            recordScheduledVisitId(visit.id)
        }

        return AlarmScheduleResult(
            scheduled = anyScheduled,
            usedExactAlarm = anyScheduled && allUsedExact
        )
    }

    fun scheduleNextAnnualMemoryAlarm(
        kunjunganId: String,
        visitDateEpochDay: Long,
        locationName: String,
        groupOrSpaceName: String,
        timeLabel: String,
        nowMillis: Long = System.currentTimeMillis()
    ): AlarmScheduleResult {
        val nextTrigger = computeNextAnnualMemoryTriggerMillis(visitDateEpochDay, nowMillis)
            ?: return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)
        return scheduleSingleKindAlarm(
            kunjunganId = kunjunganId,
            visitDateEpochDay = visitDateEpochDay,
            locationName = locationName,
            groupOrSpaceName = groupOrSpaceName,
            timeLabel = timeLabel,
            kind = ReminderKind.ANNUAL_MEMORY,
            triggerAtMillis = nextTrigger
        )
    }

    private fun scheduleSingleKindAlarm(
        kunjunganId: String,
        visitDateEpochDay: Long,
        locationName: String,
        groupOrSpaceName: String,
        timeLabel: String,
        kind: ReminderKind,
        triggerAtMillis: Long
    ): AlarmScheduleResult {
        val requestCode = requestCodeFor(kunjunganId, kind)
        val pendingIntent = buildReminderPendingIntent(
            requestCode = requestCode,
            kunjunganId = kunjunganId,
            visitDateEpochDay = visitDateEpochDay,
            locationName = locationName,
            groupOrSpaceName = groupOrSpaceName,
            timeLabel = timeLabel,
            kind = kind,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExactOrFallback(triggerAtMillis, pendingIntent)
    }

    fun cancelVisitReminder(kunjunganId: String) {
        listOf(
            ReminderKind.H_MINUS_1,
            ReminderKind.THREE_HOURS_BEFORE,
            ReminderKind.ONE_HOUR_BEFORE,
            ReminderKind.ANNUAL_MEMORY
        ).forEach { kind ->
            cancelByRequestCode(requestCodeFor(kunjunganId, kind), kunjunganId, kind)
        }
        removeScheduledVisitId(kunjunganId)
    }

    private fun cancelByRequestCode(requestCode: Int, kunjunganId: String, kind: ReminderKind) {
        val manager = alarmManager ?: return
        val existingIntent = buildReminderPendingIntent(
            requestCode = requestCode,
            kunjunganId = kunjunganId,
            visitDateEpochDay = 0L,
            locationName = "",
            groupOrSpaceName = "",
            timeLabel = "",
            kind = kind,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existingIntent != null) {
            manager.cancel(existingIntent)
            existingIntent.cancel()
        }
    }

    fun scheduleTestNotification(delaySeconds: Int = 10): AlarmScheduleResult {
        val triggerAtMillis = System.currentTimeMillis() + (delaySeconds * 1_000L)
        val pendingIntent = buildReminderPendingIntent(
            requestCode = TEST_REQUEST_CODE,
            kunjunganId = "",
            visitDateEpochDay = DateUtils.todayEpochDay(),
            locationName = "Pantai Kuta, Bali",
            groupOrSpaceName = "Tes Pengingat",
            timeLabel = "Sekarang",
            kind = ReminderKind.TEST,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExactOrFallback(triggerAtMillis, pendingIntent)
    }

    fun rescheduleAllReminders(
        visits: List<Kunjungan>,
        tripsById: Map<String, Trip>,
        settings: UserReminderSettings = ReminderPreferences.loadActiveUserSettings(context),
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val currentIds = visits.map { it.id }.toSet()
        val previouslyScheduled = loadScheduledVisitIds()
        previouslyScheduled.forEach { oldId ->
            if (oldId !in currentIds) {
                cancelVisitReminder(oldId)
            }
        }

        if (!settings.enabled) {
            visits.forEach { visit -> cancelVisitReminder(visit.id) }
            return
        }

        visits.forEach { visit ->
            val tripName = tripsById[visit.tripId]?.name
                ?: if (visit.remoteTripId != null) "Trip Bersama" else "Pribadi"
            scheduleVisitReminder(
                visit = visit,
                groupOrSpaceName = tripName,
                settings = settings,
                nowMillis = nowMillis
            )
        }
    }

    private fun scheduleExactOrFallback(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent?
    ): AlarmScheduleResult {
        val manager = alarmManager ?: return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)
        val intent = pendingIntent ?: return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)

        return try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    intent
                )
                AlarmScheduleResult(scheduled = true, usedExactAlarm = true)
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    intent
                )
                AlarmScheduleResult(scheduled = true, usedExactAlarm = false)
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                intent
            )
            AlarmScheduleResult(scheduled = true, usedExactAlarm = false)
        }
    }

    private fun buildReminderPendingIntent(
        requestCode: Int,
        kunjunganId: String,
        visitDateEpochDay: Long,
        locationName: String,
        groupOrSpaceName: String,
        timeLabel: String,
        kind: ReminderKind,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, TripReminderReceiver::class.java).apply {
            action = "${ACTION_TRIP_REMINDER}_${kind.name}"
            putExtra(EXTRA_KUNJUNGAN_ID, kunjunganId)
            putExtra(EXTRA_VISIT_DATE_EPOCH_DAY, visitDateEpochDay)
            putExtra(EXTRA_LOCATION_NAME, locationName)
            putExtra(EXTRA_GROUP_OR_SPACE_NAME, groupOrSpaceName)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
            putExtra(EXTRA_REMINDER_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun loadScheduledVisitIds(): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SCHEDULED_VISIT_IDS, emptySet())?.toSet() ?: emptySet()
    }

    private fun recordScheduledVisitId(visitId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = loadScheduledVisitIds().toMutableSet().apply { add(visitId) }
        prefs.edit().putStringSet(KEY_SCHEDULED_VISIT_IDS, updated).apply()
    }

    private fun removeScheduledVisitId(visitId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = loadScheduledVisitIds()
        if (visitId in current) {
            val updated = current.toMutableSet().apply { remove(visitId) }
            prefs.edit().putStringSet(KEY_SCHEDULED_VISIT_IDS, updated).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "tripbuddy_prefs"
        private const val KEY_SCHEDULED_VISIT_IDS = "scheduled_visit_ids"

        const val ACTION_TRIP_REMINDER = "com.example.ACTION_TRIP_REMINDER"
        const val EXTRA_KUNJUNGAN_ID = "extra_kunjungan_id"
        const val EXTRA_VISIT_DATE_EPOCH_DAY = "extra_visit_date_epoch_day"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
        const val EXTRA_GROUP_OR_SPACE_NAME = "extra_group_or_space_name"
        const val EXTRA_TIME_LABEL = "extra_time_label"
        const val EXTRA_REMINDER_KIND = "extra_reminder_kind"
        private const val TEST_REQUEST_CODE = 999_001

        fun requestCodeFor(kunjunganId: String, kind: ReminderKind): Int {
            val raw = ("$kunjunganId|${kind.name}").hashCode() and 0x3FFFFFFF
            return if (raw == TEST_REQUEST_CODE) raw - 1 else raw
        }

        fun computeReminderTriggerMillis(
            visitDateEpochDay: Long,
            visitHour: Int,
            visitMinute: Int,
            kind: ReminderKind,
            nowMillis: Long
        ): Long? {
            val triggerAt = when (kind) {
                ReminderKind.H_MINUS_1 -> {
                    DateUtils.targetMillisForVisit(visitDateEpochDay - 1L, 9, 0)
                }
                ReminderKind.THREE_HOURS_BEFORE -> {
                    DateUtils.targetMillisForVisit(visitDateEpochDay, visitHour, visitMinute) - (3L * 3_600_000L)
                }
                ReminderKind.ONE_HOUR_BEFORE -> {
                    DateUtils.targetMillisForVisit(visitDateEpochDay, visitHour, visitMinute) - (1L * 3_600_000L)
                }
                ReminderKind.ANNUAL_MEMORY -> {
                    return computeNextAnnualMemoryTriggerMillis(visitDateEpochDay, nowMillis)
                }
                ReminderKind.TEST -> nowMillis + 10_000L
            }
            return if (triggerAt > nowMillis) triggerAt else null
        }

        fun computeNextAnnualMemoryTriggerMillis(
            visitDateEpochDay: Long,
            nowMillis: Long
        ): Long? {
            val (origYear, origMonth, origDay) = DateUtils.ymdFromEpochDay(visitDateEpochDay)
            val (currentYear, _, _) = DateUtils.ymdFromEpochDay(DateUtils.todayEpochDay(nowMillis))
            var targetYear = maxOf(origYear + 1, currentYear)
            repeat(5) {
                val maxDay = DateUtils.daysInMonth(targetYear, origMonth)
                val day = minOf(origDay, maxDay)
                val anniversaryDay = DateUtils.epochDayFromYmd(targetYear, origMonth, day)
                val triggerAt = DateUtils.targetMillisForVisit(anniversaryDay, 9, 0)
                if (triggerAt > nowMillis) {
                    return triggerAt
                }
                targetYear += 1
            }
            return null
        }

        fun formatNotificationTitleAndBody(
            kind: ReminderKind,
            locationName: String,
            timeLabel: String,
            groupOrSpaceLabel: String
        ): Pair<String, String> {
            val spaceLabel = groupOrSpaceLabel.ifBlank { "Pribadi" }
            return when (kind) {
                ReminderKind.H_MINUS_1 -> {
                    "Pengingat Kunjungan Besok" to "Besok: $locationName jam $timeLabel - $spaceLabel"
                }
                ReminderKind.THREE_HOURS_BEFORE -> {
                    "Pengingat 3 Jam Lagi" to "3 jam lagi: $locationName jam $timeLabel - $spaceLabel"
                }
                ReminderKind.ONE_HOUR_BEFORE -> {
                    "Pengingat 1 Jam Lagi" to "1 jam lagi: $locationName - $spaceLabel"
                }
                ReminderKind.ANNUAL_MEMORY -> {
                    "Kenangan TripBuddy" to "Setahun lalu: $locationName"
                }
                ReminderKind.TEST -> {
                    "Tes Notifikasi TripBuddy" to "Notifikasi pengingat perjalanan berfungsi dengan baik. Lokasi contoh: $locationName"
                }
            }
        }
    }
}
