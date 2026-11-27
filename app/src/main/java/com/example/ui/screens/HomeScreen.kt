package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.DisplayVisit
import com.example.ui.TripBuddyUiState
import com.example.ui.components.LocationCard
import com.example.ui.theme.LocalAppTheme
import com.example.util.DateUtils
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: TripBuddyUiState,
    onOpenCalendar: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenAddToday: () -> Unit,
    onOpenMapsForVisit: (DisplayVisit) -> Unit,
    onToggleComplete: (DisplayVisit) -> Unit,
    onTakePhotoForVisit: (DisplayVisit) -> Unit,
    onTestNotificationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
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
                            text = uiState.activeSharedTrip?.let { "Trip Bersama: ${it.title}" }
                                ?: "Selamat Menjelajah",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.activeSharedTrip != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Explore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = DateUtils.formatIndonesianShortDate(uiState.todayEpochDay),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                val nearest = uiState.nearestTrip
                if (nearest != null) {
                    NearestTripCountdownCard(
                        visit = nearest,
                        nowMillis = uiState.nowMillis,
                        onOpenMapsClick = { onOpenMapsForVisit(nearest) },
                        onMarkCompletedClick = { onToggleComplete(nearest) },
                        onTakePhotoClick = { onTakePhotoForVisit(nearest) }
                    )
                } else {
                    EmptyNearestTripCard(
                        onPlanInCalendarClick = onOpenCalendar,
                        onQuickAddClick = onOpenAddToday
                    )
                }
            }

            if (uiState.todayVisits.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kunjungan Hari Ini",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onOpenAddToday) {
                            Text("+ Tambah")
                        }
                    }
                }

                items(uiState.todayVisits, key = { "today_${it.id}" }) { visit ->
                    LocationCard(
                        visit = visit,
                        showDateBadge = false,
                        photoCount = uiState.photoCountByVisitId[visit.id] ?: 0,
                        onOpenMapsClick = { onOpenMapsForVisit(visit) },
                        onToggleComplete = { onToggleComplete(visit) },
                        onTakePhotoClick = { onTakePhotoForVisit(visit) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatSummaryCard(
                        title = "Rencana Mendatang",
                        count = uiState.upcomingVisits.size,
                        icon = Icons.Rounded.CalendarMonth,
                        tint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onOpenCalendar,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_stat_upcoming")
                    )
                    StatSummaryCard(
                        title = "Kenangan Tersimpan",
                        count = uiState.dateMemoryGroups.size,
                        icon = Icons.Rounded.AutoAwesome,
                        tint = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = onOpenMemories,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_stat_memories")
                    )
                }
            }

            item {
                NotificationTestCard(onTestNotificationClick = onTestNotificationClick)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Jadwal Perjalanan Berikutnya",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = onOpenCalendar,
                        modifier = Modifier.testTag("home_see_calendar_button")
                    ) {
                        Text("Lihat Kalender")
                    }
                }
            }

            val upcomingList = if (uiState.upcomingVisits.size > 1) {
                uiState.upcomingVisits.drop(1).take(5)
            } else {
                emptyList()
            }

            if (uiState.nearestTrip == null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Pilih tanggal pada tab Kalender untuk menambahkan destinasi perjalanan Anda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (upcomingList.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Belum ada rencana lain setelah trip terdekat.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = onOpenCalendar) {
                                Text("+ Tambah")
                            }
                        }
                    }
                }
            } else {
                items(upcomingList, key = { it.id }) { visit ->
                    LocationCard(
                        visit = visit,
                        showDateBadge = true,
                        photoCount = uiState.photoCountByVisitId[visit.id] ?: 0,
                        onOpenMapsClick = { onOpenMapsForVisit(visit) },
                        onToggleComplete = { onToggleComplete(visit) },
                        onTakePhotoClick = { onTakePhotoForVisit(visit) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun NearestTripCountdownCard(
    visit: DisplayVisit,
    nowMillis: Long,
    onOpenMapsClick: () -> Unit,
    onMarkCompletedClick: () -> Unit,
    onTakePhotoClick: () -> Unit
) {
    val activeTheme = LocalAppTheme.current
    val heroTextColor = activeTheme.heroOnColor
    val countdown = DateUtils.computeCountdownParts(
        visitEpochDay = visit.visitDateEpochDay,
        visitHour = visit.visitHour,
        visitMinute = visit.visitMinute,
        nowMillis = nowMillis
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_nearest_trip_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(activeTheme.heroStart, activeTheme.heroEnd)
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = heroTextColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlightTakeoff,
                                contentDescription = null,
                                tint = heroTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Trip Terdekat",
                                style = MaterialTheme.typography.labelMedium,
                                color = heroTextColor
                            )
                        }
                    }

                    val urgencyLabel = when {
                        countdown.isOngoingOrPassedToday -> "Waktunya Berangkat!"
                        countdown.isToday -> "Hari Ini"
                        countdown.isTomorrow -> "Besok"
                        else -> "${countdown.days} Hari Lagi"
                    }
                    Surface(
                        color = if (countdown.isToday || countdown.isTomorrow) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            heroTextColor.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = urgencyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (countdown.isToday || countdown.isTomorrow) {
                                MaterialTheme.colorScheme.onTertiary
                            } else {
                                heroTextColor
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (visit.tripTitle.isNotBlank()) {
                        Text(
                            text = visit.tripTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = heroTextColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = visit.locationName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = heroTextColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = heroTextColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${DateUtils.formatIndonesianFullDate(visit.visitDateEpochDay)} • ${DateUtils.formatTime(visit.visitHour, visit.visitMinute)} WIB",
                            style = MaterialTheme.typography.bodySmall,
                            color = heroTextColor.copy(alpha = 0.9f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CountdownUnitBox(
                        value = countdown.days,
                        label = "Hari",
                        textColor = heroTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    CountdownUnitBox(
                        value = countdown.hours,
                        label = "Jam",
                        textColor = heroTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    CountdownUnitBox(
                        value = countdown.minutes,
                        label = "Menit",
                        textColor = heroTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    CountdownUnitBox(
                        value = countdown.seconds,
                        label = "Detik",
                        textColor = heroTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenMapsClick,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("home_nearest_open_maps_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Maps",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onTakePhotoClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_nearest_camera_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, heroTextColor.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = heroTextColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_take_photo),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Foto",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onMarkCompletedClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_nearest_complete_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, heroTextColor.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = heroTextColor)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Selesai",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownUnitBox(
    value: Long,
    label: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = textColor.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = textColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun EmptyNearestTripCard(
    onPlanInCalendarClick: () -> Unit,
    onQuickAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_empty_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_coastal),
                contentDescription = stringResource(R.string.cd_hero_coastal),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Belum Ada Trip Terdekat",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tambahkan lokasi kunjungan pada kalender untuk melihat hitung mundur perjalanan Anda di sini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPlanInCalendarClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_plan_now_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Buka Kalender")
                    }
                    OutlinedButton(
                        onClick = onQuickAddClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_quick_add_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("+ Tambah Hari Ini")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NotificationTestCard(
    onTestNotificationClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Pengingat Jadwal",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Uji notifikasi lokal dalam 10 detik",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onTestNotificationClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("test_notification_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.test_notification_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
