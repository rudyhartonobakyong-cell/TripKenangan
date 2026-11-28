package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object TripNotificationHelper {
    const val CHANNEL_ID = "trip_reminders_channel"
    const val DRIVE_BACKUP_CHANNEL_ID = "drive_backup_channel"
    const val DRIVE_ALERT_CHANNEL_ID = "drive_backup_alert_channel"
    const val EXTRA_OPEN_KUNJUNGAN_ID = "extra_open_kunjungan_id"
    private const val NOTIFICATION_ID_DRIVE_SUCCESS = 990101
    private const val NOTIFICATION_ID_DRIVE_FAILURE_ALERT = 990102

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = descriptionText
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)

            val backupChannel = NotificationChannel(
                DRIVE_BACKUP_CHANNEL_ID,
                "Cadangan Google Drive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pemberitahuan ringan saat cadangan otomatis Google Drive selesai"
            }
            manager.createNotificationChannel(backupChannel)

            val alertChannel = NotificationChannel(
                DRIVE_ALERT_CHANNEL_ID,
                "Peringatan Cadangan Google Drive",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan bila cadangan Google Drive gagal berturut-turut"
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showTripReminderNotification(
        context: Context,
        notificationId: Int,
        targetKunjunganId: String?,
        title: String,
        bodyText: String
    ) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = if (!targetKunjunganId.isNullOrBlank()) {
                "com.example.OPEN_VISIT_$targetKunjunganId"
            } else {
                Intent.ACTION_MAIN
            }
            if (!targetKunjunganId.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_KUNJUNGAN_ID, targetKunjunganId)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Izin POST_NOTIFICATIONS dicabut oleh pengguna di pengaturan sistem
        }
    }

    fun showDriveBackupSuccessNotification(
        context: Context,
        uploadedFilesCount: Int,
        totalSizeFormatted: String
    ) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val body = if (uploadedFilesCount > 0) {
            "$uploadedFilesCount file baru diunggah • Total cadangan $totalSizeFormatted."
        } else {
            "Semua data & media pribadi sudah mutakhir ($totalSizeFormatted)."
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DRIVE_SUCCESS,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DRIVE_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setContentTitle("Cadangan Google Drive berhasil")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DRIVE_SUCCESS, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showDriveBackupFailureWarningNotification(
        context: Context,
        consecutiveFailures: Int,
        reason: String
    ) {
        createNotificationChannel(context)
        if (!hasNotificationPermission(context)) return

        val cleanReason = reason.ifBlank { "Periksa koneksi internet dan izin Google Drive Anda." }
        val body = "Cadangan otomatis gagal $consecutiveFailures kali berturut-turut. Penyebab: $cleanReason"

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DRIVE_FAILURE_ALERT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DRIVE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_trip)
            .setContentTitle("Peringatan Cadangan Google Drive")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DRIVE_FAILURE_ALERT, notification)
        } catch (_: SecurityException) {
        }
    }
}
