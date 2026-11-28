package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Trip
import com.example.util.DateUtils
import com.example.util.PdfExportOptions
import com.example.util.PdfExportProgress
import com.example.util.PdfExportScope
import com.example.util.PdfGridLayout
import com.example.util.PdfPageSize
import com.example.util.PdfPhotoAlbumSpec

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfExportOptionsSheet(
    options: PdfExportOptions,
    allowThisVisitScope: Boolean,
    groupTrips: List<Trip>,
    previewSpec: PdfPhotoAlbumSpec,
    isExporting: Boolean,
    onUpdateOptions: (PdfExportOptions) -> Unit,
    onConfirmExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }

    val availableScopes = remember(allowThisVisitScope) {
        if (allowThisVisitScope) {
            listOf(
                PdfExportScope.THIS_VISIT,
                PdfExportScope.ALL,
                PdfExportScope.PERSONAL_ONLY,
                PdfExportScope.SPECIFIC_GROUP
            )
        } else {
            listOf(
                PdfExportScope.ALL,
                PdfExportScope.PERSONAL_ONLY,
                PdfExportScope.SPECIFIC_GROUP
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isExporting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Ekspor Album Foto PDF",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Foto 1:1 resolusi tinggi siap cetak (video dikecualikan)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cakupan
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cakupan Foto",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableScopes.forEach { scope ->
                        FilterChip(
                            selected = options.scope == scope,
                            onClick = {
                                val nextGroup = if (scope == PdfExportScope.SPECIFIC_GROUP) {
                                    options.selectedGroupUuid ?: groupTrips.firstOrNull()?.id
                                } else {
                                    options.selectedGroupUuid
                                }
                                onUpdateOptions(
                                    options.copy(
                                        scope = scope,
                                        selectedGroupUuid = nextGroup
                                    )
                                )
                            },
                            label = { Text(scope.label) },
                            modifier = Modifier.testTag("pdf_scope_${scope.name.lowercase()}")
                        )
                    }
                }

                if (options.scope == PdfExportScope.SPECIFIC_GROUP) {
                    if (groupTrips.isEmpty()) {
                        Text(
                            text = "Belum ada grup perjalanan yang tersedia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            groupTrips.forEach { trip ->
                                FilterChip(
                                    selected = options.selectedGroupUuid == trip.id,
                                    onClick = {
                                        onUpdateOptions(options.copy(selectedGroupUuid = trip.id))
                                    },
                                    label = { Text(trip.name) },
                                    modifier = Modifier.testTag("pdf_group_chip_${trip.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Rentang tanggal
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Filter Rentang Tanggal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pilih tanggal awal dan akhir kunjungan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = options.useDateRange,
                            onCheckedChange = { enabled ->
                                val today = DateUtils.todayEpochDay()
                                onUpdateOptions(
                                    options.copy(
                                        useDateRange = enabled,
                                        startEpochDay = options.startEpochDay ?: (today - 30L),
                                        endEpochDay = options.endEpochDay ?: today
                                    )
                                )
                            },
                            modifier = Modifier.testTag("pdf_date_range_switch")
                        )
                    }

                    if (options.useDateRange) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pickingStartDate = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pdf_start_date_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = options.startEpochDay?.let {
                                        "Dari: ${DateUtils.formatIndonesianShortDate(it)}"
                                    } ?: "Tanggal Mulai",
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { pickingEndDate = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pdf_end_date_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = options.endEpochDay?.let {
                                        "Sampai: ${DateUtils.formatIndonesianShortDate(it)}"
                                    } ?: "Tanggal Akhir",
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Ukuran Halaman & Grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tata Letak & Ukuran Halaman",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Ukuran Halaman",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfPageSize.entries.forEach { size ->
                        FilterChip(
                            selected = options.pageSize == size,
                            onClick = { onUpdateOptions(options.copy(pageSize = size)) },
                            label = { Text("${size.label} (${size.description})") },
                            modifier = Modifier.testTag("pdf_page_size_${size.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "Grid Foto per Halaman (margin 12 mm, jarak 6 mm)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfGridLayout.entries.forEach { grid ->
                        FilterChip(
                            selected = options.gridLayout == grid,
                            onClick = { onUpdateOptions(options.copy(gridLayout = grid)) },
                            label = { Text("${grid.label} (${grid.photosPerPage} foto)") },
                            modifier = Modifier.testTag("pdf_grid_${grid.name.lowercase()}")
                        )
                    }
                }
            }

            // Opsi Tampilan
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Cetak timestamp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Waktu foto diambil (contoh: 28 Nov 2026 14:32) di pojok kanan bawah.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = options.printTimestamp,
                            onCheckedChange = { onUpdateOptions(options.copy(printTimestamp = it)) },
                            modifier = Modifier.testTag("pdf_print_timestamp_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Tambah sampul",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Sertakan halaman sampul putih bersih di awal album.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = options.includeCover,
                            onCheckedChange = { onUpdateOptions(options.copy(includeCover = it)) },
                            modifier = Modifier.testTag("pdf_include_cover_switch")
                        )
                    }
                }
            }

            // Ringkasan pratinjau
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (previewSpec.totalPhotos > 0) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = if (previewSpec.totalPhotos > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = if (previewSpec.totalPhotos > 0) {
                            "Siap ekspor: ${previewSpec.totalPhotos} foto • ${previewSpec.visitSections.size} kunjungan • ${previewSpec.totalPages} halaman"
                        } else {
                            "Tidak ada foto dalam cakupan ini (video dikecualikan)."
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (previewSpec.totalPhotos > 0) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.testTag("pdf_preview_summary_text")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Batal")
                }

                Button(
                    onClick = onConfirmExport,
                    enabled = previewSpec.totalPhotos > 0 && !isExporting,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("confirm_export_pdf_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buat Album PDF")
                }
            }
        }
    }

    if (pickingStartDate) {
        val initialMillis = options.startEpochDay?.let { DateUtils.utcPickerMillisFromEpochDay(it) }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onUpdateOptions(
                                options.copy(startEpochDay = DateUtils.epochDayFromUtcPickerMillis(millis))
                            )
                        }
                        pickingStartDate = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (pickingEndDate) {
        val initialMillis = options.endEpochDay?.let { DateUtils.utcPickerMillisFromEpochDay(it) }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onUpdateOptions(
                                options.copy(endEpochDay = DateUtils.epochDayFromUtcPickerMillis(millis))
                            )
                        }
                        pickingEndDate = false
                    }
                ) {
                    Text("Pilih")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) {
                    Text("Batal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun PdfExportProgressDialog(
    progress: PdfExportProgress
) {
    val total = progress.totalPhotos.coerceAtLeast(1)
    val fraction = (progress.processedPhotos.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val percent = (fraction * 100f).toInt()

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Membuat Album Foto PDF",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Memproses foto ${progress.processedPhotos} dari ${progress.totalPhotos} ($percent%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("pdf_export_progress_bar")
                )
                Text(
                    text = "Halaman ${progress.currentPage} • Merender foto 1:1 resolusi tinggi...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {}
    )
}
