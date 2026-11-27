package com.example.ui

import android.content.Context
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
import com.example.notifications.AlarmScheduleResult
import com.example.notifications.TripAlarmScheduler
import com.example.util.DateUtils
import com.example.util.PhotoStorageHelper
import kotlinx.coroutines.CancellationException
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
import java.io.File
import java.util.UUID
import kotlin.math.abs

enum class TripTab {
    BERANDA,
    KALENDER,
    KENANGAN
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
    val coverPhotoPath: String? get() = fotos.firstOrNull()?.filePath
    val descriptionSnippet: String get() = kunjungan.memoryNotes.ifBlank { kunjungan.planNotes }.trim()
    fun toDisplayVisit(): DisplayVisit = DisplayVisit.fromKunjungan(kunjungan, trip.name)
}

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
    val activeWorkspace: Workspace = Workspace.Local,
    val currentYear: Int = DateUtils.currentYearAndMonth().first,
    val currentMonth: Int = DateUtils.currentYearAndMonth().second,
    val selectedDateEpochDay: Long = DateUtils.todayEpochDay(),
    val selectedMemoryTripId: String? = null,
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
    val collabErrorMessage: String? = null
)

data class TripBuddyUiState(
    val currentUserId: String = "",
    val currentUserName: String = "",
    val currentUserEmail: String = "",
    val currentUserPhotoUrl: String = "",
    val activeProjectId: String = "",
    val activeDatabaseId: String = "",
    val selectedTab: TripTab = TripTab.BERANDA,
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
    val photoCountByVisitId: Map<String, Int> = emptyMap(),
    val activeVisitMemoryDetail: VisitMemoryItem? = null,
    val selectedMemoryTripId: String? = null,
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
    val collabErrorMessage: String? = null
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

    init {
        viewModelScope.launch {
            localRepository.ensurePersonalTripExists()
        }
        // Background sync: keep ALL shared trips and ALL their visits synced into Room
        // without wiping local memories or photos when offline or switching workspaces.
        viewModelScope.launch {
            lastSyncedSharedTrips
                .flatMapLatest { trips ->
                    if (trips.isEmpty()) {
                        flowOf(Unit)
                    } else {
                        trips.map { trip ->
                            collabRepository.observeTripVisits(trip.id)
                                .onEach { remoteVisits ->
                                    localRepository.syncSharedVisitsToRoom(trip, remoteVisits)
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

    private fun extractErrorMessage(error: Throwable, fallback: String): String {
        return formatFirestoreErrorForUi(error, fallback)
    }

    private val sharedTripsFlow = collabRepository.observeUserTrips(currentUserId)
        .onEach { trips ->
            lastSyncedSharedTrips.value = trips
            localRepository.syncSharedTripsToRoom(trips)
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
        )
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

        val queryLower = nav.memorySearchQuery.trim().lowercase()
        val filteredVisitMemories = room.memoryItems.filter { item ->
            val matchesTrip = nav.selectedMemoryTripId == null || item.trip.id == nav.selectedMemoryTripId
            val matchesSearch = if (queryLower.isEmpty()) {
                true
            } else {
                item.kunjungan.locationName.lowercase().contains(queryLower) ||
                    item.trip.name.lowercase().contains(queryLower) ||
                    item.kunjungan.tripTitle.lowercase().contains(queryLower) ||
                    item.descriptionSnippet.lowercase().contains(queryLower) ||
                    DateUtils.formatIndonesianFullDate(item.kunjungan.visitDateEpochDay).lowercase().contains(queryLower)
            }
            matchesTrip && matchesSearch
        }

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

        TripBuddyUiState(
            currentUserId = currentUserId,
            currentUserName = currentUserName,
            currentUserEmail = currentUserEmail,
            currentUserPhotoUrl = currentUserPhotoUrl,
            activeProjectId = collabRepository.getActiveProjectId(),
            activeDatabaseId = collabRepository.getActiveDatabaseId(),
            selectedTab = nav.selectedTab,
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
            photoCountByVisitId = photoCountByVisitId,
            activeVisitMemoryDetail = room.activeDetail,
            selectedMemoryTripId = nav.selectedMemoryTripId,
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
            collabErrorMessage = nav.collabErrorMessage
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
        navAndModalState.update {
            it.copy(
                selectedTab = tab,
                showMembersScreen = false,
                activeMemoryVisitId = null
            )
        }
    }

    fun selectWorkspace(workspace: Workspace) {
        navAndModalState.update {
            it.copy(
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
                        activeWorkspace = Workspace.Shared(trip.id),
                        showInviteSheetForTrip = trip
                    )
                }
                _snackbarEvents.trySend("Trip bersama \"${trip.title}\" berhasil dibuat")
            }.onFailure { error ->
                val errorText = extractErrorMessage(error, "Gagal membuat trip bersama.")
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
                        activeWorkspace = Workspace.Shared(tripId),
                        selectedTab = TripTab.KALENDER
                    )
                }
                _snackbarEvents.trySend("Berhasil bergabung ke trip bersama!")
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

    fun deleteMemoryPhoto(foto: Foto) {
        viewModelScope.launch {
            localRepository.deleteFoto(foto)
            _snackbarEvents.trySend("Foto dihapus dari aplikasi")
        }
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

    fun setMemoryTripFilter(tripId: String?) {
        navAndModalState.update { it.copy(selectedMemoryTripId = tripId) }
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
                    alarmScheduler.scheduleVisitReminder(updatedEntity)
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
                    alarmScheduler.scheduleVisitReminder(entity)
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
                alarmScheduler.scheduleVisitReminder(updated)
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
                alarmScheduler.scheduleVisitReminder(newVisit)
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
                    alarmScheduler.scheduleVisitReminder(updated)
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui nama lokasi."))
                }
            } else {
                val updated = visit.toKunjunganEntity().copy(locationName = clean)
                localRepository.upsertKunjungan(updated)
                alarmScheduler.scheduleVisitReminder(updated)
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
                    if (nextCompleted) {
                        alarmScheduler.cancelVisitReminder(updated.id)
                    } else {
                        alarmScheduler.scheduleVisitReminder(updated)
                    }
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui status lokasi."))
                }
            } else {
                val updated = visit.toKunjunganEntity().copy(isCompletedManually = nextCompleted)
                localRepository.upsertKunjungan(updated)
                if (updated.isCompletedManually) {
                    alarmScheduler.cancelVisitReminder(updated.id)
                } else {
                    alarmScheduler.scheduleVisitReminder(updated)
                }
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

    fun scheduleTestNotification(): AlarmScheduleResult {
        return alarmScheduler.scheduleTestNotification(delaySeconds = 10)
    }

    companion object {
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
