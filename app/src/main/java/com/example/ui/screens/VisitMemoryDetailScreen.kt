package com.example.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.ui.DisplayVisit
import com.example.ui.VisitMemoryItem
import com.example.ui.components.MemoryPhotoPlaceholder
import com.example.util.DateUtils
import com.example.util.PhotoStorageHelper
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitMemoryDetailScreen(
    detail: VisitMemoryItem,
    isExportingPdf: Boolean = false,
    onBack: () -> Unit,
    onTakePhotoForVisit: (DisplayVisit) -> Unit,
    onDeletePhoto: (Foto) -> Unit,
    onSaveMemoryNotes: (Kunjungan, String) -> Unit,
    onOpenMapsForVisit: (DisplayVisit) -> Unit,
    onExportPdf: () -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val visit = detail.kunjungan
    val mediaItems = detail.fotos
    val displayVisit = remember(detail) { detail.toDisplayVisit() }
    var mediaPendingDelete by remember { mutableStateOf<Foto?>(null) }
    var activePlayingVideo by remember { mutableStateOf<Foto?>(null) }

    var notesText by rememberSaveable(visit.id, visit.memoryNotes) {
        mutableStateOf(visit.memoryNotes)
    }
    val hasChanges = remember(notesText, visit.memoryNotes) {
        notesText.trim() != visit.memoryNotes.trim()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = visit.locationName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = "${DateUtils.formatTime(visit.visitHour, visit.visitMinute)} WIB • ${DateUtils.formatIndonesianShortDate(visit.visitDateEpochDay)} • ${detail.trip.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("memory_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    if (mediaItems.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        ) {
                            MemoryPhotoPlaceholder(
                                subtitle = "Belum ada foto atau video untuk kunjungan ini"
                            )
                        }
                    } else {
                        val pagerState = rememberPagerState(pageCount = { mediaItems.size })
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                key = { index -> mediaItems[index].id },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .testTag("memory_photo_pager")
                            ) { page ->
                                val media = mediaItems[page]
                                val thumbPath = remember(media.id, media.filePath, media.mediaType) {
                                    PhotoStorageHelper.resolveDisplayThumbnailPath(context, media)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (media.isVideo) {
                                                Modifier.clickable { activePlayingVideo = media }
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    val displayFile = thumbPath?.let { File(it) }?.takeIf { it.exists() }
                                    if (displayFile != null) {
                                        AsyncImage(
                                            model = displayFile,
                                            contentDescription = if (media.isVideo) {
                                                "Thumbnail video kenangan di ${visit.locationName}"
                                            } else {
                                                "Foto kenangan di ${visit.locationName}"
                                            },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        MemoryPhotoPlaceholder(
                                            subtitle = if (media.isVideo) "Video kenangan" else "Foto kenangan"
                                        )
                                    }

                                    if (media.isVideo) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.22f))
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Black.copy(alpha = 0.72f),
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Videocam,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "VIDEO",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }

                                        Surface(
                                            onClick = { activePlayingVideo = media },
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.72f),
                                            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(64.dp)
                                                .testTag("play_video_button_${media.id}")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Putar video kenangan",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "${page + 1}/${mediaItems.size} • ${visit.locationName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { mediaPendingDelete = media },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(48.dp)
                                            .testTag("delete_photo_button_${media.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.cd_delete_photo)
                                        )
                                    }
                                }
                            }

                            if (mediaItems.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.testTag("memory_pager_indicator")
                                ) {
                                    repeat(mediaItems.size) { index ->
                                        val selected = pagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .height(8.dp)
                                                .width(if (selected) 20.dp else 8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.outline
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onTakePhotoForVisit(displayVisit) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("detail_add_photo_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Foto / Video")
                        }

                        OutlinedButton(
                            onClick = onExportPdf,
                            enabled = !isExportingPdf,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("detail_export_pdf_button"),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            if (isExportingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ekspor PDF")
                            }
                        }
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                        text = visit.locationName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${DateUtils.formatIndonesianFullDate(visit.visitDateEpochDay)} • ${DateUtils.formatTime(visit.visitHour, visit.visitMinute)} WIB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val subLabel = buildString {
                                        append("Trip: ${detail.trip.name}")
                                        if (visit.tripTitle.isNotBlank()) {
                                            append(" (${visit.tripTitle})")
                                        }
                                    }
                                    Text(
                                        text = subLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = { onOpenMapsForVisit(displayVisit) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Map,
                                        contentDescription = stringResource(R.string.cd_open_maps),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (visit.planNotes.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "Rencana: ${visit.planNotes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detail_description_input_${visit.id}"),
                                label = { Text("Deskripsi Perjalanan") },
                                placeholder = { Text("Tulis cerita perjalanan Anda di lokasi ini...") },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )

                            if (hasChanges) {
                                OutlinedButton(
                                    onClick = { onSaveMemoryNotes(visit, notesText) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_detail_description_button_${visit.id}"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Simpan Deskripsi")
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    val pendingDelete = mediaPendingDelete
    if (pendingDelete != null) {
        val mediaWord = if (pendingDelete.isVideo) "Video" else "Foto"
        AlertDialog(
            onDismissRequest = { mediaPendingDelete = null },
            title = { Text("Hapus $mediaWord Ini?") },
            text = {
                Text(
                    "$mediaWord akan dihapus dari kunjungan \"${visit.locationName}\" di aplikasi TripBuddy. Salinan di galeri perangkat Anda tetap tersimpan."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mediaPendingDelete = null
                        onDeletePhoto(pendingDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_photo_button")
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { mediaPendingDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    val playingVideo = activePlayingVideo
    if (playingVideo != null) {
        VideoPlayerDialog(
            video = playingVideo,
            locationName = visit.locationName,
            onDismiss = { activePlayingVideo = null }
        )
    }
}

@Composable
private fun VideoPlayerDialog(
    video: Foto,
    locationName: String,
    onDismiss: () -> Unit
) {
    val videoFile = remember(video.filePath) { File(video.filePath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = locationName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_video_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup pemutar video",
                            tint = Color.White
                        )
                    }
                }

                if (videoFile.exists() && videoFile.length() > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setVideoURI(Uri.fromFile(videoFile))
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                }
                            },
                            onRelease = { view ->
                                view.stopPlayback()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "File video tidak ditemukan di perangkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
