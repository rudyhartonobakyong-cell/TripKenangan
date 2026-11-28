package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.GroupMemorySummary
import com.example.ui.MemoriesFilterTab
import com.example.ui.TripBuddyUiState
import com.example.ui.VisitMemoryItem
import com.example.ui.components.MemoryPhotoPlaceholder
import com.example.util.DateUtils
import com.example.util.PhotoStorageHelper
import java.io.File

@Composable
fun MemoriesScreen(
    uiState: TripBuddyUiState,
    onBackToHome: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMemoriesTabChange: (MemoriesFilterTab) -> Unit,
    onSelectMemoryGroup: (String?) -> Unit,
    onVisitMemoryClick: (String) -> Unit,
    onExportGroupPdf: (GroupMemorySummary) -> Unit = {},
    onExportPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isInsideGroupDetail = uiState.selectedMemoriesTab == MemoriesFilterTab.GRUP &&
        uiState.selectedMemoryGroupId != null

    BackHandler {
        if (isInsideGroupDetail) {
            onSelectMemoryGroup(null)
        } else {
            onBackToHome()
        }
    }

    val tabs = listOf(
        MemoriesFilterTab.SEMUA to "Semua",
        MemoriesFilterTab.PRIBADI to "Pribadi",
        MemoriesFilterTab.GRUP to "Grup"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Kenangan Perjalanan",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = when (uiState.selectedMemoriesTab) {
                                MemoriesFilterTab.SEMUA -> "Semua kenangan pribadi & grup dari terbaru"
                                MemoriesFilterTab.PRIBADI -> "Kenangan pribadi hanya di perangkat ini"
                                MemoriesFilterTab.GRUP -> if (isInsideGroupDetail) {
                                    "Kenangan di grup ${uiState.selectedMemoryGroupSummary?.trip?.name.orEmpty()}"
                                } else {
                                    "Daftar semua grup yang pernah diikuti"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${uiState.visitMemories.size} Kunjungan",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedMemoriesTab }.coerceAtLeast(0)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {},
                        indicator = { tabPositions ->
                            if (selectedIndex in tabPositions.indices) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        tabs.forEach { (tab, label) ->
                            val selected = uiState.selectedMemoriesTab == tab
                            val tag = when (tab) {
                                MemoriesFilterTab.SEMUA -> "memories_tab_semua"
                                MemoriesFilterTab.PRIBADI -> "memories_tab_pribadi"
                                MemoriesFilterTab.GRUP -> "memories_tab_grup"
                            }
                            Tab(
                                selected = selected,
                                onClick = {
                                    if (tab == MemoriesFilterTab.GRUP && selected && isInsideGroupDetail) {
                                        onSelectMemoryGroup(null)
                                    } else {
                                        onMemoriesTabChange(tab)
                                    }
                                },
                                text = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag(tag)
                            )
                        }
                    }
                }
            }

            if (isInsideGroupDetail && uiState.selectedMemoryGroupSummary != null) {
                val activeSummary = uiState.selectedMemoryGroupSummary
                item {
                    SelectedGroupMemoriesHeader(
                        summary = activeSummary,
                        isExportingPdf = uiState.isExportingPdf,
                        onBackToGroupList = { onSelectMemoryGroup(null) },
                        onExportGroupPdf = { onExportGroupPdf(activeSummary) }
                    )
                }
            } else {
                item {
                    OutlinedButton(
                        onClick = onExportPdf,
                        enabled = !uiState.isExportingPdf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("memories_export_pdf_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ekspor Album Foto PDF")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.memorySearchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memories_search_input"),
                    placeholder = {
                        Text(
                            if (uiState.selectedMemoriesTab == MemoriesFilterTab.GRUP && !isInsideGroupDetail) {
                                "Cari nama grup atau rentang tanggal..."
                            } else {
                                "Cari lokasi, nama trip, tanggal, atau deskripsi..."
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.memorySearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchQueryChange("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus pencarian"
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            if (uiState.selectedMemoriesTab == MemoriesFilterTab.GRUP && !isInsideGroupDetail) {
                if (uiState.groupMemorySummaries.isEmpty()) {
                    item {
                        EmptyGroupMemoriesCard(hasSearchQuery = uiState.memorySearchQuery.isNotBlank())
                    }
                } else {
                    items(
                        items = uiState.groupMemorySummaries,
                        key = { "group_summary_${it.trip.id}" }
                    ) { summary ->
                        GroupMemoryFolderCard(
                            summary = summary,
                            onClick = { onSelectMemoryGroup(summary.trip.id) }
                        )
                    }
                }
            } else if (uiState.filteredVisitMemories.isEmpty()) {
                val hasMemoriesOverall = when (uiState.selectedMemoriesTab) {
                    MemoriesFilterTab.SEMUA -> uiState.visitMemories.isNotEmpty()
                    MemoriesFilterTab.PRIBADI -> uiState.visitMemories.any { it.trip.isPersonal }
                    MemoriesFilterTab.GRUP -> (uiState.selectedMemoryGroupSummary?.memoryCount ?: 0) > 0
                }
                item {
                    if (!hasMemoriesOverall && uiState.memorySearchQuery.isBlank()) {
                        EmptyMemoriesCard(
                            title = when (uiState.selectedMemoriesTab) {
                                MemoriesFilterTab.PRIBADI -> "Belum Ada Kenangan Pribadi"
                                MemoriesFilterTab.GRUP -> "Belum Ada Kenangan di Grup Ini"
                                MemoriesFilterTab.SEMUA -> "Belum Ada Kenangan Tersimpan"
                            }
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Tidak ada kenangan yang cocok dengan filter atau pencarian Anda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.filteredVisitMemories,
                    key = { _, item -> item.kunjungan.id }
                ) { index, item ->
                    val previousDate = uiState.filteredVisitMemories
                        .getOrNull(index - 1)
                        ?.kunjungan
                        ?.visitDateEpochDay

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (previousDate != item.kunjungan.visitDateEpochDay) {
                            Text(
                                text = DateUtils.formatIndonesianFullDate(item.kunjungan.visitDateEpochDay),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp)
                            )
                        }

                        VisitMemoryThumbnailCard(
                            item = item,
                            onClick = { onVisitMemoryClick(item.kunjungan.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

private fun formatMediaCountLabel(photoCount: Int, videoCount: Int): String {
    return when {
        photoCount > 0 && videoCount > 0 -> "$photoCount foto • $videoCount video"
        videoCount > 0 -> "$videoCount video"
        else -> "$photoCount foto"
    }
}

@Composable
private fun SelectedGroupMemoriesHeader(
    summary: GroupMemorySummary,
    isExportingPdf: Boolean,
    onBackToGroupList: () -> Unit,
    onExportGroupPdf: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memories_selected_group_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onBackToGroupList,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("memories_group_back_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali ke daftar grup",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Semua Grup")
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "${summary.memoryCount} kenangan",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Text(
                text = summary.trip.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "${summary.dateRangeLabel} • ${formatMediaCountLabel(summary.photoCount, summary.videoCount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Button(
                onClick = onExportGroupPdf,
                enabled = !isExportingPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("memories_group_export_pdf_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ekspor PDF")
                }
            }
        }
    }
}

@Composable
private fun GroupMemoryFolderCard(
    summary: GroupMemorySummary,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val resolvedCoverPath = remember(summary.coverMedia, summary.coverPhotoPath) {
        summary.coverMedia?.let { PhotoStorageHelper.resolveDisplayThumbnailPath(context, it) }
            ?: summary.coverPhotoPath
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_group_card_${summary.trip.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                val coverFile = resolvedCoverPath?.let { File(it) }?.takeIf { it.exists() }
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = "Sampul grup ${summary.trip.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MemoryPhotoPlaceholder(subtitle = null)
                }

                if (summary.coverMedia?.isVideo == true) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Grup",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${summary.memoryCount} kenangan",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.trip.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = summary.dateRangeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${summary.memoryCount} kenangan • ${formatMediaCountLabel(summary.photoCount, summary.videoCount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Buka kenangan grup ${summary.trip.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyGroupMemoriesCard(hasSearchQuery: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memories_empty_group_list")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasSearchQuery) {
                    "Grup Tidak Ditemukan"
                } else {
                    "Belum Ada Grup yang Diikuti"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasSearchQuery) {
                    "Coba kata kunci nama grup atau tanggal lain."
                } else {
                    "Buat grup baru atau gabung dengan kode di tab Grup pada bagian atas layar untuk melihat album kenangan grup di sini."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VisitMemoryThumbnailCard(
    item: VisitMemoryItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val kunjungan = item.kunjungan
    val firstMedia = item.coverMedia
    val resolvedCoverPath = remember(firstMedia, item.coverPhotoPath) {
        firstMedia?.let { PhotoStorageHelper.resolveDisplayThumbnailPath(context, it) }
            ?: item.coverPhotoPath
    }
    val mediaCountText = formatMediaCountLabel(item.photoCount, item.videoCount)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_visit_card_${kunjungan.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gambar sampul besar bergaya thumbnail YouTube (16:9) dari media pertama kunjungan ini
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                val coverFile = resolvedCoverPath?.let { File(it) }?.takeIf { it.exists() }
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = "Sampul ${kunjungan.locationName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MemoryPhotoPlaceholder(subtitle = null)
                }

                if (firstMedia?.isVideo == true) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Badge nama trip di pojok kiri atas
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = item.trip.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }

                // Badge jumlah media di pojok kanan bawah
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = mediaCountText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Metadata kunjungan di bawah thumbnail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = kunjungan.locationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${DateUtils.formatTime(kunjungan.visitHour, kunjungan.visitMinute)} WIB • ${DateUtils.formatIndonesianShortDate(kunjungan.visitDateEpochDay)} • $mediaCountText",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = item.descriptionSnippet.ifBlank {
                            "Ketuk kartu untuk melihat foto/video atau menulis deskripsi perjalanan."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMemoriesCard(
    title: String = "Belum Ada Kenangan Tersimpan"
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memories_empty_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_memories_empty),
                contentDescription = stringResource(R.string.cd_memories_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Potret foto kunjungan hari ini atau tunggu tanggal kunjungan terlewati untuk melihat kartu kenangan per kunjungan di sini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
