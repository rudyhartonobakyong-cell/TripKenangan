package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.Kunjungan
import com.example.ui.DisplayVisit
import com.example.util.DateUtils

data class AlarmScheduleResult(
    val scheduled: Boolean,
    val usedExactAlarm: Boolean
)

class TripAlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        val manager = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleVisitReminder(visit: Kunjungan): AlarmScheduleResult {
        val requestCode = requestCodeForUuid(visit.id)
        return scheduleReminderInternal(
            requestCode = requestCode,
            visitIdLong = requestCode.toLong(),
            locationName = visit.locationName,
            tripTitle = visit.tripTitle,
            visitDateEpochDay = visit.visitDateEpochDay,
            visitHour = visit.visitHour,
            visitMinute = visit.visitMinute,
            completedManually = visit.isCompletedManually
        )
    }

    fun scheduleVisitReminder(visit: DisplayVisit): AlarmScheduleResult {
        val requestCode = requestCodeForUuid(visit.id)
        return scheduleReminderInternal(
            requestCode = requestCode,
            visitIdLong = requestCode.toLong(),
            locationName = visit.locationName,
            tripTitle = visit.tripTitle,
            visitDateEpochDay = visit.visitDateEpochDay,
            visitHour = visit.visitHour,
            visitMinute = visit.visitMinute,
            completedManually = visit.completedManually
        )
    }

    private fun scheduleReminderInternal(
        requestCode: Int,
        visitIdLong: Long,
        locationName: String,
        tripTitle: String,
        visitDateEpochDay: Long,
        visitHour: Int,
        visitMinute: Int,
        completedManually: Boolean
    ): AlarmScheduleResult {
        cancelByRequestCode(requestCode, visitIdLong)

        if (completedManually) {
            return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)
        }

        val triggerAtMillis = DateUtils.targetMillisForVisit(
            epochDay = visitDateEpochDay,
            hour = visitHour,
            minute = visitMinute
        )

        if (triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleResult(scheduled = false, usedExactAlarm = false)
        }

        val pendingIntent = buildReminderPendingIntent(
            requestCode = requestCode,
            visitId = visitIdLong,
            locationName = locationName,
            tripTitle = tripTitle,
            timeLabel = DateUtils.formatTime(visitHour, visitMinute),
            isTest = false,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return scheduleExactOrFallback(triggerAtMillis, pendingIntent)
    }

    fun cancelVisitReminder(kunjunganId: String) {
        val requestCode = requestCodeForUuid(kunjunganId)
        cancelByRequestCode(requestCode, requestCode.toLong())
    }

    fun cancelVisitReminder(visit: DisplayVisit) {
        cancelVisitReminder(visit.id)
    }

    private fun cancelByRequestCode(requestCode: Int, visitId: Long) {
        val manager = alarmManager ?: return
        val existingIntent = buildReminderPendingIntent(
            requestCode = requestCode,
            visitId = visitId,
            locationName = "",
            tripTitle = "",
            timeLabel = "",
            isTest = false,
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
            visitId = -1L,
            locationName = "Pantai Kuta, Bali",
            tripTitle = "Tes Pengingat",
            timeLabel = "Sekarang",
            isTest = true,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return scheduleExactOrFallback(triggerAtMillis, pendingIntent)
    }

    fun rescheduleAllUpcomingReminders(visits: List<Kunjungan>) {
        visits.forEach { visit ->
            scheduleVisitReminder(visit)
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
        visitId: Long,
        locationName: String,
        tripTitle: String,
        timeLabel: String,
        isTest: Boolean,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, TripReminderReceiver::class.java).apply {
            action = ACTION_TRIP_REMINDER
            putExtra(EXTRA_VISIT_ID, visitId)
            putExtra(EXTRA_LOCATION_NAME, locationName)
            putExtra(EXTRA_TRIP_TITLE, tripTitle)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
            putExtra(EXTRA_IS_TEST, isTest)
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun requestCodeForUuid(uuid: String): Int {
        val positive = uuid.hashCode() and 0x3FFFFFFF
        return if (positive == TEST_REQUEST_CODE) positive - 1 else positive
    }

    companion object {
        const val ACTION_TRIP_REMINDER = "com.example.ACTION_TRIP_REMINDER"
        const val EXTRA_VISIT_ID = "extra_visit_id"
        const val EXTRA_LOCATION_NAME = "extra_location_name"
        const val EXTRA_TRIP_TITLE = "extra_trip_title"
        const val EXTRA_TIME_LABEL = "extra_time_label"
        const val EXTRA_IS_TEST = "extra_is_test"
        private const val TEST_REQUEST_CODE = 999_001
    }
}
