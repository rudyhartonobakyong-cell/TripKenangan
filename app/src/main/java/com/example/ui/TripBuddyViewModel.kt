package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.collab.CollabRepository
import com.example.collab.FirestoreConnectionTestResult
import com.example.collab.SharedTrip
import com.example.collab.SharedVisit
import com.example.collab.TripDateComment
import com.example.collab.TripMember
import com.example.collab.extractFirestoreException
import com.example.collab.formatFirestoreErrorForUi
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.data.TripRepository
import com.example.drive.DriveAccountConnection
import com.example.drive.DriveApiClient
import com.example.drive.DriveAuthManager
import com.example.drive.DriveAuthOutcome
import com.example.drive.DriveBackupEngine
import com.example.drive.DriveBackupLastStatus
import com.example.drive.DriveBackupPreferences
import com.example.drive.DriveBackupProgress
import com.example.drive.DriveBackupScheduleSettings
import com.example.drive.DriveBackupScheduler
import com.example.drive.DriveRemoteBackupSummary
import com.example.drive.DriveRestoreConfirmationSummary
import com.example.notifications.AlarmScheduleResult
import com.example.notifications.ReminderPreferences
import com.example.notifications.ReminderTimingOption
import com.example.notifications.TripAlarmScheduler
import com.example.notifications.TripNotificationHelper
import com.example.notifications.UserReminderSettings
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.ThemePreferences
import com.example.util.BackupEstimate
import com.example.util.BackupExportResult
import com.example.util.BackupInspectionSummary
import com.example.util.BackupManager
import com.example.util.BackupProgress
import com.example.util.DateUtils
import com.example.util.ExportedPdfResult
import com.example.util.PdfExportOptions
import com.example.util.PdfExportProgress
import com.example.util.PdfExportScope
import com.example.util.PdfExporter
import com.example.util.PdfPhotoAlbumSpec
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs

enum class TripTab {
    BERANDA,
    KALENDER,
    KENANGAN
}

enum class SpaceMode {
    PRIBADI,
    GRUP
}

enum class MemoriesFilterTab {
    SEMUA,
    PRIBADI,
    GRUP
}

sealed interface Workspace {
    data object Local : Workspace
    data class Shared(val tripId: String) : Workspace

    fun storageKey(): String = when (this) {
        Local -> Trip.PERSONAL_TRIP_ID
        is Shared -> Trip.sharedTripUuid(tripId)
    }
}

data class VisitMemoryItem(
    val kunjungan: Kunjungan,
    val trip: Trip,
    val fotos: List<Foto>
) {
    val coverMedia: Foto? get() = fotos.firstOrNull()
    val coverPhotoPath: String? get() = fotos.firstOrNull()?.filePath
    val photoCount: Int get() = fotos.count { !it.isVideo }
    val videoCount: Int get() = fotos.count { it.isVideo }
    val descriptionSnippet: String get() = kunjungan.memoryNotes.ifBlank { kunjungan.planNotes }.trim()
    fun toDisplayVisit(): DisplayVisit = DisplayVisit.fromKunjungan(kunjungan, trip.name)
}

data class GroupMemorySummary(
    val trip: Trip,
    val dateRangeLabel: String,
    val memoryCount: Int,
    val photoCount: Int,
    val videoCount: Int = 0,
    val coverPhotoPath: String?,
    val coverMedia: Foto? = null,
    val latestSortKey: Long
)

private data class ActiveTripCollabData(
    val visits: List<SharedVisit> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val comments: List<TripDateComment> = emptyList()
)

private data class RoomBaseSnapshot(
    val allTrips: List<Trip>,
    val allKunjungan: List<Kunjungan>,
    val allFotos: List<Foto>,
    val memoryItems: List<VisitMemoryItem>,
    val activeDetail: VisitMemoryItem?
)

private data class NavAndModalState(
    val selectedTab: TripTab = TripTab.BERANDA,
    val spaceMode: SpaceMode = SpaceMode.PRIBADI,
    val showGroupDirectory: Boolean = false,
    val activeWorkspace: Workspace = Workspace.Local,
    val currentYear: Int = DateUtils.currentYearAndMonth().first,
    val currentMonth: Int = DateUtils.currentYearAndMonth().second,
    val selectedDateEpochDay: Long = DateUtils.todayEpochDay(),
    val selectedMemoriesTab: MemoriesFilterTab = MemoriesFilterTab.SEMUA,
    val selectedMemoryGroupId: String? = null,
    val memorySearchQuery: String = "",
    val activeMapsVisit: DisplayVisit? = null,
    val activeAddEditDateEpochDay: Long? = null,
    val editingVisit: DisplayVisit? = null,
    val activeMemoryVisitId: String? = null,
    val showCameraVisitPicker: Boolean = false,
    val showCreateTripDialog: Boolean = false,
    val createTripError: String? = null,
    val showJoinTripDialog: Boolean = false,
    val joinTripPrefillCode: String = "",
    val joinTripError: String? = null,
    val isSubmittingCollab: Boolean = false,
    val showInviteSheetForTrip: SharedTrip? = null,
    val showMembersScreen: Boolean = false,
    val showAccountSheet: Boolean = false,
    val isTestingFirestore: Boolean = false,
    val firestoreTestResult: FirestoreConnectionTestResult? = null,
    val collabErrorMessage: String? = null,
    val isExportingPdf: Boolean = false,
    val pdfExportOptions: PdfExportOptions? = null,
    val pdfAllowThisVisitScope: Boolean = false,
    val pdfExportProgress: PdfExportProgress? = null,
    val exportedPdfResult: ExportedPdfResult? = null,
    val reminderSettings: UserReminderSettings = UserReminderSettings(),
    val backupIncludeVideos: Boolean = true,
    val backupEstimate: BackupEstimate? = null,
    val isEstimatingBackup: Boolean = false,
    val backupProgress: BackupProgress? = null,
    val pendingBackupImportSummary: BackupInspectionSummary? = null,
    val backupStatusDialogTitle: String? = null,
    val backupStatusDialogMessage: String? = null,
    val backupStatusIsError: Boolean = false,
    val driveConnection: DriveAccountConnection = DriveAccountConnection(),
    val driveScheduleSettings: DriveBackupScheduleSettings = DriveBackupScheduleSettings(),
    val driveLastStatus: DriveBackupLastStatus = DriveBackupLastStatus(),
    val isDriveActionLoading: Boolean = false,
    val driveProgress: DriveBackupProgress? = null,
    val availableDriveBackups: List<DriveRemoteBackupSummary>? = null,
    val pendingDriveRestoreConfirm: DriveRestoreConfirmationSummary? = null
)

data class TripBuddyUiState(
    val currentUserId: String = "",
    val currentUserName: String = "",
    val currentUserEmail: String = "",
    val currentUserPhotoUrl: String = "",
    val activeProjectId: String = "",
    val activeDatabaseId: String = "",
    val selectedTab: TripTab = TripTab.BERANDA,
    val spaceMode: SpaceMode = SpaceMode.PRIBADI,
    val showGroupDirectory: Boolean = false,
    val activeSpaceTitle: String = "Pribadi - hanya di HP ini",
    val activeWorkspace: Workspace = Workspace.Local,
    val allTrips: List<Trip> = emptyList(),
    val sharedTrips: List<SharedTrip> = emptyList(),
    val activeSharedTrip: SharedTrip? = null,
    val activeTripMembers: List<TripMember> = emptyList(),
    val commentsOnSelectedDate: List<TripDateComment> = emptyList(),
    val currentYear: Int = DateUtils.currentYearAndMonth().first,
    val currentMonth: Int = DateUtils.currentYearAndMonth().second,
    val selectedDateEpochDay: Long = DateUtils.todayEpochDay(),
    val todayEpochDay: Long = DateUtils.todayEpochDay(),
    val nowMillis: Long = System.currentTimeMillis(),
    val allVisits: List<DisplayVisit> = emptyList(),
    val todayVisits: List<DisplayVisit> = emptyList(),
    val todayVisitsAllTrips: List<DisplayVisit> = emptyList(),
    val defaultCameraVisit: DisplayVisit? = null,
    val upcomingVisits: List<DisplayVisit> = emptyList(),
    val nearestTrip: DisplayVisit? = null,
    val visitMemories: List<VisitMemoryItem> = emptyList(),
    val filteredVisitMemories: List<VisitMemoryItem> = emptyList(),
    val groupMemorySummaries: List<GroupMemorySummary> = emptyList(),
    val selectedMemoryGroupSummary: GroupMemorySummary? = null,
    val photoCountByVisitId: Map<String, Int> = emptyMap(),
    val activeVisitMemoryDetail: VisitMemoryItem? = null,
    val selectedMemoriesTab: MemoriesFilterTab = MemoriesFilterTab.SEMUA,
    val selectedMemoryGroupId: String? = null,
    val memorySearchQuery: String = "",
    val visitsOnSelectedDate: List<DisplayVisit> = emptyList(),
    val upcomingDatesInMonth: Set<Long> = emptySet(),
    val memoryDatesInMonth: Set<Long> = emptySet(),
    val activeMapsVisit: DisplayVisit? = null,
    val activeAddEditDateEpochDay: Long? = null,
    val editingVisit: DisplayVisit? = null,
    val showCameraVisitPicker: Boolean = false,
    val showCreateTripDialog: Boolean = false,
    val createTripError: String? = null,
    val showJoinTripDialog: Boolean = false,
    val joinTripPrefillCode: String = "",
    val joinTripError: String? = null,
    val isSubmittingCollab: Boolean = false,
    val showInviteSheetForTrip: SharedTrip? = null,
    val showMembersScreen: Boolean = false,
    val showAccountSheet: Boolean = false,
    val isTestingFirestore: Boolean = false,
    val firestoreTestResult: FirestoreConnectionTestResult? = null,
    val collabErrorMessage: String? = null,
    val isExportingPdf: Boolean = false,
    val pdfExportOptions: PdfExportOptions? = null,
    val pdfAllowThisVisitScope: Boolean = false,
    val pdfExportPreviewSpec: PdfPhotoAlbumSpec? = null,
    val pdfExportProgress: PdfExportProgress? = null,
    val exportedPdfResult: ExportedPdfResult? = null,
    val reminderSettings: UserReminderSettings = UserReminderSettings(),
    val backupIncludeVideos: Boolean = true,
    val backupEstimate: BackupEstimate? = null,
    val isEstimatingBackup: Boolean = false,
    val backupProgress: BackupProgress? = null,
    val pendingBackupImportSummary: BackupInspectionSummary? = null,
    val backupStatusDialogTitle: String? = null,
    val backupStatusDialogMessage: String? = null,
    val backupStatusIsError: Boolean = false,
    val driveConnection: DriveAccountConnection = DriveAccountConnection(),
    val driveScheduleSettings: DriveBackupScheduleSettings = DriveBackupScheduleSettings(),
    val driveLastStatus: DriveBackupLastStatus = DriveBackupLastStatus(),
    val isDriveActionLoading: Boolean = false,
    val driveProgress: DriveBackupProgress? = null,
    val availableDriveBackups: List<DriveRemoteBackupSummary>? = null,
    val pendingDriveRestoreConfirm: DriveRestoreConfirmationSummary? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class TripBuddyViewModel(
    private val localRepository: TripRepository,
    private val collabRepository: CollabRepository,
    private val alarmScheduler: TripAlarmScheduler,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String,
    private val currentUserPhotoUrl: String
) : ViewModel() {

    private val navAndModalState = MutableStateFlow(NavAndModalState())

    private val _snackbarEvents = Channel<String>(Channel.BUFFERED)
    val snackbarEvents = _snackbarEvents.receiveAsFlow()

    private val _shareBackupEvents = Channel<BackupExportResult>(Channel.BUFFERED)
    val shareBackupEvents = _shareBackupEvents.receiveAsFlow()

    private val _themeRestoredEvents = Channel<AppThemeOption>(Channel.BUFFERED)
    val themeRestoredEvents = _themeRestoredEvents.receiveAsFlow()

    private val _driveConsentResolutionEvents = Channel<IntentSender>(Channel.BUFFERED)
    val driveConsentResolutionEvents = _driveConsentResolutionEvents.receiveAsFlow()

    private enum class PendingDriveAction {
        CONNECT_ONLY,
        BACKUP_NOW,
        FETCH_VERSIONS_FOR_RESTORE,
        INSPECT_VERSION,
        RESTORE_SELECTED
    }

    private var pendingDriveAction: PendingDriveAction? = null
    private var pendingDriveSummaryForAction: DriveRemoteBackupSummary? = null

    private val tickerMillis = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val todayEpochDayFlow = tickerMillis
        .map { DateUtils.todayEpochDay(it) }
        .distinctUntilChanged()

    private val lastSyncedSharedTrips = MutableStateFlow<List<SharedTrip>>(emptyList())
    private val reminderSyncMutex = Mutex()
    @Volatile
    private var latestRoomSnapshot: RoomBaseSnapshot? = null

    init {
        alarmScheduler.setActiveUser(currentUserId)
        val initialReminderSettings = alarmScheduler.getUserReminderSettings(currentUserId)
        navAndModalState.update { it.copy(reminderSettings = initialReminderSettings) }

        viewModelScope.launch {
            localRepository.ensurePersonalTripExists()
            rescheduleAllLocalReminders()
        }
        // Background sync: keep ALL shared trips and ALL their visits synced into Room
        // and keep local AlarmManager reminders synchronized with Firestore changes.
        viewModelScope.launch {
            lastSyncedSharedTrips
                .flatMapLatest { trips ->
                    if (trips.isEmpty()) {
                        flowOf(Unit)
                    } else {
                        trips.map { trip ->
                            collabRepository.observeTripVisits(trip.id)
                                .onEach { remoteVisits ->
                                    val removedIds = localRepository.syncSharedVisitsToRoom(trip, remoteVisits)
                                    removedIds.forEach { alarmScheduler.cancelVisitReminder(it) }
                                    rescheduleAllLocalReminders()
                                }
                                .catch { error ->
                                    val firestoreError = extractFirestoreException(error)
                                    if (firestoreError == null && error is CancellationException) throw error
                                }
                                .map { Unit }
                        }.merge()
                    }
                }
                .collect {}
        }
    }

    private suspend fun rescheduleAllLocalReminders() {
        reminderSyncMutex.withLock {
            withContext(Dispatchers.IO) {
                val tripsById = localRepository.getAllTripsOnce().associateBy { it.id }
                val allVisits = localRepository.getAllKunjunganOnce()
                val settings = navAndModalState.value.reminderSettings
                alarmScheduler.rescheduleAllReminders(
                    visits = allVisits,
                    tripsById = tripsById,
                    settings = settings
                )
            }
        }
    }

    private fun resolveGroupOrSpaceName(tripId: String, remoteTripId: String?): String {
        val fromShared = remoteTripId?.let { rId ->
            uiState.value.sharedTrips.find { it.id == rId }?.title
        }
        if (!fromShared.isNullOrBlank()) return fromShared
        val fromRoom = latestRoomSnapshot?.allTrips?.find { it.id == tripId }?.name
        if (!fromRoom.isNullOrBlank()) return fromRoom
        return if (remoteTripId != null) "Trip Bersama" else "Pribadi"
    }

    private fun extractErrorMessage(error: Throwable, fallback: String): String {
        return formatFirestoreErrorForUi(error, fallback)
    }

    private val sharedTripsFlow = collabRepository.observeUserTrips(currentUserId)
        .onEach { trips ->
            lastSyncedSharedTrips.value = trips
            localRepository.syncSharedTripsToRoom(trips)
            rescheduleAllLocalReminders()
            val currentSharedId = (navAndModalState.value.activeWorkspace as? Workspace.Shared)?.tripId
            if (currentSharedId != null && trips.none { it.id == currentSharedId }) {
                navAndModalState.update { state ->
                    state.copy(
                        activeWorkspace = Workspace.Local,
                        showMembersScreen = false,
                        showGroupDirectory = state.spaceMode == SpaceMode.GRUP
                    )
                }
            }
        }
        .catch { error ->
            val firestoreError = extractFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val msg = extractErrorMessage(error, "Gagal memuat trip bersama.")
            navAndModalState.update { it.copy(collabErrorMessage = msg) }
            // Preserve last known trips so online errors never drop cached UI state
            emit(lastSyncedSharedTrips.value)
        }

    private val activeSharedTripIdFlow = navAndModalState
        .map { (it.activeWorkspace as? Workspace.Shared)?.tripId }
        .distinctUntilChanged()

    private val activeTripCollabFlow = activeSharedTripIdFlow.flatMapLatest { tripId ->
        if (tripId == null) {
            flowOf(ActiveTripCollabData())
        } else {
            val visitsFlow = collabRepository.observeTripVisits(tripId).catch { error ->
                val firestoreError = extractFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                navAndModalState.update {
                    it.copy(collabErrorMessage = extractErrorMessage(error, "Gagal memuat lokasi trip bersama."))
                }
                emit(emptyList())
            }
            val membersFlow = collabRepository.observeTripMembers(tripId).catch { error ->
                val firestoreError = extractFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
            val commentsFlow = collabRepository.observeTripComments(tripId).catch { error ->
                val firestoreError = extractFirestoreException(error)
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
            combine(visitsFlow, membersFlow, commentsFlow) { visits, members, comments ->
                ActiveTripCollabData(visits = visits, members = members, comments = comments)
            }
        }
    }

    // Kenangan is queried directly from Room across ALL trips (Pribadi + all shared trips)
    private val roomMemoriesFlow = todayEpochDayFlow.flatMapLatest { today ->
        localRepository.observeMemoryKunjungan(today).map { rows ->
            rows.map { row ->
                VisitMemoryItem(
                    kunjungan = row.kunjungan,
                    trip = row.trip ?: Trip.personalTrip(),
                    fotos = row.fotos.sortedBy { it.createdAtMillis }
                )
            }
        }
    }

    private val activeDetailFlow = navAndModalState
        .map { it.activeMemoryVisitId }
        .distinctUntilChanged()
        .flatMapLatest { visitId ->
            if (visitId == null) {
                flowOf(null)
            } else {
                localRepository.observeKunjunganDetailById(visitId).map { row ->
                    row?.let {
                        VisitMemoryItem(
                            kunjungan = it.kunjungan,
                            trip = it.trip ?: Trip.personalTrip(),
                            fotos = it.fotos.sortedBy { f -> f.createdAtMillis }
                        )
                    }
                }
            }
        }

    private val roomBaseFlow = combine(
        localRepository.allTrips,
        localRepository.allKunjungan,
        localRepository.allFotos,
        roomMemoriesFlow,
        activeDetailFlow
    ) { trips, kunjunganList, fotos, memoryItems, activeDetail ->
        RoomBaseSnapshot(
            allTrips = trips,
            allKunjungan = kunjunganList,
            allFotos = fotos,
            memoryItems = memoryItems,
            activeDetail = activeDetail
        ).also { latestRoomSnapshot = it }
    }

    val uiState: StateFlow<TripBuddyUiState> = combine(
        roomBaseFlow,
        sharedTripsFlow,
        activeTripCollabFlow,
        navAndModalState,
        tickerMillis
    ) { room, sharedTrips, collabData, nav, nowMillis ->
        val today = DateUtils.todayEpochDay(nowMillis)
        val tripById = room.allTrips.associateBy { it.id }

        val activeSharedTrip = (nav.activeWorkspace as? Workspace.Shared)?.let { shared ->
            sharedTrips.find { it.id == shared.tripId }
        }

        val activeTripUuid = nav.activeWorkspace.storageKey()
        val roomVisitsForWorkspace = room.allKunjungan
            .filter { it.tripId == activeTripUuid }
            .map { entity ->
                DisplayVisit.fromKunjungan(
                    entity = entity,
                    tripName = tripById[entity.tripId]?.name
                        ?: activeSharedTrip?.title
                        ?: "Pribadi"
                )
            }

        val visits: List<DisplayVisit> = when (nav.activeWorkspace) {
            Workspace.Local -> roomVisitsForWorkspace
            is Workspace.Shared -> {
                if (collabData.visits.isNotEmpty()) {
                    val sharedTripTitle = activeSharedTrip?.title ?: "Trip Bersama"
                    collabData.visits.map { DisplayVisit.fromShared(it, sharedTripTitle) }
                } else {
                    roomVisitsForWorkspace
                }
            }
        }

        val photoCountByVisitId = room.allFotos.groupingBy { it.kunjunganId }.eachCount()

        val todayVisits = visits
            .filter { it.visitDateEpochDay == today }
            .sortedWith(compareBy<DisplayVisit> { it.visitHour }.thenBy { it.visitMinute })

        val todayVisitsAllTrips = room.allKunjungan
            .filter { it.visitDateEpochDay == today }
            .map { entity ->
                DisplayVisit.fromKunjungan(
                    entity = entity,
                    tripName = tripById[entity.tripId]?.name ?: "Pribadi"
                )
            }
            .sortedWith(compareBy<DisplayVisit> { it.visitHour }.thenBy { it.visitMinute })

        val defaultCameraVisit = findClosestVisitToNow(todayVisitsAllTrips, nowMillis)

        val upcoming = visits
            .filter { visit -> !visit.completedManually && visit.visitDateEpochDay >= today }
            .sortedWith(
                compareBy<DisplayVisit> { it.visitDateEpochDay }
                    .thenBy { it.visitHour }
                    .thenBy { it.visitMinute }
            )

        val allGroupSummaries = buildGroupMemorySummaries(
            roomTrips = room.allTrips,
            sharedTrips = sharedTrips,
            allKunjungan = room.allKunjungan,
            memoryItems = room.memoryItems
        )
        val queryLower = nav.memorySearchQuery.trim().lowercase()
        val filteredGroupSummaries = if (queryLower.isEmpty()) {
            allGroupSummaries
        } else {
            allGroupSummaries.filter { summary ->
                summary.trip.name.lowercase().contains(queryLower) ||
                    summary.dateRangeLabel.lowercase().contains(queryLower)
            }
        }
        val selectedMemoryGroupSummary = nav.selectedMemoryGroupId?.let { groupId ->
            allGroupSummaries.find { it.trip.id == groupId }
        }
        val filteredVisitMemories = filterMemoriesForTab(
            memoryItems = room.memoryItems,
            selectedTab = nav.selectedMemoriesTab,
            selectedGroupId = selectedMemoryGroupSummary?.trip?.id ?: nav.selectedMemoryGroupId,
            searchQuery = nav.memorySearchQuery
        )

        val selectedVisits = visits.filter { it.visitDateEpochDay == nav.selectedDateEpochDay }

        val commentsOnSelectedDate = if (nav.activeWorkspace is Workspace.Shared) {
            collabData.comments.filter { it.dateEpochDay == nav.selectedDateEpochDay }
        } else {
            emptyList()
        }

        val daysInActiveMonth = DateUtils.daysInMonth(nav.currentYear, nav.currentMonth)
        val monthStart = DateUtils.epochDayFromYmd(nav.currentYear, nav.currentMonth, 1)
        val monthEnd = DateUtils.epochDayFromYmd(nav.currentYear, nav.currentMonth, daysInActiveMonth)

        val upcomingDates = mutableSetOf<Long>()
        val memoryDatesInMonth = mutableSetOf<Long>()
        visits.forEach { visit ->
            if (visit.visitDateEpochDay in monthStart..monthEnd) {
                val hasPhotos = (photoCountByVisitId[visit.id] ?: 0) > 0
                if (!visit.completedManually && visit.visitDateEpochDay >= today && !hasPhotos) {
                    upcomingDates.add(visit.visitDateEpochDay)
                } else {
                    memoryDatesInMonth.add(visit.visitDateEpochDay)
                }
            }
        }

        val refreshedMapsVisit = nav.activeMapsVisit?.let { current ->
            visits.find { it.id == current.id }
                ?: room.allKunjungan.find { it.id == current.id }?.let {
                    DisplayVisit.fromKunjungan(it, tripById[it.tripId]?.name ?: current.tripName)
                }
                ?: current
        }

        val refreshedInviteTrip = nav.showInviteSheetForTrip?.let { current ->
            sharedTrips.find { it.id == current.id } ?: current
        }

        val activeSpaceTitle = formatActiveSpaceTitle(
            spaceMode = nav.spaceMode,
            showGroupDirectory = nav.showGroupDirectory,
            activeSharedTrip = activeSharedTrip,
            memberCount = activeSharedTrip?.let { maxOf(collabData.members.size, it.memberUids.size) } ?: 0,
            totalGroups = sharedTrips.size
        )

        val pdfPreviewSpec = nav.pdfExportOptions?.let { options ->
            PdfExporter.buildPhotoAlbumSpec(
                options = options,
                allTrips = room.allTrips,
                allKunjungan = room.allKunjungan,
                allFotos = room.allFotos,
                resolveTakenTimeMillis = { it.createdAtMillis }
            )
        }

        TripBuddyUiState(
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            currentUserEmail = currentUserEmail,
            currentUserPhotoUrl = currentUserPhotoUrl,
            activeProjectId = collabRepository.getActiveProjectId(),
            activeDatabaseId = collabRepository.getActiveDatabaseId(),
            selectedTab = nav.selectedTab,
            spaceMode = nav.spaceMode,
            showGroupDirectory = nav.showGroupDirectory,
            activeSpaceTitle = activeSpaceTitle,
            activeWorkspace = nav.activeWorkspace,
            allTrips = room.allTrips,
            sharedTrips = sharedTrips,
            activeSharedTrip = activeSharedTrip,
            activeTripMembers = if (nav.activeWorkspace is Workspace.Shared) collabData.members else emptyList(),
            commentsOnSelectedDate = commentsOnSelectedDate,
            currentYear = nav.currentYear,
            currentMonth = nav.currentMonth,
            selectedDateEpochDay = nav.selectedDateEpochDay,
            todayEpochDay = today,
            nowMillis = nowMillis,
            allVisits = visits,
            todayVisits = todayVisits,
            todayVisitsAllTrips = todayVisitsAllTrips,
            defaultCameraVisit = defaultCameraVisit,
            upcomingVisits = upcoming,
            nearestTrip = upcoming.firstOrNull(),
            visitMemories = room.memoryItems,
            filteredVisitMemories = filteredVisitMemories,
            groupMemorySummaries = filteredGroupSummaries,
            selectedMemoryGroupSummary = selectedMemoryGroupSummary,
            photoCountByVisitId = photoCountByVisitId,
            activeVisitMemoryDetail = room.activeDetail,
            selectedMemoriesTab = nav.selectedMemoriesTab,
            selectedMemoryGroupId = selectedMemoryGroupSummary?.trip?.id,
            memorySearchQuery = nav.memorySearchQuery,
            visitsOnSelectedDate = selectedVisits,
            upcomingDatesInMonth = upcomingDates,
            memoryDatesInMonth = memoryDatesInMonth,
            activeMapsVisit = refreshedMapsVisit,
            activeAddEditDateEpochDay = nav.activeAddEditDateEpochDay,
            editingVisit = nav.editingVisit,
            showCameraVisitPicker = nav.showCameraVisitPicker,
            showCreateTripDialog = nav.showCreateTripDialog,
            createTripError = nav.createTripError,
            showJoinTripDialog = nav.showJoinTripDialog,
            joinTripPrefillCode = nav.joinTripPrefillCode,
            joinTripError = nav.joinTripError,
            isSubmittingCollab = nav.isSubmittingCollab,
            showInviteSheetForTrip = refreshedInviteTrip,
            showMembersScreen = nav.showMembersScreen,
            showAccountSheet = nav.showAccountSheet,
            isTestingFirestore = nav.isTestingFirestore,
            firestoreTestResult = nav.firestoreTestResult,
            collabErrorMessage = nav.collabErrorMessage,
            isExportingPdf = nav.isExportingPdf,
            pdfExportOptions = nav.pdfExportOptions,
            pdfAllowThisVisitScope = nav.pdfAllowThisVisitScope,
            pdfExportPreviewSpec = pdfPreviewSpec,
            pdfExportProgress = nav.pdfExportProgress,
            exportedPdfResult = nav.exportedPdfResult,
            reminderSettings = nav.reminderSettings,
            backupIncludeVideos = nav.backupIncludeVideos,
            backupEstimate = nav.backupEstimate,
            isEstimatingBackup = nav.isEstimatingBackup,
            backupProgress = nav.backupProgress,
            pendingBackupImportSummary = nav.pendingBackupImportSummary,
            backupStatusDialogTitle = nav.backupStatusDialogTitle,
            backupStatusDialogMessage = nav.backupStatusDialogMessage,
            backupStatusIsError = nav.backupStatusIsError,
            driveConnection = nav.driveConnection,
            driveScheduleSettings = nav.driveScheduleSettings,
            driveLastStatus = nav.driveLastStatus,
            isDriveActionLoading = nav.isDriveActionLoading,
            driveProgress = nav.driveProgress,
            availableDriveBackups = nav.availableDriveBackups,
            pendingDriveRestoreConfirm = nav.pendingDriveRestoreConfirm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripBuddyUiState(
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            currentUserEmail = currentUserEmail,
            currentUserPhotoUrl = currentUserPhotoUrl,
            activeProjectId = collabRepository.getActiveProjectId(),
            activeDatabaseId = collabRepository.getActiveDatabaseId()
        )
    )

    fun selectTab(tab: TripTab) {
        val current = navAndModalState.value
        if (current.spaceMode == SpaceMode.GRUP && current.showGroupDirectory) {
            if (current.activeWorkspace is Workspace.Shared || tab == TripTab.KENANGAN) {
                navAndModalState.update {
                    it.copy(
                        selectedTab = tab,
                        showGroupDirectory = false,
                        showMembersScreen = false,
                        activeMemoryVisitId = null
                    )
                }
            } else {
                navAndModalState.update {
                    it.copy(
                        selectedTab = tab,
                        showGroupDirectory = true,
                        showMembersScreen = false,
                        activeMemoryVisitId = null
                    )
                }
                _snackbarEvents.trySend("Pilih, buat, atau gabung grup terlebih dahulu")
            }
            return
        }
        navAndModalState.update {
            it.copy(
                selectedTab = tab,
                showMembersScreen = false,
                activeMemoryVisitId = null
            )
        }
    }

    fun selectSpaceMode(mode: SpaceMode) {
        when (mode) {
            SpaceMode.PRIBADI -> {
                navAndModalState.update {
                    it.copy(
                        spaceMode = SpaceMode.PRIBADI,
                        showGroupDirectory = false,
                        activeWorkspace = Workspace.Local,
                        showMembersScreen = false,
                        activeMapsVisit = null,
                        activeAddEditDateEpochDay = null,
                        editingVisit = null
                    )
                }
            }

            SpaceMode.GRUP -> {
                navAndModalState.update {
                    it.copy(
                        spaceMode = SpaceMode.GRUP,
                        showGroupDirectory = true,
                        showMembersScreen = false,
                        activeMapsVisit = null,
                        activeAddEditDateEpochDay = null,
                        editingVisit = null
                    )
                }
            }
        }
    }

    fun openGroupDirectory() {
        navAndModalState.update {
            it.copy(
                spaceMode = SpaceMode.GRUP,
                showGroupDirectory = true,
                showMembersScreen = false,
                activeMapsVisit = null,
                activeAddEditDateEpochDay = null,
                editingVisit = null
            )
        }
    }

    fun selectGroupTrip(trip: SharedTrip, openMembersPage: Boolean = false) {
        navAndModalState.update {
            it.copy(
                spaceMode = SpaceMode.GRUP,
                showGroupDirectory = false,
                activeWorkspace = Workspace.Shared(trip.id),
                showMembersScreen = openMembersPage,
                activeMapsVisit = null,
                activeAddEditDateEpochDay = null,
                editingVisit = null
            )
        }
    }

    fun selectWorkspace(workspace: Workspace) {
        navAndModalState.update {
            it.copy(
                spaceMode = if (workspace is Workspace.Shared) SpaceMode.GRUP else SpaceMode.PRIBADI,
                showGroupDirectory = false,
                activeWorkspace = workspace,
                showMembersScreen = false,
                activeMapsVisit = null,
                activeAddEditDateEpochDay = null,
                editingVisit = null
            )
        }
    }

    fun clearCollabError() {
        navAndModalState.update { it.copy(collabErrorMessage = null) }
    }

    fun openCreateTripDialog() {
        navAndModalState.update { it.copy(showCreateTripDialog = true, createTripError = null) }
    }

    fun dismissCreateTripDialog() {
        navAndModalState.update { it.copy(showCreateTripDialog = false, createTripError = null) }
    }

    fun createSharedTrip(title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        navAndModalState.update { it.copy(isSubmittingCollab = true, createTripError = null) }
        viewModelScope.launch {
            val result = collabRepository.createSharedTrip(
                title = cleanTitle,
                displayName = currentUserName,
                photoUrl = currentUserPhotoUrl
            )
            result.onSuccess { trip ->
                localRepository.syncSharedTripToRoom(trip)
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        showCreateTripDialog = false,
                        createTripError = null,
                        spaceMode = SpaceMode.GRUP,
                        showGroupDirectory = false,
                        activeWorkspace = Workspace.Shared(trip.id),
                        showInviteSheetForTrip = trip
                    )
                }
                _snackbarEvents.trySend("Grup \"${trip.title}\" berhasil dibuat")
            }.onFailure { error ->
                val errorText = extractErrorMessage(error, "Gagal membuat grup.")
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        createTripError = errorText
                    )
                }
                _snackbarEvents.trySend(errorText)
            }
        }
    }

    fun openJoinTripDialog(prefillCode: String = "") {
        navAndModalState.update {
            it.copy(
                showJoinTripDialog = true,
                joinTripPrefillCode = prefillCode,
                joinTripError = null
            )
        }
    }

    fun dismissJoinTripDialog() {
        navAndModalState.update {
            it.copy(showJoinTripDialog = false, joinTripError = null, joinTripPrefillCode = "")
        }
    }

    fun joinTripByCode(rawInput: String) {
        navAndModalState.update { it.copy(isSubmittingCollab = true, joinTripError = null) }
        val alreadyJoined = uiState.value.sharedTrips.map { it.id }.toSet()
        viewModelScope.launch {
            val result = collabRepository.joinTripByCode(
                rawInput = rawInput,
                displayName = currentUserName,
                photoUrl = currentUserPhotoUrl,
                alreadyJoinedTripIds = alreadyJoined
            )
            result.onSuccess { tripId ->
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        showJoinTripDialog = false,
                        joinTripPrefillCode = "",
                        joinTripError = null,
                        spaceMode = SpaceMode.GRUP,
                        showGroupDirectory = false,
                        activeWorkspace = Workspace.Shared(tripId),
                        selectedTab = TripTab.KALENDER
                    )
                }
                _snackbarEvents.trySend("Berhasil bergabung ke grup!")
            }.onFailure { error ->
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        joinTripError = extractErrorMessage(error, "Gagal bergabung dengan kode tersebut.")
                    )
                }
            }
        }
    }

    fun openInviteSheet(trip: SharedTrip? = uiState.value.activeSharedTrip) {
        if (trip == null) return
        navAndModalState.update { it.copy(showInviteSheetForTrip = trip) }
    }

    fun dismissInviteSheet() {
        navAndModalState.update { it.copy(showInviteSheetForTrip = null) }
    }

    fun openMembersScreen() {
        if (uiState.value.activeSharedTrip == null) return
        navAndModalState.update { it.copy(showMembersScreen = true) }
    }

    fun closeMembersScreen() {
        navAndModalState.update { it.copy(showMembersScreen = false) }
    }

    fun deleteSharedTrip(trip: SharedTrip) {
        if (trip.ownerId != currentUserId) {
            _snackbarEvents.trySend("Hanya admin pemilik grup yang dapat menghapus grup.")
            return
        }
        navAndModalState.update { it.copy(isSubmittingCollab = true) }
        viewModelScope.launch {
            val visitIdsToCancel = localRepository.getKunjunganIdsForSharedTrip(trip.id)
            val result = collabRepository.deleteSharedTrip(trip.id)
            result.onSuccess {
                visitIdsToCancel.forEach { alarmScheduler.cancelVisitReminder(it) }
                localRepository.cleanupRemovedSharedTripCache(trip.id)
                lastSyncedSharedTrips.update { list -> list.filterNot { it.id == trip.id } }
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        showMembersScreen = false,
                        showInviteSheetForTrip = if (it.showInviteSheetForTrip?.id == trip.id) null else it.showInviteSheetForTrip,
                        activeWorkspace = Workspace.Local,
                        spaceMode = SpaceMode.GRUP,
                        showGroupDirectory = true
                    )
                }
                _snackbarEvents.trySend("Grup \"${trip.title}\" telah dihapus permanen")
            }.onFailure { error ->
                navAndModalState.update { it.copy(isSubmittingCollab = false) }
                _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menghapus grup."))
            }
        }
    }

    fun leaveSharedTrip(trip: SharedTrip) {
        if (trip.ownerId == currentUserId) {
            _snackbarEvents.trySend("Pemilik tidak dapat keluar tanpa menghapus grup atau menyerahkan peran admin.")
            return
        }
        navAndModalState.update { it.copy(isSubmittingCollab = true) }
        viewModelScope.launch {
            val visitIdsToCancel = localRepository.getKunjunganIdsForSharedTrip(trip.id)
            val result = collabRepository.leaveSharedTrip(trip.id)
            result.onSuccess {
                visitIdsToCancel.forEach { alarmScheduler.cancelVisitReminder(it) }
                localRepository.cleanupRemovedSharedTripCache(trip.id)
                lastSyncedSharedTrips.update { list -> list.filterNot { it.id == trip.id } }
                navAndModalState.update {
                    it.copy(
                        isSubmittingCollab = false,
                        showMembersScreen = false,
                        showInviteSheetForTrip = if (it.showInviteSheetForTrip?.id == trip.id) null else it.showInviteSheetForTrip,
                        activeWorkspace = Workspace.Local,
                        spaceMode = SpaceMode.GRUP,
                        showGroupDirectory = true
                    )
                }
                _snackbarEvents.trySend("Anda telah keluar dari grup \"${trip.title}\"")
            }.onFailure { error ->
                navAndModalState.update { it.copy(isSubmittingCollab = false) }
                _snackbarEvents.trySend(extractErrorMessage(error, "Gagal keluar dari grup."))
            }
        }
    }

    fun transferGroupAdmin(trip: SharedTrip, newAdmin: TripMember) {
        if (trip.ownerId != currentUserId) {
            _snackbarEvents.trySend("Hanya admin pemilik grup yang dapat menyerahkan peran admin.")
            return
        }
        if (newAdmin.uid == currentUserId) return
        navAndModalState.update { it.copy(isSubmittingCollab = true) }
        viewModelScope.launch {
            val result = collabRepository.transferGroupAdmin(trip.id, newAdmin.uid)
            result.onSuccess {
                lastSyncedSharedTrips.update { list ->
                    list.map { item ->
                        if (item.id == trip.id) item.copy(ownerId = newAdmin.uid) else item
                    }
                }
                navAndModalState.update { it.copy(isSubmittingCollab = false) }
                _snackbarEvents.trySend("Peran admin diserahkan ke ${newAdmin.displayName}")
            }.onFailure { error ->
                navAndModalState.update { it.copy(isSubmittingCollab = false) }
                _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menyerahkan peran admin."))
            }
        }
    }

    fun openAccountSheet() {
        navAndModalState.update { it.copy(showAccountSheet = true) }
    }

    fun dismissAccountSheet() {
        navAndModalState.update { it.copy(showAccountSheet = false) }
    }

    fun testFirestoreConnection() {
        navAndModalState.update { it.copy(isTestingFirestore = true, firestoreTestResult = null) }
        viewModelScope.launch {
            val result = collabRepository.testFirestoreConnection()
            navAndModalState.update {
                it.copy(
                    isTestingFirestore = false,
                    firestoreTestResult = result
                )
            }
        }
    }

    fun refreshDriveBackupState(context: Context) {
        val appContext = context.applicationContext
        DriveBackupPreferences.setActiveUserId(appContext, currentUserId)
        val connection = DriveBackupPreferences.loadConnection(appContext, currentUserId)
        val schedule = DriveBackupPreferences.loadScheduleSettings(appContext, currentUserId)
        val status = DriveBackupPreferences.loadLastStatus(appContext, currentUserId)
        navAndModalState.update {
            it.copy(
                driveConnection = connection,
                driveScheduleSettings = schedule,
                driveLastStatus = status,
                backupIncludeVideos = schedule.includeVideos
            )
        }
    }

    fun setBackupIncludeVideos(context: Context, includeVideos: Boolean) {
        val appContext = context.applicationContext
        val updatedSchedule = navAndModalState.value.driveScheduleSettings.copy(includeVideos = includeVideos)
        DriveBackupPreferences.saveScheduleSettings(appContext, currentUserId, updatedSchedule)
        DriveBackupScheduler.syncSchedule(appContext, currentUserId)
        navAndModalState.update {
            it.copy(
                backupIncludeVideos = includeVideos,
                driveScheduleSettings = updatedSchedule
            )
        }
        refreshBackupEstimate(appContext, includeVideos)
    }

    fun connectGoogleDrive(context: Context) {
        authorizeDriveAndRun(context, PendingDriveAction.CONNECT_ONLY)
    }

    fun disconnectGoogleDrive(context: Context) {
        if (navAndModalState.value.isDriveActionLoading || navAndModalState.value.driveProgress != null) return
        val appContext = context.applicationContext
        val previousEmail = navAndModalState.value.driveConnection.accountEmail.ifBlank { currentUserEmail }
        navAndModalState.update { it.copy(isDriveActionLoading = true) }
        viewModelScope.launch {
            DriveAuthManager.revokeDriveAccess(appContext, previousEmail)
            DriveBackupPreferences.saveConnection(appContext, currentUserId, DriveAccountConnection())
            DriveBackupScheduler.cancelSchedule(appContext)
            navAndModalState.update {
                it.copy(
                    isDriveActionLoading = false,
                    driveConnection = DriveAccountConnection()
                )
            }
            _snackbarEvents.trySend("Koneksi Google Drive diputuskan")
        }
    }

    fun updateDriveScheduleSettings(context: Context, updated: DriveBackupScheduleSettings) {
        val appContext = context.applicationContext
        DriveBackupPreferences.saveScheduleSettings(appContext, currentUserId, updated)
        DriveBackupScheduler.syncSchedule(appContext, currentUserId)
        navAndModalState.update {
            it.copy(
                driveScheduleSettings = updated,
                backupIncludeVideos = updated.includeVideos
            )
        }
        refreshBackupEstimate(appContext, updated.includeVideos)
    }

    fun triggerDriveBackupNow(context: Context) {
        authorizeDriveAndRun(context, PendingDriveAction.BACKUP_NOW)
    }

    fun openDriveRestorePicker(context: Context) {
        authorizeDriveAndRun(context, PendingDriveAction.FETCH_VERSIONS_FOR_RESTORE)
    }

    fun dismissDriveVersionPicker() {
        navAndModalState.update { it.copy(availableDriveBackups = null) }
    }

    fun selectDriveVersionToInspect(context: Context, summary: DriveRemoteBackupSummary) {
        pendingDriveSummaryForAction = summary
        navAndModalState.update { it.copy(availableDriveBackups = null) }
        authorizeDriveAndRun(context, PendingDriveAction.INSPECT_VERSION)
    }

    fun dismissDriveRestoreConfirm() {
        navAndModalState.update { it.copy(pendingDriveRestoreConfirm = null) }
    }

    fun confirmDriveRestore(context: Context) {
        val confirm = navAndModalState.value.pendingDriveRestoreConfirm ?: return
        pendingDriveSummaryForAction = confirm.remoteSummary
        navAndModalState.update { it.copy(pendingDriveRestoreConfirm = null) }
        authorizeDriveAndRun(context, PendingDriveAction.RESTORE_SELECTED)
    }

    fun onDriveConsentActivityResult(context: Context, isOk: Boolean, data: Intent?) {
        val action = pendingDriveAction
        pendingDriveAction = null
        val appContext = context.applicationContext
        if (!isOk) {
            navAndModalState.update { it.copy(isDriveActionLoading = false) }
            _snackbarEvents.trySend("Izin Google Drive dibatalkan.")
            return
        }
        val preferredEmail = navAndModalState.value.driveConnection.accountEmail.ifBlank { currentUserEmail }
        when (val outcome = DriveAuthManager.extractAuthorizationFromIntent(appContext, data, preferredEmail)) {
            is DriveAuthOutcome.Authorized -> {
                if (action != null) {
                    viewModelScope.launch {
                        handleDriveAuthorized(appContext, outcome, action)
                    }
                } else {
                    navAndModalState.update { it.copy(isDriveActionLoading = false) }
                }
            }
            is DriveAuthOutcome.NeedsResolution -> {
                navAndModalState.update { it.copy(isDriveActionLoading = false) }
                _snackbarEvents.trySend("Izin Google Drive belum selesai diberikan.")
            }
            is DriveAuthOutcome.Failed -> {
                navAndModalState.update {
                    it.copy(
                        isDriveActionLoading = false,
                        backupStatusDialogTitle = "Gagal Menghubungkan Drive",
                        backupStatusDialogMessage = outcome.message,
                        backupStatusIsError = true
                    )
                }
            }
        }
    }

    private fun authorizeDriveAndRun(context: Context, action: PendingDriveAction) {
        if (navAndModalState.value.isDriveActionLoading || navAndModalState.value.driveProgress != null) return
        val appContext = context.applicationContext
        val preferredEmail = navAndModalState.value.driveConnection.accountEmail.ifBlank { currentUserEmail }
        navAndModalState.update { it.copy(isDriveActionLoading = true) }

        viewModelScope.launch {
            when (val outcome = DriveAuthManager.authorizeDriveAccess(appContext, preferredEmail)) {
                is DriveAuthOutcome.Authorized -> {
                    handleDriveAuthorized(appContext, outcome, action)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    pendingDriveAction = action
                    _driveConsentResolutionEvents.trySend(outcome.intentSender)
                }
                is DriveAuthOutcome.Failed -> {
                    navAndModalState.update {
                        it.copy(
                            isDriveActionLoading = false,
                            backupStatusDialogTitle = "Izin Google Drive Gagal",
                            backupStatusDialogMessage = outcome.message,
                            backupStatusIsError = true
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleDriveAuthorized(
        appContext: Context,
        auth: DriveAuthOutcome.Authorized,
        action: PendingDriveAction
    ) {
        val currentConn = DriveBackupPreferences.loadConnection(appContext, currentUserId)
        val profile = if (!currentConn.isConnected || currentConn.accountEmail.isBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { DriveApiClient.fetchUserAbout(auth.accessToken) }.getOrNull()
            }
        } else null

        val resolvedEmail = profile?.email?.takeIf { it.isNotBlank() }
            ?: auth.accountEmail.takeIf { it.isNotBlank() }
            ?: currentConn.accountEmail.takeIf { it.isNotBlank() }
            ?: currentUserEmail
        val resolvedName = profile?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.accountName.takeIf { it.isNotBlank() }
            ?: currentConn.accountName.takeIf { it.isNotBlank() }
            ?: currentUserName

        val updatedConnection = DriveAccountConnection(
            isConnected = true,
            accountEmail = resolvedEmail,
            accountName = resolvedName,
            connectedAtMillis = if (currentConn.isConnected && currentConn.connectedAtMillis > 0L) {
                currentConn.connectedAtMillis
            } else {
                System.currentTimeMillis()
            }
        )
        DriveBackupPreferences.saveConnection(appContext, currentUserId, updatedConnection)
        DriveBackupScheduler.syncSchedule(appContext, currentUserId)
        navAndModalState.update { it.copy(driveConnection = updatedConnection) }

        when (action) {
            PendingDriveAction.CONNECT_ONLY -> {
                navAndModalState.update { it.copy(isDriveActionLoading = false) }
                _snackbarEvents.trySend("Google Drive terhubung (${resolvedEmail.ifBlank { resolvedName }})")
            }

            PendingDriveAction.BACKUP_NOW -> {
                navAndModalState.update {
                    it.copy(
                        isDriveActionLoading = false,
                        driveProgress = DriveBackupProgress(
                            isRestore = false,
                            stepLabel = "Menyiapkan cadangan ke Google Drive...",
                            processedFiles = 0,
                            totalFiles = 1,
                            processedBytes = 0L,
                            totalBytes = 0L
                        )
                    )
                }
                val includeVideos = navAndModalState.value.driveScheduleSettings.includeVideos
                val backupResult = withContext(Dispatchers.IO) {
                    DriveBackupEngine.performIncrementalBackup(
                        context = appContext,
                        userId = currentUserId,
                        accessToken = auth.accessToken,
                        localRepository = localRepository,
                        includeVideos = includeVideos,
                        onProgress = { progress ->
                            navAndModalState.update { state -> state.copy(driveProgress = progress) }
                        }
                    )
                }
                val latestStatus = DriveBackupPreferences.loadLastStatus(appContext, currentUserId)
                backupResult.onSuccess { exec ->
                    val sizeText = BackupManager.formatByteSize(exec.totalBackupSizeBytes)
                    TripNotificationHelper.showDriveBackupSuccessNotification(
                        context = appContext,
                        uploadedFilesCount = exec.uploadedFilesCount,
                        totalSizeFormatted = sizeText
                    )
                    navAndModalState.update {
                        it.copy(
                            driveProgress = null,
                            driveLastStatus = latestStatus,
                            backupStatusDialogTitle = "Cadangan Drive Berhasil",
                            backupStatusDialogMessage = if (exec.uploadedFilesCount > 0) {
                                "${exec.uploadedFilesCount} file media baru diunggah dan ${exec.reusedFilesCount} file yang tidak berubah dilewati.\n\nTotal ukuran cadangan: $sizeText."
                            } else {
                                "Data pribadi berhasil dicadangkan ke folder \"TripBuddy Backup\" ($sizeText). Semua ${exec.reusedFilesCount} media sudah mutakhir."
                            },
                            backupStatusIsError = false
                        )
                    }
                }.onFailure { err ->
                    if (latestStatus.consecutiveFailures >= 3) {
                        TripNotificationHelper.showDriveBackupFailureWarningNotification(
                            context = appContext,
                            consecutiveFailures = latestStatus.consecutiveFailures,
                            reason = err.localizedMessage ?: latestStatus.lastErrorMessage
                        )
                    }
                    navAndModalState.update {
                        it.copy(
                            driveProgress = null,
                            driveLastStatus = latestStatus,
                            backupStatusDialogTitle = "Cadangan Drive Gagal",
                            backupStatusDialogMessage = err.localizedMessage
                                ?: "Gagal mencadangkan ke Google Drive. Unggahan yang terputus akan dilanjutkan pada percobaan berikutnya.",
                            backupStatusIsError = true
                        )
                    }
                }
            }

            PendingDriveAction.FETCH_VERSIONS_FOR_RESTORE -> {
                val listResult = withContext(Dispatchers.IO) {
                    DriveBackupEngine.fetchAvailableDriveBackups(auth.accessToken)
                }
                listResult.onSuccess { versions ->
                    navAndModalState.update {
                        it.copy(
                            isDriveActionLoading = false,
                            availableDriveBackups = versions
                        )
                    }
                }.onFailure { err ->
                    navAndModalState.update {
                        it.copy(
                            isDriveActionLoading = false,
                            backupStatusDialogTitle = "Gagal Membaca Cadangan Drive",
                            backupStatusDialogMessage = err.localizedMessage
                                ?: "Tidak dapat mengambil daftar cadangan di folder TripBuddy Backup.",
                            backupStatusIsError = true
                        )
                    }
                }
            }

            PendingDriveAction.INSPECT_VERSION -> {
                val summary = pendingDriveSummaryForAction
                pendingDriveSummaryForAction = null
                if (summary == null) {
                    navAndModalState.update { it.copy(isDriveActionLoading = false) }
                    return
                }
                val inspectResult = withContext(Dispatchers.IO) {
                    DriveBackupEngine.inspectDriveBackupForRestore(
                        context = appContext,
                        accessToken = auth.accessToken,
                        summary = summary,
                        localRepository = localRepository
                    )
                }
                inspectResult.onSuccess { confirmSummary ->
                    navAndModalState.update {
                        it.copy(
                            isDriveActionLoading = false,
                            pendingDriveRestoreConfirm = confirmSummary
                        )
                    }
                }.onFailure { err ->
                    navAndModalState.update {
                        it.copy(
                            isDriveActionLoading = false,
                            backupStatusDialogTitle = "Gagal Memeriksa Cadangan",
                            backupStatusDialogMessage = err.localizedMessage
                                ?: "File cadangan di Drive tidak dapat dibaca.",
                            backupStatusIsError = true
                        )
                    }
                }
            }

            PendingDriveAction.RESTORE_SELECTED -> {
                val summary = pendingDriveSummaryForAction
                pendingDriveSummaryForAction = null
                if (summary == null) {
                    navAndModalState.update { it.copy(isDriveActionLoading = false) }
                    return
                }
                navAndModalState.update {
                    it.copy(
                        isDriveActionLoading = false,
                        driveProgress = DriveBackupProgress(
                            isRestore = true,
                            stepLabel = "Memulai pemulihan dari Google Drive...",
                            processedFiles = 0,
                            totalFiles = summary.manifest.mediaFiles.size + 1,
                            processedBytes = 0L,
                            totalBytes = summary.manifest.totalMediaBytes
                        )
                    )
                }
                val restoreResult = withContext(Dispatchers.IO) {
                    DriveBackupEngine.restoreFromDriveBackup(
                        context = appContext,
                        userId = currentUserId,
                        accessToken = auth.accessToken,
                        localRepository = localRepository,
                        summary = summary,
                        onProgress = { progress ->
                            navAndModalState.update { state -> state.copy(driveProgress = progress) }
                        }
                    )
                }
                restoreResult.onSuccess { res ->
                    navAndModalState.update {
                        it.copy(
                            driveProgress = null,
                            reminderSettings = res.restoredReminderSettings,
                            backupStatusDialogTitle = "Pemulihan Drive Selesai",
                            backupStatusDialogMessage = buildString {
                                append("Berhasil memulihkan ")
                                append("${res.restoredKunjunganCount} kunjungan pribadi, ")
                                append("${res.restoredFotoCount} entri media (${res.downloadedMediaCount} file diunduh), dan pengaturan aplikasi.")
                                if (res.skippedKunjunganCount > 0 || res.skippedFotoCount > 0) {
                                    append("\n\n${res.skippedKunjunganCount} kunjungan dan ${res.skippedFotoCount} media yang sudah ada di perangkat dilewati tanpa duplikasi.")
                                }
                            },
                            backupStatusIsError = false
                        )
                    }
                    _themeRestoredEvents.trySend(res.restoredTheme)
                    rescheduleAllLocalReminders()
                    refreshBackupEstimate(appContext)
                }.onFailure { err ->
                    navAndModalState.update {
                        it.copy(
                            driveProgress = null,
                            backupStatusDialogTitle = "Pemulihan Drive Gagal",
                            backupStatusDialogMessage = err.localizedMessage
                                ?: "Terjadi kesalahan saat memulihkan data dari Google Drive.",
                            backupStatusIsError = true
                        )
                    }
                }
            }
        }
    }

    fun refreshBackupEstimate(
        context: Context,
        includeVideos: Boolean = navAndModalState.value.backupIncludeVideos
    ) {
        val appContext = context.applicationContext
        navAndModalState.update { it.copy(isEstimatingBackup = true) }
        viewModelScope.launch {
            val estimate = withContext(Dispatchers.IO) {
                localRepository.ensurePersonalTripExists()
                val personalVisits = localRepository.getAllKunjunganOnce()
                    .filter { it.tripId == Trip.PERSONAL_TRIP_ID }
                val personalVisitIds = personalVisits.map { it.id }.toSet()
                val personalFotos = localRepository.getAllFotosOnce()
                    .filter { it.tripId == Trip.PERSONAL_TRIP_ID || it.kunjunganId in personalVisitIds }
                BackupManager.computeBackupEstimate(
                    filesDir = appContext.filesDir,
                    personalKunjungan = personalVisits,
                    personalFotos = personalFotos,
                    includeVideos = includeVideos
                )
            }
            navAndModalState.update {
                it.copy(
                    isEstimatingBackup = false,
                    backupEstimate = estimate
                )
            }
        }
    }

    fun exportPersonalBackup(context: Context) {
        if (navAndModalState.value.backupProgress != null) return
        val appContext = context.applicationContext
        val includeVideos = navAndModalState.value.backupIncludeVideos

        navAndModalState.update {
            it.copy(
                backupProgress = BackupProgress(
                    isImport = false,
                    stepLabel = "Menyiapkan cadangan data pribadi...",
                    processedItems = 0,
                    totalItems = 1,
                    processedBytes = 0L,
                    totalBytes = 0L
                )
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                localRepository.ensurePersonalTripExists()
                val personalTrip = localRepository.getAllTripsOnce()
                    .firstOrNull { it.id == Trip.PERSONAL_TRIP_ID }
                    ?: Trip.personalTrip()
                val personalVisits = localRepository.getAllKunjunganOnce()
                    .filter { it.tripId == Trip.PERSONAL_TRIP_ID }
                val personalVisitIds = personalVisits.map { it.id }.toSet()
                val personalFotos = localRepository.getAllFotosOnce()
                    .filter { it.tripId == Trip.PERSONAL_TRIP_ID || it.kunjunganId in personalVisitIds }

                BackupManager.exportPersonalBackup(
                    context = appContext,
                    personalTrip = personalTrip,
                    personalKunjungan = personalVisits,
                    personalFotos = personalFotos,
                    userId = currentUserId,
                    includeVideos = includeVideos,
                    onProgress = { progress ->
                        navAndModalState.update { state ->
                            state.copy(backupProgress = progress)
                        }
                    }
                )
            }

            result.onSuccess { exported ->
                navAndModalState.update {
                    it.copy(backupProgress = null)
                }
                _shareBackupEvents.trySend(exported)
            }.onFailure { err ->
                navAndModalState.update {
                    it.copy(
                        backupProgress = null,
                        backupStatusDialogTitle = "Gagal Membuat Cadangan",
                        backupStatusDialogMessage = err.localizedMessage
                            ?: "Terjadi kesalahan saat membuat file cadangan ZIP.",
                        backupStatusIsError = true
                    )
                }
            }
        }
    }

    fun inspectBackupZipForImport(context: Context, uri: Uri) {
        if (navAndModalState.value.backupProgress != null) return
        val appContext = context.applicationContext

        navAndModalState.update {
            it.copy(
                backupProgress = BackupProgress(
                    isImport = true,
                    stepLabel = "Memvalidasi file cadangan & manifest...",
                    processedItems = 0,
                    totalItems = 1,
                    processedBytes = 0L,
                    totalBytes = 0L
                )
            )
        }

        viewModelScope.launch {
            val inspectionResult = withContext(Dispatchers.IO) {
                val existingKunjunganIds = localRepository.getAllKunjunganOnce().map { it.id }.toSet()
                val existingFotoIds = localRepository.getAllFotosOnce().map { it.id }.toSet()
                BackupManager.inspectBackupZip(
                    context = appContext,
                    uri = uri,
                    existingKunjunganIds = existingKunjunganIds,
                    existingFotoIds = existingFotoIds
                )
            }

            inspectionResult.onSuccess { summary ->
                navAndModalState.update {
                    it.copy(
                        backupProgress = null,
                        pendingBackupImportSummary = summary
                    )
                }
            }.onFailure { err ->
                navAndModalState.update {
                    it.copy(
                        backupProgress = null,
                        pendingBackupImportSummary = null,
                        backupStatusDialogTitle = "File Cadangan Tidak Valid",
                        backupStatusDialogMessage = err.localizedMessage
                            ?: "File cadangan rusak atau tidak dapat dibaca.",
                        backupStatusIsError = true
                    )
                }
            }
        }
    }

    fun dismissBackupImportConfirm() {
        navAndModalState.update { it.copy(pendingBackupImportSummary = null) }
    }

    fun confirmBackupRestore(context: Context) {
        val summary = navAndModalState.value.pendingBackupImportSummary ?: return
        if (navAndModalState.value.backupProgress != null) return
        val appContext = context.applicationContext

        navAndModalState.update {
            it.copy(
                pendingBackupImportSummary = null,
                backupProgress = BackupProgress(
                    isImport = true,
                    stepLabel = "Memulai pemulihan data...",
                    processedItems = 0,
                    totalItems = (summary.manifest.mediaFileCount + 2).coerceAtLeast(2),
                    processedBytes = 0L,
                    totalBytes = summary.estimatedRestoreBytes
                )
            )
        }

        viewModelScope.launch {
            val restoreResult = withContext(Dispatchers.IO) {
                runCatching {
                    val existingKunjunganIds = localRepository.getAllKunjunganOnce().map { it.id }.toSet()
                    val existingFotoIds = localRepository.getAllFotosOnce().map { it.id }.toSet()

                    val parsed = BackupManager.extractBackupZipFromStream(
                        streamProvider = { appContext.contentResolver.openInputStream(summary.sourceUri) },
                        filesDir = appContext.filesDir,
                        existingKunjunganIds = existingKunjunganIds,
                        existingFotoIds = existingFotoIds,
                        expectedTotalBytes = summary.estimatedRestoreBytes,
                        expectedTotalMediaFiles = summary.manifest.mediaFileCount,
                        onProgress = { progress ->
                            navAndModalState.update { state ->
                                state.copy(backupProgress = progress)
                            }
                        }
                    )

                    localRepository.restorePersonalBackupData(
                        newKunjungan = parsed.newKunjungan,
                        newFotos = parsed.newFotos
                    )

                    // Pre-warm thumbnails for newly restored videos so Kenangan displays them right away
                    parsed.newFotos.filter { it.isVideo }.forEach { video ->
                        PhotoStorageHelper.generateAndCacheVideoThumbnail(
                            context = appContext,
                            videoPath = video.filePath,
                            mediaUuid = video.id
                        )
                    }

                    val restoredTheme = AppThemeOption.fromId(parsed.restoredSettings.themeId)
                    ThemePreferences.saveTheme(appContext, restoredTheme)

                    val restoredTimings = ReminderTimingOption.fromIds(
                        parsed.restoredSettings.selectedTimingIds.toSet()
                    )
                    val restoredReminder = UserReminderSettings(
                        enabled = parsed.restoredSettings.remindersEnabled,
                        selectedTimings = restoredTimings,
                        annualMemoryReminderEnabled = parsed.restoredSettings.annualMemoryReminderEnabled
                    )
                    ReminderPreferences.saveSettings(appContext, currentUserId, restoredReminder)

                    BackupManager.formatByteSize(summary.estimatedRestoreBytes) // validate helper accessibility
                    Triple(parsed, restoredTheme, restoredReminder)
                }
            }

            restoreResult.onSuccess { (parsed, restoredTheme, restoredReminder) ->
                navAndModalState.update {
                    it.copy(
                        backupProgress = null,
                        reminderSettings = restoredReminder,
                        backupStatusDialogTitle = "Pemulihan Selesai",
                        backupStatusDialogMessage = buildString {
                            append("Berhasil memulihkan ")
                            append("${parsed.newKunjungan.size} kunjungan pribadi, ")
                            append("${parsed.newFotos.size} item media, dan pengaturan aplikasi.")
                            if (parsed.skippedKunjunganCount > 0 || parsed.skippedFotoCount > 0) {
                                append("\n\n${parsed.skippedKunjunganCount} kunjungan dan ${parsed.skippedFotoCount} media yang sudah ada dilewati agar tidak duplikat.")
                            }
                        },
                        backupStatusIsError = false
                    )
                }
                _themeRestoredEvents.trySend(restoredTheme)
                rescheduleAllLocalReminders()
                refreshBackupEstimate(appContext)
            }.onFailure { err ->
                navAndModalState.update {
                    it.copy(
                        backupProgress = null,
                        backupStatusDialogTitle = "Gagal Memulihkan Cadangan",
                        backupStatusDialogMessage = err.localizedMessage
                            ?: "Terjadi kesalahan saat memulihkan data dari file cadangan.",
                        backupStatusIsError = true
                    )
                }
            }
        }
    }

    fun dismissBackupStatusDialog() {
        navAndModalState.update {
            it.copy(
                backupStatusDialogTitle = null,
                backupStatusDialogMessage = null,
                backupStatusIsError = false
            )
        }
    }

    fun openVisitMemoryDetail(kunjunganId: String) {
        navAndModalState.update { it.copy(activeMemoryVisitId = kunjunganId) }
    }

    fun closeVisitMemoryDetail() {
        navAndModalState.update { it.copy(activeMemoryVisitId = null) }
    }

    fun openCameraVisitPicker() {
        navAndModalState.update { it.copy(showCameraVisitPicker = true) }
    }

    fun dismissCameraVisitPicker() {
        navAndModalState.update { it.copy(showCameraVisitPicker = false) }
    }

    fun saveCapturedPhoto(
        context: Context,
        kunjunganId: String,
        tempCaptureFile: File
    ) {
        viewModelScope.launch {
            val result = localRepository.saveCapturedPhotoForKunjungan(
                context = context.applicationContext,
                tempCaptureFile = tempCaptureFile,
                kunjunganId = kunjunganId
            )
            result.onSuccess { foto ->
                val message = if (foto.mediaStoreUri != null) {
                    "Foto kenangan disimpan di aplikasi & album TripBuddy"
                } else {
                    "Foto kenangan disimpan di penyimpanan aplikasi"
                }
                _snackbarEvents.trySend(message)
            }.onFailure { error ->
                PhotoStorageHelper.deleteFileQuietly(tempCaptureFile.absolutePath)
                _snackbarEvents.trySend(
                    "Gagal menyimpan foto: ${error.localizedMessage ?: "terjadi kesalahan"}"
                )
            }
        }
    }

    fun saveCapturedVideo(
        context: Context,
        kunjunganId: String,
        tempVideoFile: File
    ) {
        viewModelScope.launch {
            val result = localRepository.saveCapturedVideoForKunjungan(
                context = context.applicationContext,
                tempVideoFile = tempVideoFile,
                kunjunganId = kunjunganId
            )
            result.onSuccess { video ->
                val message = if (video.mediaStoreUri != null) {
                    "Video kenangan disimpan di aplikasi & album TripBuddy"
                } else {
                    "Video kenangan disimpan di penyimpanan aplikasi"
                }
                _snackbarEvents.trySend(message)
            }.onFailure { error ->
                PhotoStorageHelper.deleteFileQuietly(tempVideoFile.absolutePath)
                _snackbarEvents.trySend(
                    "Gagal menyimpan video: ${error.localizedMessage ?: "terjadi kesalahan"}"
                )
            }
        }
    }

    fun deleteMemoryPhoto(foto: Foto, filesDir: File? = null) {
        viewModelScope.launch {
            localRepository.deleteFoto(foto, filesDir)
            val label = if (foto.isVideo) "Video" else "Foto"
            _snackbarEvents.trySend("$label dihapus dari aplikasi")
        }
    }

    fun openPdfExportDialog(
        defaultScope: PdfExportScope,
        selectedVisitId: String? = null,
        selectedGroupUuid: String? = null
    ) {
        if (navAndModalState.value.isExportingPdf) return
        val resolvedGroupUuid = selectedGroupUuid
            ?: (navAndModalState.value.activeWorkspace as? Workspace.Shared)?.let { Trip.sharedTripUuid(it.tripId) }
            ?: latestRoomSnapshot?.allTrips?.firstOrNull { !it.isPersonal }?.id
        navAndModalState.update {
            it.copy(
                pdfAllowThisVisitScope = selectedVisitId != null,
                pdfExportOptions = PdfExportOptions(
                    scope = defaultScope,
                    selectedVisitId = selectedVisitId,
                    selectedGroupUuid = resolvedGroupUuid
                )
            )
        }
    }

    fun updatePdfExportOptions(options: PdfExportOptions) {
        navAndModalState.update { it.copy(pdfExportOptions = options) }
    }

    fun dismissPdfExportDialog() {
        navAndModalState.update {
            it.copy(pdfExportOptions = null, pdfAllowThisVisitScope = false)
        }
    }

    fun confirmPdfExport(context: Context) {
        val options = navAndModalState.value.pdfExportOptions ?: return
        if (navAndModalState.value.isExportingPdf) return
        val appContext = context.applicationContext

        navAndModalState.update {
            it.copy(
                pdfExportOptions = null,
                pdfAllowThisVisitScope = false,
                isExportingPdf = true,
                pdfExportProgress = PdfExportProgress(
                    processedPhotos = 0,
                    totalPhotos = 1,
                    currentPage = 1
                )
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val trips = localRepository.getAllTripsOnce()
                val visits = localRepository.getAllKunjunganOnce()
                val fotos = localRepository.getAllFotosOnce()
                val spec = PdfExporter.buildPhotoAlbumSpec(
                    options = options,
                    allTrips = trips,
                    allKunjungan = visits,
                    allFotos = fotos
                )
                PdfExporter.exportPhotoAlbumToPdf(
                    context = appContext,
                    spec = spec,
                    onProgress = { progress ->
                        navAndModalState.update { state ->
                            state.copy(pdfExportProgress = progress)
                        }
                    }
                )
            }
            result.onSuccess { exported ->
                navAndModalState.update {
                    it.copy(
                        isExportingPdf = false,
                        pdfExportProgress = null,
                        exportedPdfResult = exported
                    )
                }
            }.onFailure { error ->
                navAndModalState.update {
                    it.copy(
                        isExportingPdf = false,
                        pdfExportProgress = null
                    )
                }
                _snackbarEvents.trySend(
                    "Gagal mengekspor album PDF: ${error.localizedMessage ?: "terjadi kesalahan"}"
                )
            }
        }
    }

    fun dismissExportedPdfDialog() {
        navAndModalState.update { it.copy(exportedPdfResult = null) }
    }

    fun goToPreviousMonth() {
        navAndModalState.update { state ->
            if (state.currentMonth == 0) {
                state.copy(currentYear = state.currentYear - 1, currentMonth = 11)
            } else {
                state.copy(currentMonth = state.currentMonth - 1)
            }
        }
    }

    fun goToNextMonth() {
        navAndModalState.update { state ->
            if (state.currentMonth == 11) {
                state.copy(currentYear = state.currentYear + 1, currentMonth = 0)
            } else {
                state.copy(currentMonth = state.currentMonth + 1)
            }
        }
    }

    fun jumpToToday() {
        val (year, month) = DateUtils.currentYearAndMonth()
        val today = DateUtils.todayEpochDay()
        navAndModalState.update {
            it.copy(currentYear = year, currentMonth = month, selectedDateEpochDay = today)
        }
    }

    fun onCalendarDateClicked(epochDay: Long) {
        val visitsForDate = uiState.value.allVisits.filter { it.visitDateEpochDay == epochDay }
        val (year, month, _) = DateUtils.ymdFromEpochDay(epochDay)
        navAndModalState.update { state ->
            val isShared = state.activeWorkspace is Workspace.Shared
            val shouldOpenAddDirectly = !isShared && (visitsForDate.isEmpty() || state.selectedDateEpochDay == epochDay)
            state.copy(
                currentYear = year,
                currentMonth = month,
                selectedDateEpochDay = epochDay,
                activeAddEditDateEpochDay = if (shouldOpenAddDirectly) epochDay else null,
                editingVisit = null
            )
        }
    }

    fun openAddLocationSheet(epochDay: Long = navAndModalState.value.selectedDateEpochDay) {
        navAndModalState.update {
            it.copy(
                selectedDateEpochDay = epochDay,
                activeAddEditDateEpochDay = epochDay,
                editingVisit = null
            )
        }
    }

    fun openEditLocationSheet(visit: DisplayVisit) {
        navAndModalState.update {
            it.copy(
                activeAddEditDateEpochDay = visit.visitDateEpochDay,
                editingVisit = visit
            )
        }
    }

    fun dismissAddEditSheet() {
        navAndModalState.update {
            it.copy(activeAddEditDateEpochDay = null, editingVisit = null)
        }
    }

    fun openMapsSearchSheet(visit: DisplayVisit) {
        navAndModalState.update { it.copy(activeMapsVisit = visit) }
    }

    fun dismissMapsSearchSheet() {
        navAndModalState.update { it.copy(activeMapsVisit = null) }
    }

    fun selectMemoriesTab(tab: MemoriesFilterTab) {
        navAndModalState.update {
            it.copy(
                selectedMemoriesTab = tab,
                selectedMemoryGroupId = if (tab == MemoriesFilterTab.GRUP) it.selectedMemoryGroupId else null
            )
        }
    }

    fun selectMemoryGroup(tripId: String?) {
        navAndModalState.update {
            it.copy(
                selectedMemoriesTab = MemoriesFilterTab.GRUP,
                selectedMemoryGroupId = tripId
            )
        }
    }

    fun setMemorySearchQuery(query: String) {
        navAndModalState.update { it.copy(memorySearchQuery = query) }
    }

    fun saveLocationVisit(
        locationName: String,
        tripTitle: String,
        epochDay: Long,
        hour: Int,
        minute: Int,
        planNotes: String
    ) {
        val cleanLocation = locationName.trim()
        if (cleanLocation.isEmpty()) return

        val existing = navAndModalState.value.editingVisit
        val workspace = navAndModalState.value.activeWorkspace

        viewModelScope.launch {
            if (existing != null && existing.isShared && existing.sharedTripId != null && existing.sharedDocId != null) {
                val result = collabRepository.updateSharedVisit(
                    tripId = existing.sharedTripId,
                    visitId = existing.sharedDocId,
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim(),
                    memoryNotes = existing.memoryNotes,
                    completedManually = existing.completedManually
                )
                result.onSuccess {
                    val updatedEntity = existing.toKunjunganEntity().copy(
                        locationName = cleanLocation,
                        tripTitle = tripTitle.trim(),
                        visitDateEpochDay = epochDay,
                        visitHour = hour,
                        visitMinute = minute,
                        planNotes = planNotes.trim()
                    )
                    localRepository.upsertKunjungan(updatedEntity)
                    alarmScheduler.scheduleVisitReminder(
                        visit = updatedEntity,
                        groupOrSpaceName = resolveGroupOrSpaceName(updatedEntity.tripId, updatedEntity.remoteTripId),
                        settings = navAndModalState.value.reminderSettings
                    )
                    dismissAddEditSheet()
                    _snackbarEvents.trySend("Lokasi \"$cleanLocation\" diperbarui untuk semua anggota")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menyimpan perubahan lokasi."))
                }
            } else if (existing == null && workspace is Workspace.Shared) {
                val activeTrip = uiState.value.sharedTrips.find { it.id == workspace.tripId }
                if (activeTrip != null) {
                    localRepository.syncSharedTripToRoom(activeTrip)
                }
                val result = collabRepository.addSharedVisit(
                    tripId = workspace.tripId,
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim(),
                    authorName = currentUserName
                )
                result.onSuccess { docId ->
                    val visitUuid = Kunjungan.sharedVisitUuid(workspace.tripId, docId)
                    val entity = Kunjungan(
                        id = visitUuid,
                        tripId = Trip.sharedTripUuid(workspace.tripId),
                        locationName = cleanLocation,
                        tripTitle = tripTitle.trim(),
                        visitDateEpochDay = epochDay,
                        visitHour = hour,
                        visitMinute = minute,
                        planNotes = planNotes.trim(),
                        memoryNotes = "",
                        isCompletedManually = false,
                        createdByName = currentUserName,
                        remoteDocId = docId,
                        remoteTripId = workspace.tripId,
                        createdAtMillis = System.currentTimeMillis()
                    )
                    localRepository.upsertKunjungan(entity)
                    alarmScheduler.scheduleVisitReminder(
                        visit = entity,
                        groupOrSpaceName = resolveGroupOrSpaceName(entity.tripId, entity.remoteTripId),
                        settings = navAndModalState.value.reminderSettings
                    )
                    dismissAddEditSheet()
                    _snackbarEvents.trySend("Lokasi \"$cleanLocation\" ditambahkan ke trip bersama")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menambahkan lokasi."))
                }
            } else if (existing != null) {
                val updated = existing.toKunjunganEntity().copy(
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim()
                )
                localRepository.upsertKunjungan(updated)
                alarmScheduler.scheduleVisitReminder(
                    visit = updated,
                    groupOrSpaceName = resolveGroupOrSpaceName(updated.tripId, updated.remoteTripId),
                    settings = navAndModalState.value.reminderSettings
                )
                dismissAddEditSheet()
                _snackbarEvents.trySend("Lokasi \"$cleanLocation\" disimpan beserta pengingatnya")
            } else {
                val newVisit = Kunjungan(
                    id = UUID.randomUUID().toString(),
                    tripId = Trip.PERSONAL_TRIP_ID,
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim()
                )
                localRepository.upsertKunjungan(newVisit)
                alarmScheduler.scheduleVisitReminder(
                    visit = newVisit,
                    groupOrSpaceName = "Pribadi",
                    settings = navAndModalState.value.reminderSettings
                )
                dismissAddEditSheet()
                _snackbarEvents.trySend("Lokasi \"$cleanLocation\" disimpan beserta pengingatnya")
            }
        }
    }

    fun updateLocationNameFromMapsSheet(visit: DisplayVisit, newLocationName: String) {
        val clean = newLocationName.trim()
        if (clean.isEmpty() || clean == visit.locationName) return
        viewModelScope.launch {
            if (visit.isShared && visit.sharedTripId != null && visit.sharedDocId != null) {
                val result = collabRepository.updateSharedVisit(
                    tripId = visit.sharedTripId,
                    visitId = visit.sharedDocId,
                    locationName = clean,
                    tripTitle = visit.tripTitle,
                    visitDateEpochDay = visit.visitDateEpochDay,
                    visitHour = visit.visitHour,
                    visitMinute = visit.visitMinute,
                    planNotes = visit.planNotes,
                    memoryNotes = visit.memoryNotes,
                    completedManually = visit.completedManually
                )
                result.onSuccess {
                    val updated = visit.toKunjunganEntity().copy(locationName = clean)
                    localRepository.upsertKunjungan(updated)
                    alarmScheduler.scheduleVisitReminder(
                        visit = updated,
                        groupOrSpaceName = resolveGroupOrSpaceName(updated.tripId, updated.remoteTripId),
                        settings = navAndModalState.value.reminderSettings
                    )
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui nama lokasi."))
                }
            } else {
                val updated = visit.toKunjunganEntity().copy(locationName = clean)
                localRepository.upsertKunjungan(updated)
                alarmScheduler.scheduleVisitReminder(
                    visit = updated,
                    groupOrSpaceName = resolveGroupOrSpaceName(updated.tripId, updated.remoteTripId),
                    settings = navAndModalState.value.reminderSettings
                )
            }
        }
    }

    fun toggleManualCompleted(visit: DisplayVisit) {
        val nextCompleted = !visit.completedManually
        viewModelScope.launch {
            if (visit.isShared && visit.sharedTripId != null && visit.sharedDocId != null) {
                val result = collabRepository.updateSharedVisit(
                    tripId = visit.sharedTripId,
                    visitId = visit.sharedDocId,
                    locationName = visit.locationName,
                    tripTitle = visit.tripTitle,
                    visitDateEpochDay = visit.visitDateEpochDay,
                    visitHour = visit.visitHour,
                    visitMinute = visit.visitMinute,
                    planNotes = visit.planNotes,
                    memoryNotes = visit.memoryNotes,
                    completedManually = nextCompleted
                )
                result.onSuccess {
                    val updated = visit.toKunjunganEntity().copy(isCompletedManually = nextCompleted)
                    localRepository.upsertKunjungan(updated)
                    alarmScheduler.scheduleVisitReminder(
                        visit = updated,
                        groupOrSpaceName = resolveGroupOrSpaceName(updated.tripId, updated.remoteTripId),
                        settings = navAndModalState.value.reminderSettings
                    )
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui status lokasi."))
                }
            } else {
                val updated = visit.toKunjunganEntity().copy(isCompletedManually = nextCompleted)
                localRepository.upsertKunjungan(updated)
                alarmScheduler.scheduleVisitReminder(
                    visit = updated,
                    groupOrSpaceName = resolveGroupOrSpaceName(updated.tripId, updated.remoteTripId),
                    settings = navAndModalState.value.reminderSettings
                )
            }
        }
    }

    fun saveMemoryNotes(kunjungan: Kunjungan, memoryNotes: String) {
        val cleanNotes = memoryNotes.trim()
        viewModelScope.launch {
            // Save immediately in local Room per kunjunganId
            localRepository.updateMemoryNotes(kunjungan.id, cleanNotes)
            if (kunjungan.remoteTripId != null && kunjungan.remoteDocId != null) {
                val result = collabRepository.updateSharedVisit(
                    tripId = kunjungan.remoteTripId,
                    visitId = kunjungan.remoteDocId,
                    locationName = kunjungan.locationName,
                    tripTitle = kunjungan.tripTitle,
                    visitDateEpochDay = kunjungan.visitDateEpochDay,
                    visitHour = kunjungan.visitHour,
                    visitMinute = kunjungan.visitMinute,
                    planNotes = kunjungan.planNotes,
                    memoryNotes = cleanNotes,
                    completedManually = kunjungan.isCompletedManually
                )
                result.onSuccess {
                    _snackbarEvents.trySend("Deskripsi kenangan disimpan")
                }.onFailure {
                    _snackbarEvents.trySend("Deskripsi disimpan di perangkat")
                }
            } else {
                _snackbarEvents.trySend("Deskripsi kenangan disimpan")
            }
        }
    }

    fun deleteVisit(visit: DisplayVisit) {
        viewModelScope.launch {
            if (visit.isShared && visit.sharedTripId != null && visit.sharedDocId != null) {
                val result = collabRepository.deleteSharedVisit(visit.sharedTripId, visit.sharedDocId)
                result.onSuccess {
                    alarmScheduler.cancelVisitReminder(visit.id)
                    localRepository.deleteKunjunganById(visit.id)
                    navAndModalState.update { state ->
                        state.copy(
                            activeMapsVisit = if (state.activeMapsVisit?.id == visit.id) null else state.activeMapsVisit,
                            activeMemoryVisitId = if (state.activeMemoryVisitId == visit.id) null else state.activeMemoryVisitId
                        )
                    }
                    _snackbarEvents.trySend("\"${visit.locationName}\" dihapus")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menghapus lokasi."))
                }
            } else {
                alarmScheduler.cancelVisitReminder(visit.id)
                localRepository.deleteKunjunganById(visit.id)
                navAndModalState.update { state ->
                    state.copy(
                        activeMapsVisit = if (state.activeMapsVisit?.id == visit.id) null else state.activeMapsVisit,
                        activeMemoryVisitId = if (state.activeMemoryVisitId == visit.id) null else state.activeMemoryVisitId
                    )
                }
                _snackbarEvents.trySend("\"${visit.locationName}\" dihapus")
            }
        }
    }

    fun sendDateComment(text: String) {
        val tripId = (navAndModalState.value.activeWorkspace as? Workspace.Shared)?.tripId ?: return
        val epochDay = navAndModalState.value.selectedDateEpochDay
        viewModelScope.launch {
            val result = collabRepository.addDateComment(
                tripId = tripId,
                dateEpochDay = epochDay,
                authorName = currentUserName,
                authorPhotoUrl = currentUserPhotoUrl,
                text = text
            )
            result.onFailure { error ->
                _snackbarEvents.trySend(extractErrorMessage(error, "Gagal mengirim komentar."))
            }
        }
    }

    fun deleteDateComment(comment: TripDateComment) {
        val tripId = (navAndModalState.value.activeWorkspace as? Workspace.Shared)?.tripId ?: return
        if (comment.id.isBlank()) return
        viewModelScope.launch {
            val result = collabRepository.deleteDateComment(tripId, comment.id)
            result.onFailure { error ->
                _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menghapus komentar."))
            }
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        val updated = navAndModalState.value.reminderSettings.copy(enabled = enabled)
        updateAndRescheduleReminderSettings(updated)
    }

    fun toggleReminderTiming(option: ReminderTimingOption) {
        val current = navAndModalState.value.reminderSettings
        val nextSet = current.selectedTimings.toMutableSet().apply {
            if (!add(option)) remove(option)
        }
        updateAndRescheduleReminderSettings(current.copy(selectedTimings = nextSet))
    }

    fun setAnnualMemoryReminderEnabled(enabled: Boolean) {
        val updated = navAndModalState.value.reminderSettings.copy(annualMemoryReminderEnabled = enabled)
        updateAndRescheduleReminderSettings(updated)
    }

    private fun updateAndRescheduleReminderSettings(updated: UserReminderSettings) {
        navAndModalState.update { it.copy(reminderSettings = updated) }
        alarmScheduler.saveUserReminderSettings(currentUserId, updated)
        viewModelScope.launch {
            rescheduleAllLocalReminders()
        }
    }

    fun scheduleTestNotification(): AlarmScheduleResult {
        return alarmScheduler.scheduleTestNotification(delaySeconds = 10)
    }

    companion object {
        fun formatActiveSpaceTitle(
            spaceMode: SpaceMode,
            showGroupDirectory: Boolean,
            activeSharedTrip: SharedTrip?,
            memberCount: Int,
            totalGroups: Int
        ): String {
            return when (spaceMode) {
                SpaceMode.PRIBADI -> "Pribadi - hanya di HP ini"
                SpaceMode.GRUP -> {
                    if (!showGroupDirectory && activeSharedTrip != null) {
                        "Grup: ${activeSharedTrip.title} - $memberCount anggota"
                    } else if (totalGroups > 0) {
                        "Grup - $totalGroups grup tersedia"
                    } else {
                        "Grup - belum ada grup"
                    }
                }
            }
        }

        fun canDeleteGroup(trip: SharedTrip, userId: String): Boolean =
            userId.isNotBlank() && trip.ownerId == userId

        fun canLeaveGroupDirectly(trip: SharedTrip, userId: String): Boolean =
            userId.isNotBlank() && trip.ownerId != userId && userId in trip.memberUids

        fun isValidDeleteGroupConfirmation(typedName: String, groupName: String): Boolean =
            groupName.isNotBlank() && typedName.trim() == groupName.trim()

        fun formatDateRange(epochDays: List<Long>): String {
            if (epochDays.isEmpty()) return "Belum ada tanggal kunjungan"
            val minDay = epochDays.min()
            val maxDay = epochDays.max()
            return if (minDay == maxDay) {
                DateUtils.formatIndonesianShortDate(minDay)
            } else {
                "${DateUtils.formatIndonesianShortDate(minDay)} – ${DateUtils.formatIndonesianShortDate(maxDay)}"
            }
        }

        fun buildGroupMemorySummaries(
            roomTrips: List<Trip>,
            sharedTrips: List<SharedTrip>,
            allKunjungan: List<Kunjungan>,
            memoryItems: List<VisitMemoryItem>
        ): List<GroupMemorySummary> {
            val groupTripsById = LinkedHashMap<String, Trip>()
            roomTrips.filter { !it.isPersonal }.forEach { trip ->
                groupTripsById[trip.id] = trip
            }
            sharedTrips.forEach { shared ->
                val uuid = Trip.sharedTripUuid(shared.id)
                if (!groupTripsById.containsKey(uuid)) {
                    groupTripsById[uuid] = Trip(
                        id = uuid,
                        name = shared.title.ifBlank { "Trip Bersama" },
                        isPersonal = false,
                        remoteTripId = shared.id,
                        createdAtMillis = shared.createdAt?.toDate()?.time ?: 0L
                    )
                }
            }

            return groupTripsById.values.map { trip ->
                val groupVisits = allKunjungan.filter { it.tripId == trip.id }
                val groupMemories = memoryItems.filter { it.trip.id == trip.id }
                val allDays = (groupVisits.map { it.visitDateEpochDay } +
                    groupMemories.map { it.kunjungan.visitDateEpochDay }).distinct()

                val chronologicalMemories = groupMemories.sortedWith(
                    compareBy<VisitMemoryItem> { it.kunjungan.visitDateEpochDay }
                        .thenBy { it.kunjungan.visitHour }
                        .thenBy { it.kunjungan.visitMinute }
                        .thenBy { it.kunjungan.createdAtMillis }
                )
                val firstCoverMedia = chronologicalMemories
                    .firstNotNullOfOrNull { item -> item.fotos.firstOrNull() }

                val totalPhotos = groupMemories.sumOf { item -> item.fotos.count { !it.isVideo } }
                val totalVideos = groupMemories.sumOf { item -> item.fotos.count { it.isVideo } }

                val latestVisitKey = (groupMemories.map { it.kunjungan } + groupVisits)
                    .maxOfOrNull { k ->
                        k.visitDateEpochDay * 86_400_000L + k.visitHour * 3_600_000L + k.visitMinute * 60_000L
                    } ?: trip.createdAtMillis

                GroupMemorySummary(
                    trip = trip,
                    dateRangeLabel = formatDateRange(allDays),
                    memoryCount = groupMemories.size,
                    photoCount = totalPhotos,
                    videoCount = totalVideos,
                    coverPhotoPath = firstCoverMedia?.filePath,
                    coverMedia = firstCoverMedia,
                    latestSortKey = latestVisitKey
                )
            }.sortedWith(
                compareByDescending<GroupMemorySummary> { it.latestSortKey }
                    .thenBy { it.trip.name.lowercase() }
            )
        }

        fun filterMemoriesForTab(
            memoryItems: List<VisitMemoryItem>,
            selectedTab: MemoriesFilterTab,
            selectedGroupId: String?,
            searchQuery: String
        ): List<VisitMemoryItem> {
            val base = when (selectedTab) {
                MemoriesFilterTab.SEMUA -> memoryItems
                MemoriesFilterTab.PRIBADI -> memoryItems.filter { it.trip.isPersonal }
                MemoriesFilterTab.GRUP -> {
                    if (selectedGroupId == null) emptyList()
                    else memoryItems.filter { it.trip.id == selectedGroupId }
                }
            }
            val queryLower = searchQuery.trim().lowercase()
            if (queryLower.isEmpty()) return base
            return base.filter { item ->
                item.kunjungan.locationName.lowercase().contains(queryLower) ||
                    item.trip.name.lowercase().contains(queryLower) ||
                    item.kunjungan.tripTitle.lowercase().contains(queryLower) ||
                    item.descriptionSnippet.lowercase().contains(queryLower) ||
                    DateUtils.formatIndonesianFullDate(item.kunjungan.visitDateEpochDay).lowercase().contains(queryLower)
            }
        }

        fun findClosestVisitToNow(
            todayVisits: List<DisplayVisit>,
            nowMillis: Long
        ): DisplayVisit? {
            if (todayVisits.isEmpty()) return null
            return todayVisits.minByOrNull { visit ->
                val visitMillis = DateUtils.targetMillisForVisit(
                    epochDay = visit.visitDateEpochDay,
                    hour = visit.visitHour,
                    minute = visit.visitMinute
                )
                abs(visitMillis - nowMillis)
            }
        }
    }

    class Factory(
        private val localRepository: TripRepository,
        private val collabRepository: CollabRepository,
        private val alarmScheduler: TripAlarmScheduler,
        private val currentUserId: String,
        private val currentUserName: String,
        private val currentUserEmail: String,
        private val currentUserPhotoUrl: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TripBuddyViewModel(
                localRepository = localRepository,
                collabRepository = collabRepository,
                alarmScheduler = alarmScheduler,
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserEmail = currentUserEmail,
                currentUserPhotoUrl = currentUserPhotoUrl
            ) as T
        }
    }
}
