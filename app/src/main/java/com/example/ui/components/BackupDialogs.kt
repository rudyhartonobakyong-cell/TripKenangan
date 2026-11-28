package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RestorePage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.BackupEstimate
import com.example.util.BackupInspectionSummary
import com.example.util.BackupManager
import com.example.util.BackupProgress
import com.example.util.DateUtils

@Composable
fun BackupAndRestoreSection(
    includeVideos: Boolean,
    estimate: BackupEstimate?,
    isEstimating: Boolean,
    isBusy: Boolean,
    onToggleIncludeVideos: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
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
                text = "Buat satu arsip ZIP berisi data pribadi (kunjungan, kenangan, pengaturan) serta foto dan video di penyimpanan aplikasi pada resolusi asli. Data grup di Firestore tidak disalin.",
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
                    enabled = !isBusy,
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
                            text = "Siap mengekspor data pribadi ke ZIP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Button(
                onClick = onExportClick,
                enabled = !isBusy,
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
                enabled = !isBusy,
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
