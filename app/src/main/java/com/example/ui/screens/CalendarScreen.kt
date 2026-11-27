package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.collab.TripDateComment
import com.example.ui.DisplayVisit
import com.example.ui.TripBuddyUiState
import com.example.ui.components.DateCommentsSection
import com.example.ui.components.LocationCard
import com.example.util.DateUtils

@Composable
fun CalendarScreen(
    uiState: TripBuddyUiState,
    onBackToHome: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onJumpToToday: () -> Unit,
    onDateClick: (Long) -> Unit,
    onAddLocationForSelectedDate: () -> Unit,
    onOpenMapsForVisit: (DisplayVisit) -> Unit,
    onToggleComplete: (DisplayVisit) -> Unit,
    onTakePhotoForVisit: (DisplayVisit) -> Unit,
    onEditVisit: (DisplayVisit) -> Unit,
    onDeleteVisit: (DisplayVisit) -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (TripDateComment) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToHome)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWideLayout = maxWidth >= 780.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1.05f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalendarHeader(onJumpToToday = onJumpToToday)
                    MonthlyCalendarCard(
                        uiState = uiState,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onDateClick = onDateClick
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        SelectedDateAgendaHeader(
                            selectedDateEpochDay = uiState.selectedDateEpochDay,
                            onAddClick = onAddLocationForSelectedDate
                        )
                    }
                    if (uiState.visitsOnSelectedDate.isEmpty()) {
                        item {
                            EmptyDateAgendaCard(
                                selectedDateEpochDay = uiState.selectedDateEpochDay,
                                onAddClick = onAddLocationForSelectedDate
                            )
                        }
                    } else {
                        items(uiState.visitsOnSelectedDate, key = { it.id }) { visit ->
                            LocationCard(
                                visit = visit,
                                showDateBadge = false,
                                photoCount = uiState.photoCountByVisitId[visit.id] ?: 0,
                                onOpenMapsClick = { onOpenMapsForVisit(visit) },
                                onToggleComplete = { onToggleComplete(visit) },
                                onTakePhotoClick = { onTakePhotoForVisit(visit) },
                                onEditClick = { onEditVisit(visit) },
                                onDeleteClick = { onDeleteVisit(visit) }
                            )
                        }
                    }

                    if (uiState.activeSharedTrip != null) {
                        item {
                            DateCommentsSection(
                                selectedDateEpochDay = uiState.selectedDateEpochDay,
                                comments = uiState.commentsOnSelectedDate,
                                currentUserId = uiState.currentUserId,
                                onSendComment = onSendComment,
                                onDeleteComment = onDeleteComment
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    CalendarHeader(onJumpToToday = onJumpToToday)
                }

                item {
                    MonthlyCalendarCard(
                        uiState = uiState,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onDateClick = onDateClick
                    )
                }

                item {
                    SelectedDateAgendaHeader(
                        selectedDateEpochDay = uiState.selectedDateEpochDay,
                        onAddClick = onAddLocationForSelectedDate
                    )
                }

                if (uiState.visitsOnSelectedDate.isEmpty()) {
                    item {
                        EmptyDateAgendaCard(
                            selectedDateEpochDay = uiState.selectedDateEpochDay,
                            onAddClick = onAddLocationForSelectedDate
                        )
                    }
                } else {
                    items(uiState.visitsOnSelectedDate, key = { it.id }) { visit ->
                        LocationCard(
                            visit = visit,
                            showDateBadge = false,
                            photoCount = uiState.photoCountByVisitId[visit.id] ?: 0,
                            onOpenMapsClick = { onOpenMapsForVisit(visit) },
                            onToggleComplete = { onToggleComplete(visit) },
                            onTakePhotoClick = { onTakePhotoForVisit(visit) },
                            onEditClick = { onEditVisit(visit) },
                            onDeleteClick = { onDeleteVisit(visit) }
                        )
                    }
                }

                if (uiState.activeSharedTrip != null) {
                    item {
                        DateCommentsSection(
                            selectedDateEpochDay = uiState.selectedDateEpochDay,
                            comments = uiState.commentsOnSelectedDate,
                            currentUserId = uiState.currentUserId,
                            onSendComment = onSendComment,
                            onDeleteComment = onDeleteComment
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun CalendarHeader(onJumpToToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Kalender Perjalanan",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Ketuk tanggal untuk menambah lokasi kunjungan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(
            onClick = onJumpToToday,
            modifier = Modifier.testTag("calendar_today_button")
        ) {
            Text("Hari Ini")
        }
    }
}

@Composable
private fun MonthlyCalendarCard(
    uiState: TripBuddyUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateClick: (Long) -> Unit
) {
    val daysCount = DateUtils.daysInMonth(uiState.currentYear, uiState.currentMonth)
    val startOffset = DateUtils.firstDayOfWeekInMonth(uiState.currentYear, uiState.currentMonth)
    val totalCells = ((startOffset + daysCount + 6) / 7) * 7
    val weeks = totalCells / 7

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_calendar_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousMonth,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("calendar_prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.cd_prev_month),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = DateUtils.formatMonthYear(uiState.currentYear, uiState.currentMonth),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("calendar_next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_next_month),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                DateUtils.weekdayShortHeaders.forEach { dayName ->
                    Text(
                        text = dayName,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (week in 0 until weeks) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = week * 7 + col
                            val dayNumber = cellIndex - startOffset + 1
                            if (dayNumber in 1..daysCount) {
                                val cellEpochDay = DateUtils.epochDayFromYmd(
                                    uiState.currentYear,
                                    uiState.currentMonth,
                                    dayNumber
                                )
                                val isSelected = cellEpochDay == uiState.selectedDateEpochDay
                                val isToday = cellEpochDay == uiState.todayEpochDay
                                val hasUpcoming = cellEpochDay in uiState.upcomingDatesInMonth
                                val hasMemory = cellEpochDay in uiState.memoryDatesInMonth

                                CalendarDayCell(
                                    dayNumber = dayNumber,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    hasUpcoming = hasUpcoming,
                                    hasMemory = hasMemory,
                                    onClick = { onDateClick(cellEpochDay) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("calendar_day_$dayNumber")
                                )
                            } else {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Rencana",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Kenangan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasUpcoming: Boolean,
    hasMemory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            if (hasUpcoming || hasMemory) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (hasUpcoming) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary
                                )
                        )
                    }
                    if (hasMemory) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.secondary
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDateAgendaHeader(
    selectedDateEpochDay: Long,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lokasi Kunjungan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = DateUtils.formatIndonesianFullDate(selectedDateEpochDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .height(48.dp)
                .testTag("calendar_add_location_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_add_location),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tambah")
        }
    }
}

@Composable
private fun EmptyDateAgendaCard(
    selectedDateEpochDay: Long,
    onAddClick: () -> Unit
) {
    Surface(
        onClick = onAddClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calendar_empty_date_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AddLocationAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "Belum ada lokasi pada ${DateUtils.formatIndonesianShortDate(selectedDateEpochDay)}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ketuk di sini untuk menambahkan destinasi perjalanan baru.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
