package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drive.DriveAccountConnection
import com.example.drive.DriveBackupFrequency
import com.example.drive.DriveBackupLastStatus
import com.example.drive.DriveBackupProgress
import com.example.drive.DriveBackupScheduleSettings
import com.example.drive.DriveRemoteBackupSummary
import com.example.drive.DriveRestoreConfirmationSummary
import com.example.util.BackupEstimate
import com.example.util.BackupInspectionSummary
import com.example.util.BackupManager
import com.example.util.BackupProgress
import com.example.util.DateUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupAndRestoreSection(
    includeVideos: Boolean,
    estimate: BackupEstimate?,
    isEstimating: Boolean,
    isBusy: Boolean,
    driveConnection: DriveAccountConnection,
    driveSchedule: DriveBackupScheduleSettings,
    driveLastStatus: DriveBackupLastStatus,
    isDriveActionLoading: Boolean,
    onToggleIncludeVideos: (Boolean) -> Unit,
    onConnectDriveClick: () -> Unit,
    onDisconnectDriveClick: () -> Unit,
    onUpdateDriveSchedule: (DriveBackupScheduleSettings) -> Unit,
    onDriveBackupNowClick: () -> Unit,
    onDriveRestoreClick: () -> Unit,
    onOpenBatteryAppInfo: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    val anyBusy = isBusy || isDriveActionLoading

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Cadangan & Pemulihan",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Cadangkan data pribadi (kunjungan, kenangan, pengaturan) serta foto dan video di penyimpanan aplikasi pada resolusi asli. Data grup di Firestore tidak disalin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Sertakan video",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Nonaktifkan jika ingin ukuran cadangan lebih ringkas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeVideos,
                    onCheckedChange = onToggleIncludeVideos,
                    enabled = !anyBusy,
                    modifier = Modifier.testTag("backup_include_videos_switch")
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEstimating && estimate == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Menghitung perkiraan ukuran cadangan...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (estimate != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_estimate_text"),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Perkiraan ukuran: ~${BackupManager.formatByteSize(estimate.estimatedZipBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${estimate.kunjunganCount} kunjungan (${estimate.memoryCount} kenangan) • ${estimate.photoCount} foto • ${estimate.videoCount} video",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Siap mencadangkan data pribadi & media lokal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Google Drive Auto-Backup Subsection
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (driveConnection.isConnected) {
                                Icons.Rounded.CloudDone
                            } else {
                                Icons.Rounded.CloudOff
                            },
                            contentDescription = null,
                            tint = if (driveConnection.isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Drive (Folder TripBuddy Backup)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Akses terbatas (drive.file) hanya untuk file buatan TripBuddy, menyimpan maksimal 3 versi manifest terakhir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!driveConnection.isConnected) {
                        Button(
                            onClick = onConnectDriveClick,
                            enabled = !anyBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("connect_drive_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isDriveActionLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Menghubungkan...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Backup,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Hubungkan Google Drive")
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = driveConnection.accountName.ifBlank { "Akun Google Terhubung" },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (driveConnection.accountEmail.isNotBlank()) {
                                        Text(
                                            text = driveConnection.accountEmail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showDisconnectConfirm = true },
                                    enabled = !anyBusy,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .testTag("disconnect_drive_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Putuskan")
                                }
                            }
                        }

                        // Last Backup Status Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when (driveLastStatus.lastResultSuccess) {
                                true -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drive_backup_status_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val statusHeading = when (driveLastStatus.lastResultSuccess) {
                                    true -> "Cadangan Terakhir: Berhasil"
                                    false -> if (driveLastStatus.consecutiveFailures >= 3) {
                                        "Cadangan Terakhir: Gagal (${driveLastStatus.consecutiveFailures}x berturut-turut)"
                                    } else {
                                        "Cadangan Terakhir: Gagal"
                                    }
                                    null -> "Status Cadangan: Belum pernah mencadangkan ke Drive"
                                }
                                val statusColor = when (driveLastStatus.lastResultSuccess) {
                                    true -> MaterialTheme.colorScheme.onSecondaryContainer
                                    false -> MaterialTheme.colorScheme.onErrorContainer
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    text = statusHeading,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColor
                                )

                                if (driveLastStatus.lastSuccessAtMillis > 0L) {
                                    Text(
                                        text = "Waktu berhasil: ${DateUtils.formatPhotoTimestamp(driveLastStatus.lastSuccessAtMillis)} • Ukuran: ${BackupManager.formatByteSize(driveLastStatus.lastBackupSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                } else if (driveLastStatus.lastAttemptAtMillis > 0L) {
                                    Text(
                                        text = "Percobaan terakhir: ${DateUtils.formatPhotoTimestamp(driveLastStatus.lastAttemptAtMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }

                                if (driveLastStatus.lastResultSuccess == true &&
                                    (driveLastStatus.lastUploadedFilesCount > 0 || driveLastStatus.lastSkippedFilesCount > 0)
                                ) {
                                    Text(
                                        text = "${driveLastStatus.lastUploadedFilesCount} file baru diunggah, ${driveLastStatus.lastSkippedFilesCount} file unchanged dilewati.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }

                                if (driveLastStatus.lastResultSuccess == false && driveLastStatus.lastErrorMessage.isNotBlank()) {
                                    Text(
                                        text = "Penyebab: ${driveLastStatus.lastErrorMessage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Manual "Cadangkan Sekarang" + "Pulihkan dari Drive"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onDriveBackupNowClick,
                                enabled = !anyBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("drive_backup_now_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cadangkan Sekarang", maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = onDriveRestoreClick,
                            enabled = !anyBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("drive_restore_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pulihkan dari Drive")
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        // Automatic Backup Schedule Controls
                        Text(
                            text = "Cadangan Otomatis",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DriveBackupFrequency.entries.forEach { freq ->
                                val selected = driveSchedule.frequency == freq
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        onUpdateDriveSchedule(driveSchedule.copy(frequency = freq))
                                    },
                                    enabled = !anyBusy,
                                    label = { Text(freq.label) },
                                    modifier = Modifier.testTag("drive_freq_${freq.id}")
                                )
                            }
                        }

                        if (driveSchedule.frequency == DriveBackupFrequency.WEEKLY) {
                            Text(
                                text = "Pilih hari pencadangan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                DriveBackupScheduleSettings.orderedDaysOfWeek.forEach { day ->
                                    FilterChip(
                                        selected = driveSchedule.dayOfWeek == day,
                                        onClick = {
                                            onUpdateDriveSchedule(driveSchedule.copy(dayOfWeek = day))
                                        },
                                        enabled = !anyBusy,
                                        label = { Text(DriveBackupScheduleSettings.dayOfWeekLabel(day)) },
                                        modifier = Modifier.testTag("drive_dow_$day")
                                    )
                                }
                            }
                        }

                        if (driveSchedule.frequency == DriveBackupFrequency.MONTHLY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tanggal setiap bulan",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            val prevDay = if (driveSchedule.dayOfMonth <= 1) 28 else driveSchedule.dayOfMonth - 1
                                            onUpdateDriveSchedule(driveSchedule.copy(dayOfMonth = prevDay))
                                        },
                                        enabled = !anyBusy,
                                        modifier = Modifier.testTag("drive_dom_prev")
                                    ) {
                                        Icon(Icons.Rounded.Remove, contentDescription = "Kurangi tanggal")
                                    }
                                    Text(
                                        text = "Tgl ${driveSchedule.dayOfMonth}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            val nextDay = if (driveSchedule.dayOfMonth >= 28) 1 else driveSchedule.dayOfMonth + 1
                                            onUpdateDriveSchedule(driveSchedule.copy(dayOfMonth = nextDay))
                                        },
                                        enabled = !anyBusy,
                                        modifier = Modifier.testTag("drive_dom_next")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Tambah tanggal")
                                    }
                                }
                            }
                        }

                        if (driveSchedule.frequency != DriveBackupFrequency.OFF) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !anyBusy) { showTimePickerDialog = true }
                                    .testTag("drive_time_picker_row")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Schedule,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Jam pencadangan",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Disarankan dini hari agar tidak mengganggu pemakaian.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = driveSchedule.formattedTime(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Hanya saat Wi-Fi",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Hemat kuota seluler saat mengunggah foto & video.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = driveSchedule.wifiOnly,
                                    onCheckedChange = { checked ->
                                        onUpdateDriveSchedule(driveSchedule.copy(wifiOnly = checked))
                                    },
                                    enabled = !anyBusy,
                                    modifier = Modifier.testTag("drive_wifi_only_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Hanya saat mengisi daya",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Menjaga baterai tetap awet saat cadangan berjalan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = driveSchedule.chargingOnly,
                                    onCheckedChange = { checked ->
                                        onUpdateDriveSchedule(driveSchedule.copy(chargingOnly = checked))
                                    },
                                    enabled = !anyBusy,
                                    modifier = Modifier.testTag("drive_charging_only_switch")
                                )
                            }
                        }
                    }

                    // Battery instructions specifically for background Drive sync on Xiaomi/Oppo/Vivo
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BatterySaver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Tips Baterai Cadangan Otomatis",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Agar jadwal cadangan otomatis tidak dihentikan sistem saat layar mati, atur baterai TripBuddy ke \"Tanpa batasan\":\n" +
                                    "• Xiaomi: Pengaturan > Aplikasi > TripBuddy > Penghemat baterai > Tanpa batasan.\n" +
                                    "• Oppo / Realme: Info Aplikasi > Penggunaan baterai > Izinkan aktivitas latar belakang.\n" +
                                    "• Vivo: Info Aplikasi > Baterai > Tidak dibatasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenBatteryAppInfo,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("drive_open_battery_settings_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Buka Info Aplikasi")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Text(
                text = "Cadangan File ZIP Lokal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onExportClick,
                enabled = !anyBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("export_backup_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ekspor Semua Data")
            }

            OutlinedButton(
                onClick = onImportClick,
                enabled = !anyBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_backup_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestorePage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Impor dari Cadangan")
            }
        }
    }

    if (showTimePickerDialog) {
        DriveTimePickerDialog(
            initialHour = driveSchedule.hour,
            initialMinute = driveSchedule.minute,
            onConfirm = { h, m ->
                showTimePickerDialog = false
                onUpdateDriveSchedule(driveSchedule.copy(hour = h, minute = m))
            },
            onDismiss = { showTimePickerDialog = false }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Putuskan Google Drive?") },
            text = {
                Text(
                    "Jadwal cadangan otomatis akan dihentikan. File cadangan yang sudah ada di folder \"TripBuddy Backup\" Google Drive Anda tetap aman dan tidak dihapus."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirm = false
                        onDisconnectDriveClick()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_disconnect_drive_button")
                ) {
                    Text("Putuskan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.coerceIn(0, 23),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih Jam Pencadangan") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(state.hour, state.minute) },
                modifier = Modifier.testTag("confirm_drive_time_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun DriveBackupProgressDialog(progress: DriveBackupProgress) {
    val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = if (progress.isRestore) {
                    "Memulihkan dari Google Drive"
                } else {
                    "Mencadangkan ke Google Drive"
                },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = progress.stepLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("drive_progress_bar")
                )
                val bytesNote = if (progress.totalBytes > 0L) {
                    "${BackupManager.formatByteSize(progress.processedBytes)} / ${BackupManager.formatByteSize(progress.totalBytes)} ($percent%)"
                } else {
                    "$percent%"
                }
                Text(
                    text = bytesNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = { }
    )
}

@Composable
fun DriveBackupVersionPickerDialog(
    versions: List<DriveRemoteBackupSummary>,
    onSelectVersion: (DriveRemoteBackupSummary) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Pilih Versi Cadangan Drive",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (versions.isEmpty()) {
                Text(
                    text = "Belum ada versi cadangan di folder \"TripBuddy Backup\" pada akun Google Drive Anda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih salah satu versi cadangan terbaru untuk memulihkan data pribadi & media ke HP ini:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    versions.forEachIndexed { idx, item ->
                        val dateLabel = if (item.manifest.createdAtMillis > 0L) {
                            DateUtils.formatPhotoTimestamp(item.manifest.createdAtMillis)
                        } else {
                            item.fileName
                        }
                        Surface(
                            onClick = { onSelectVersion(item) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drive_version_item_$idx")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (idx == 0) "Terbaru • $dateLabel" else dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (idx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = BackupManager.formatByteSize(item.manifest.totalMediaBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "${item.manifest.memoryCount} kenangan (${item.manifest.kunjunganCount} kunjungan) • ${item.manifest.photoCount} foto • ${item.manifest.videoCount} video",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun DriveRestoreConfirmDialog(
    summary: DriveRestoreConfirmationSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val manifest = summary.remoteSummary.manifest
    val createdLabel = if (manifest.createdAtMillis > 0L) {
        DateUtils.formatPhotoTimestamp(manifest.createdAtMillis)
    } else {
        summary.remoteSummary.fileName
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Konfirmasi Pemulihan Drive",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Data pribadi dan file media dari Google Drive siap dipulihkan ke perangkat ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Versi: $createdLabel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${summary.newKunjunganCount} kunjungan baru (${summary.existingKunjunganCount} sudah ada akan dilewati)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${summary.newFotoCount} media baru (${summary.existingFotoCount} sudah ada akan dilewati)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${summary.missingMediaFilesCount} file media akan diunduh (~${BackupManager.formatByteSize(summary.missingMediaBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_drive_restore_button")
            ) {
                Text("Pulihkan Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun BackupProgressDialog(progress: BackupProgress) {
    val percent = (progress.fraction * 100f).toInt().coerceIn(0, 100)
    AlertDialog(
        onDismissRequest = { },
        title = {
            Text(
                text = if (progress.isImport) "Memulihkan Cadangan" else "Membuat File Cadangan",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = progress.stepLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_progress_bar")
                )
                val bytesNote = if (progress.totalBytes > 0L) {
                    "${BackupManager.formatByteSize(progress.processedBytes)} / ${BackupManager.formatByteSize(progress.totalBytes)} ($percent%)"
                } else {
                    "$percent%"
                }
                Text(
                    text = bytesNote,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = { }
    )
}

@Composable
fun BackupImportConfirmDialog(
    summary: BackupInspectionSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val exportedAtLabel = if (summary.manifest.exportedAtMillis > 0L) {
        DateUtils.formatPhotoTimestamp(summary.manifest.exportedAtMillis)
    } else {
        "Tidak diketahui"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Konfirmasi Pemulihan Data",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "File cadangan tervalidasi dan siap dipulihkan ke HP ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = summary.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dibuat: $exportedAtLabel • Versi v${summary.manifest.backupVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "• ${summary.manifest.memoryCount} kenangan (${summary.manifest.kunjunganCount} kunjungan pribadi)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${summary.manifest.photoCount} foto • ${summary.manifest.videoCount} video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• Perkiraan ukuran media: ${BackupManager.formatByteSize(summary.estimatedRestoreBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = if (summary.existingKunjunganCount > 0 || summary.existingFotoCount > 0) {
                        "Item baru: ${summary.newKunjunganCount} kunjungan dan ${summary.newFotoCount} media. Item yang ID-nya sudah ada (${summary.existingKunjunganCount} kunjungan, ${summary.existingFotoCount} media) tidak akan diduplikasi."
                    } else {
                        "Item yang ID-nya sudah ada di perangkat tidak akan diduplikasi."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_restore_backup_button")
            ) {
                Text("Pulihkan Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun BackupStatusDialog(
    title: String,
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_backup_status_button")
            ) {
                Text("Tutup")
            }
        }
    )
}
