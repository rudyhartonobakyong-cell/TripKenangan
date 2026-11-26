package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TripReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val visitId = intent.getLongExtra(TripAlarmScheduler.EXTRA_VISIT_ID, -1L)
        val locationName = intent.getStringExtra(TripAlarmScheduler.EXTRA_LOCATION_NAME).orEmpty()
        val tripTitle = intent.getStringExtra(TripAlarmScheduler.EXTRA_TRIP_TITLE).orEmpty()
        val timeLabel = intent.getStringExtra(TripAlarmScheduler.EXTRA_TIME_LABEL).orEmpty()
        val isTest = intent.getBooleanExtra(TripAlarmScheduler.EXTRA_IS_TEST, false)

        if (!isTest && locationName.isBlank()) return

        val notificationId = if (isTest) {
            777_001
        } else {
            (visitId.hashCode() and 0x7FFFFFFF).let { if (it == 777_001) it + 1 else it }
        }

        TripNotificationHelper.showTripReminderNotification(
            context = context,
            notificationId = notificationId,
            locationName = locationName.ifBlank { "Lokasi Perjalanan" },
            tripTitle = tripTitle,
            timeLabel = timeLabel.ifBlank { "Sekarang" },
            isTest = isTest
        )
    }
}
