package com.example.ui

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
import com.example.data.TripRepository
import com.example.data.TripVisitEntity
import com.example.notifications.AlarmScheduleResult
import com.example.notifications.TripAlarmScheduler
import com.example.util.DateUtils
import com.google.firebase.firestore.FirebaseFirestoreException
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TripTab {
    BERANDA,
    KALENDER,
    KENANGAN
}

enum class MemoryFilter {
    SEMUA,
    DENGAN_CATATAN
}

sealed interface Workspace {
    data object Local : Workspace
    data class Shared(val tripId: String) : Workspace
}

private data class ActiveTripCollabData(
    val visits: List<SharedVisit> = emptyList(),
    val members: List<TripMember> = emptyList(),
    val comments: List<TripDateComment> = emptyList()
)

private data class NavAndModalState(
    val selectedTab: TripTab = TripTab.BERANDA,
    val activeWorkspace: Workspace = Workspace.Local,
    val currentYear: Int = DateUtils.currentYearAndMonth().first,
    val currentMonth: Int = DateUtils.currentYearAndMonth().second,
    val selectedDateEpochDay: Long = DateUtils.todayEpochDay(),
    val memoryFilter: MemoryFilter = MemoryFilter.SEMUA,
    val memorySearchQuery: String = "",
    val activeMapsVisit: DisplayVisit? = null,
    val activeAddEditDateEpochDay: Long? = null,
    val editingVisit: DisplayVisit? = null,
    val activeMemoryDetailVisit: DisplayVisit? = null,
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
    val upcomingVisits: List<DisplayVisit> = emptyList(),
    val nearestTrip: DisplayVisit? = null,
    val visitedMemories: List<DisplayVisit> = emptyList(),
    val filteredMemories: List<DisplayVisit> = emptyList(),
    val memoryFilter: MemoryFilter = MemoryFilter.SEMUA,
    val memorySearchQuery: String = "",
    val visitsOnSelectedDate: List<DisplayVisit> = emptyList(),
    val upcomingDatesInMonth: Set<Long> = emptySet(),
    val memoryDatesInMonth: Set<Long> = emptySet(),
    val activeMapsVisit: DisplayVisit? = null,
    val activeAddEditDateEpochDay: Long? = null,
    val editingVisit: DisplayVisit? = null,
    val activeMemoryDetailVisit: DisplayVisit? = null,
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

    private fun extractErrorMessage(error: Throwable, fallback: String): String {
        return formatFirestoreErrorForUi(error, fallback)
    }

    private val sharedTripsFlow = collabRepository.observeUserTrips(currentUserId)
        .catch { error ->
            val firestoreError = extractFirestoreException(error)
            if (firestoreError == null && error is CancellationException) throw error
            val msg = extractErrorMessage(error, "Gagal memuat trip bersama.")
            navAndModalState.update { it.copy(collabErrorMessage = msg) }
            emit(emptyList())
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

    val uiState: StateFlow<TripBuddyUiState> = combine(
        localRepository.allVisits,
        sharedTripsFlow,
        activeTripCollabFlow,
        navAndModalState,
        tickerMillis
    ) { localVisits, sharedTrips, collabData, nav, nowMillis ->
        val today = DateUtils.todayEpochDay(nowMillis)

        val activeSharedTrip = (nav.activeWorkspace as? Workspace.Shared)?.let { shared ->
            sharedTrips.find { it.id == shared.tripId }
        }

        val visits: List<DisplayVisit> = when (nav.activeWorkspace) {
            Workspace.Local -> localVisits.map { DisplayVisit.fromLocal(it) }
            is Workspace.Shared -> collabData.visits.map { DisplayVisit.fromShared(it) }
        }

        val upcoming = visits
            .filter { visit -> !visit.completedManually && visit.visitDateEpochDay >= today }
            .sortedWith(
                compareBy<DisplayVisit> { it.visitDateEpochDay }
                    .thenBy { it.visitHour }
                    .thenBy { it.visitMinute }
            )

        val memories = visits
            .filter { visit -> visit.completedManually || visit.visitDateEpochDay < today }
            .sortedWith(
                compareByDescending<DisplayVisit> { it.visitDateEpochDay }
                    .thenByDescending { it.visitHour }
                    .thenByDescending { it.visitMinute }
                    .thenByDescending { it.createdAtMillis }
            )

        val queryLower = nav.memorySearchQuery.trim().lowercase()
        val filteredMemories = memories.filter { visit ->
            val matchesFilter = when (nav.memoryFilter) {
                MemoryFilter.SEMUA -> true
                MemoryFilter.DENGAN_CATATAN -> visit.memoryNotes.isNotBlank()
            }
            val matchesSearch = if (queryLower.isEmpty()) {
                true
            } else {
                visit.locationName.lowercase().contains(queryLower) ||
                    visit.tripTitle.lowercase().contains(queryLower) ||
                    visit.memoryNotes.lowercase().contains(queryLower)
            }
            matchesFilter && matchesSearch
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
        val memoryDates = mutableSetOf<Long>()
        visits.forEach { visit ->
            if (visit.visitDateEpochDay in monthStart..monthEnd) {
                if (!visit.completedManually && visit.visitDateEpochDay >= today) {
                    upcomingDates.add(visit.visitDateEpochDay)
                } else {
                    memoryDates.add(visit.visitDateEpochDay)
                }
            }
        }

        val refreshedMapsVisit = nav.activeMapsVisit?.let { current ->
            visits.find { it.id == current.id } ?: current
        }
        val refreshedMemoryVisit = nav.activeMemoryDetailVisit?.let { current ->
            visits.find { it.id == current.id } ?: current
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
            upcomingVisits = upcoming,
            nearestTrip = upcoming.firstOrNull(),
            visitedMemories = memories,
            filteredMemories = filteredMemories,
            memoryFilter = nav.memoryFilter,
            memorySearchQuery = nav.memorySearchQuery,
            visitsOnSelectedDate = selectedVisits,
            upcomingDatesInMonth = upcomingDates,
            memoryDatesInMonth = memoryDates,
            activeMapsVisit = refreshedMapsVisit,
            activeAddEditDateEpochDay = nav.activeAddEditDateEpochDay,
            editingVisit = nav.editingVisit,
            activeMemoryDetailVisit = refreshedMemoryVisit,
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
        navAndModalState.update { it.copy(selectedTab = tab, showMembersScreen = false) }
    }

    fun selectWorkspace(workspace: Workspace) {
        navAndModalState.update {
            it.copy(
                activeWorkspace = workspace,
                showMembersScreen = false,
                activeMapsVisit = null,
                activeAddEditDateEpochDay = null,
                editingVisit = null,
                activeMemoryDetailVisit = null
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

    fun openMemoryDetailSheet(visit: DisplayVisit) {
        navAndModalState.update { it.copy(activeMemoryDetailVisit = visit) }
    }

    fun dismissMemoryDetailSheet() {
        navAndModalState.update { it.copy(activeMemoryDetailVisit = null) }
    }

    fun setMemoryFilter(filter: MemoryFilter) {
        navAndModalState.update { it.copy(memoryFilter = filter) }
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
                    alarmScheduler.scheduleVisitReminder(
                        existing.copy(
                            locationName = cleanLocation,
                            tripTitle = tripTitle.trim(),
                            visitDateEpochDay = epochDay,
                            visitHour = hour,
                            visitMinute = minute,
                            planNotes = planNotes.trim()
                        )
                    )
                    dismissAddEditSheet()
                    _snackbarEvents.trySend("Lokasi \"$cleanLocation\" diperbarui untuk semua anggota")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menyimpan perubahan lokasi."))
                }
            } else if (existing == null && workspace is Workspace.Shared) {
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
                    val createdDisplay = DisplayVisit(
                        id = "shared_$docId",
                        localRoomId = null,
                        sharedTripId = workspace.tripId,
                        sharedDocId = docId,
                        locationName = cleanLocation,
                        tripTitle = tripTitle.trim(),
                        visitDateEpochDay = epochDay,
                        visitHour = hour,
                        visitMinute = minute,
                        planNotes = planNotes.trim(),
                        memoryNotes = "",
                        completedManually = false,
                        createdByName = currentUserName,
                        createdAtMillis = System.currentTimeMillis()
                    )
                    alarmScheduler.scheduleVisitReminder(createdDisplay)
                    dismissAddEditSheet()
                    _snackbarEvents.trySend("Lokasi \"$cleanLocation\" ditambahkan ke trip bersama")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menambahkan lokasi."))
                }
            } else if (existing != null && existing.localRoomId != null) {
                val updated = existing.toLocalEntity().copy(
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim()
                )
                localRepository.updateVisit(updated)
                alarmScheduler.scheduleVisitReminder(updated)
                dismissAddEditSheet()
                _snackbarEvents.trySend("Lokasi \"$cleanLocation\" disimpan beserta pengingatnya")
            } else {
                val newVisit = TripVisitEntity(
                    locationName = cleanLocation,
                    tripTitle = tripTitle.trim(),
                    visitDateEpochDay = epochDay,
                    visitHour = hour,
                    visitMinute = minute,
                    planNotes = planNotes.trim()
                )
                val insertedId = localRepository.insertVisit(newVisit)
                alarmScheduler.scheduleVisitReminder(newVisit.copy(id = insertedId))
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
                    alarmScheduler.scheduleVisitReminder(visit.copy(locationName = clean))
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui nama lokasi."))
                }
            } else if (visit.localRoomId != null) {
                val updated = visit.toLocalEntity().copy(locationName = clean)
                localRepository.updateVisit(updated)
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
                    val updatedDisplay = visit.copy(completedManually = nextCompleted)
                    if (nextCompleted) {
                        alarmScheduler.cancelVisitReminder(updatedDisplay)
                    } else {
                        alarmScheduler.scheduleVisitReminder(updatedDisplay)
                    }
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal memperbarui status lokasi."))
                }
            } else if (visit.localRoomId != null) {
                val updated = visit.toLocalEntity().copy(isCompletedManually = nextCompleted)
                localRepository.updateVisit(updated)
                if (updated.isCompletedManually) {
                    alarmScheduler.cancelVisitReminder(updated.id)
                } else {
                    alarmScheduler.scheduleVisitReminder(updated)
                }
            }
        }
    }

    fun saveMemoryNotes(visit: DisplayVisit, memoryNotes: String) {
        val cleanNotes = memoryNotes.trim()
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
                    memoryNotes = cleanNotes,
                    completedManually = visit.completedManually
                )
                result.onSuccess {
                    dismissMemoryDetailSheet()
                    _snackbarEvents.trySend("Catatan kenangan disimpan")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menyimpan catatan kenangan."))
                }
            } else if (visit.localRoomId != null) {
                localRepository.updateVisit(visit.toLocalEntity().copy(memoryNotes = cleanNotes))
                dismissMemoryDetailSheet()
                _snackbarEvents.trySend("Catatan kenangan disimpan")
            }
        }
    }

    fun deleteVisit(visit: DisplayVisit) {
        viewModelScope.launch {
            if (visit.isShared && visit.sharedTripId != null && visit.sharedDocId != null) {
                val result = collabRepository.deleteSharedVisit(visit.sharedTripId, visit.sharedDocId)
                result.onSuccess {
                    alarmScheduler.cancelVisitReminder(visit)
                    navAndModalState.update { state ->
                        state.copy(
                            activeMapsVisit = if (state.activeMapsVisit?.id == visit.id) null else state.activeMapsVisit,
                            activeMemoryDetailVisit = if (state.activeMemoryDetailVisit?.id == visit.id) null else state.activeMemoryDetailVisit
                        )
                    }
                    _snackbarEvents.trySend("\"${visit.locationName}\" dihapus")
                }.onFailure { error ->
                    _snackbarEvents.trySend(extractErrorMessage(error, "Gagal menghapus lokasi."))
                }
            } else if (visit.localRoomId != null) {
                alarmScheduler.cancelVisitReminder(visit.localRoomId)
                localRepository.deleteVisitById(visit.localRoomId)
                navAndModalState.update { state ->
                    state.copy(
                        activeMapsVisit = if (state.activeMapsVisit?.id == visit.id) null else state.activeMapsVisit,
                        activeMemoryDetailVisit = if (state.activeMemoryDetailVisit?.id == visit.id) null else state.activeMemoryDetailVisit
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
