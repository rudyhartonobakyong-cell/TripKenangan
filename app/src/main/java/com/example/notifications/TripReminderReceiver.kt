package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TripReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val kunjunganId = intent.getStringExtra(TripAlarmScheduler.EXTRA_KUNJUNGAN_ID).orEmpty()
        val visitDateEpochDay = intent.getLongExtra(TripAlarmScheduler.EXTRA_VISIT_DATE_EPOCH_DAY, 0L)
        val locationName = intent.getStringExtra(TripAlarmScheduler.EXTRA_LOCATION_NAME).orEmpty()
        val groupOrSpaceName = intent.getStringExtra(TripAlarmScheduler.EXTRA_GROUP_OR_SPACE_NAME).orEmpty()
        val timeLabel = intent.getStringExtra(TripAlarmScheduler.EXTRA_TIME_LABEL).orEmpty()
        val kindRaw = intent.getStringExtra(TripAlarmScheduler.EXTRA_REMINDER_KIND)
        val kind = ReminderKind.entries.find { it.name == kindRaw } ?: ReminderKind.ONE_HOUR_BEFORE

        if (kind != ReminderKind.TEST && locationName.isBlank()) return

        val settings = ReminderPreferences.loadActiveUserSettings(context)
        if (kind != ReminderKind.TEST && !settings.enabled) return
        if (kind == ReminderKind.ANNUAL_MEMORY && !settings.annualMemoryReminderEnabled) return

        val resolvedLocation = locationName.ifBlank { "Lokasi Perjalanan" }
        val resolvedTime = timeLabel.ifBlank { "Sekarang" }
        val (title, body) = TripAlarmScheduler.formatNotificationTitleAndBody(
            kind = kind,
            locationName = resolvedLocation,
            timeLabel = resolvedTime,
            groupOrSpaceLabel = groupOrSpaceName
        )

        val notificationId = if (kind == ReminderKind.TEST) {
            777_001
        } else {
            TripAlarmScheduler.requestCodeFor(kunjunganId.ifBlank { resolvedLocation }, kind)
                .let { if (it == 777_001) it + 1 else it }
        }

        TripNotificationHelper.showTripReminderNotification(
            context = context,
            notificationId = notificationId,
            targetKunjunganId = if (kind == ReminderKind.TEST) null else kunjunganId.takeIf { it.isNotBlank() },
            title = title,
            bodyText = body
        )

        if (kind == ReminderKind.ANNUAL_MEMORY && kunjunganId.isNotBlank() && visitDateEpochDay > 0L) {
            TripAlarmScheduler(context.applicationContext).scheduleNextAnnualMemoryAlarm(
                kunjunganId = kunjunganId,
                visitDateEpochDay = visitDateEpochDay,
                locationName = resolvedLocation,
                groupOrSpaceName = groupOrSpaceName,
                timeLabel = resolvedTime,
                nowMillis = System.currentTimeMillis() + 60_000L
            )
        }
    }
}
