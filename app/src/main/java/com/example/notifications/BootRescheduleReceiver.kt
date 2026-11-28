package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.TripBuddyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != ACTION_QUICKBOOT_POWERON &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                TripNotificationHelper.createNotificationChannel(appContext)
                val db = TripBuddyDatabase.getInstance(appContext)
                val tripsById = db.tripDao().getAllTripsOnce().associateBy { it.id }
                val allVisits = db.kunjunganDao().getAllKunjunganOnce()
                val settings = ReminderPreferences.loadActiveUserSettings(appContext)
                TripAlarmScheduler(appContext).rescheduleAllReminders(
                    visits = allVisits,
                    tripsById = tripsById,
                    settings = settings
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
