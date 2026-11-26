package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOutFromGoogle
import com.example.collab.CollabRepository
import com.example.data.TripBuddyDatabase
import com.example.data.TripRepository
import com.example.notifications.TripAlarmScheduler
import com.example.notifications.TripNotificationHelper
import com.example.ui.TripBuddyViewModel
import com.example.ui.TripTab
import com.example.ui.components.AccountProfileSheet
import com.example.ui.components.AddLocationSheet
import com.example.ui.components.CollabWorkspaceBar
import com.example.ui.components.CreateSharedTripDialog
import com.example.ui.components.InviteMemberSheet
import com.example.ui.components.JoinTripByCodeDialog
import com.example.ui.components.MapsSearchSheet
import com.example.ui.components.MemoryDetailSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.TripMembersScreen
import com.example.ui.theme.TripBuddyTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var deepLinkInviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        TripNotificationHelper.createNotificationChannel(applicationContext)
        deepLinkInviteCode = extractInviteCodeFromIntent(intent)

        val database = TripBuddyDatabase.getInstance(applicationContext)
        val localRepository = TripRepository(database.tripVisitDao())
        val alarmScheduler = TripAlarmScheduler(applicationContext)
        val firestore = CollabRepository.getConfiguredFirestore(applicationContext)
        val configuredDatabaseId = CollabRepository.resolveConfiguredDatabaseId(applicationContext)
        val collabRepository = CollabRepository(firestore, Firebase.auth, configuredDatabaseId)

        setContent {
            TripBuddyTheme {
                val currentUser by Firebase.auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

                if (currentUser == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    val user = currentUser!!
                    key(user.uid) {
                        val viewModel: TripBuddyViewModel = viewModel(
                            key = user.uid,
                            factory = TripBuddyViewModel.Factory(
                                localRepository = localRepository,
                                collabRepository = collabRepository,
                                alarmScheduler = alarmScheduler,
                                currentUserId = user.uid,
                                currentUserName = user.displayName?.takeIf { it.isNotBlank() }
                                    ?: user.email?.substringBefore("@")
                                    ?: "Pengguna TripBuddy",
                                currentUserEmail = user.email.orEmpty(),
                                currentUserPhotoUrl = user.photoUrl?.toString().orEmpty()
                            )
                        )

                        LaunchedEffect(deepLinkInviteCode) {
                            val code = deepLinkInviteCode
                            if (!code.isNullOrBlank()) {
                                viewModel.openJoinTripDialog(code)
                                deepLinkInviteCode = null
                            }
                        }

                        TripBuddyApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractInviteCodeFromIntent(intent)?.let { code ->
            deepLinkInviteCode = code
        }
    }

    private fun extractInviteCodeFromIntent(intent: Intent?): String? {
        val dataString = intent?.dataString ?: return null
        return CollabRepository.extractInviteCode(dataString)
    }
}

private data class NavTabItem(
    val tab: TripTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun TripBuddyApp(viewModel: TripBuddyViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var pendingTestAfterPermission by remember { mutableStateOf(false) }

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState.collabErrorMessage) {
        val err = uiState.collabErrorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearCollabError()
        }
    }

    val scheduleTestAndNotifyUser: () -> Unit = {
        val result = viewModel.scheduleTestNotification()
        coroutineScope.launch {
            if (!result.scheduled) {
                snackbarHostState.showSnackbar("Gagal menjadwalkan notifikasi tes.")
            } else if (result.usedExactAlarm || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                snackbarHostState.showSnackbar(
                    message = "Tes notifikasi dijadwalkan. Akan muncul dalam 10 detik.",
                    duration = SnackbarDuration.Short
                )
            } else {
                val snackbarResult = snackbarHostState.showSnackbar(
                    message = "Tes dijadwalkan (10 detik). Aktifkan Alarm Tepat Waktu agar selalu presisi.",
                    actionLabel = "Izinkan",
                    duration = SnackbarDuration.Long
                )
                if (snackbarResult == SnackbarResult.ActionPerformed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val settingsIntent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(settingsIntent)
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingTestAfterPermission) {
                pendingTestAfterPermission = false
                scheduleTestAndNotifyUser()
            }
        } else {
            if (pendingTestAfterPermission) {
                pendingTestAfterPermission = false
                showMessage("Izin notifikasi diperlukan untuk menampilkan pengingat.")
            }
        }
    }

    LaunchedEffect(Unit) {
        TripNotificationHelper.createNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = context.getSharedPreferences("tripbuddy_prefs", Context.MODE_PRIVATE)
            val alreadyRequested = prefs.getBoolean("asked_post_notifications", false)
            if (!TripNotificationHelper.hasNotificationPermission(context) && !alreadyRequested) {
                prefs.edit().putBoolean("asked_post_notifications", true).apply()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val navItems = remember {
        listOf(
            NavTabItem(
                tab = TripTab.BERANDA,
                labelRes = R.string.tab_home,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_beranda"
            ),
            NavTabItem(
                tab = TripTab.KALENDER,
                labelRes = R.string.tab_calendar,
                selectedIcon = Icons.Rounded.CalendarMonth,
                unselectedIcon = Icons.Outlined.CalendarMonth,
                testTag = "nav_tab_kalender"
            ),
            NavTabItem(
                tab = TripTab.KENANGAN,
                labelRes = R.string.tab_memories,
                selectedIcon = Icons.Rounded.AutoAwesome,
                unselectedIcon = Icons.Outlined.AutoAwesome,
                testTag = "nav_tab_kenangan"
            )
        )
    }

    if (uiState.showMembersScreen && uiState.activeSharedTrip != null) {
        TripMembersScreen(
            trip = uiState.activeSharedTrip!!,
            members = uiState.activeTripMembers,
            currentUserId = uiState.currentUserId,
            onBack = viewModel::closeMembersScreen,
            onInviteClick = { viewModel.openInviteSheet() }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            navItems.forEach { item ->
                                val selected = uiState.selectedTab == item.tab
                                val labelText = stringResource(item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = labelText
                                        )
                                    },
                                    label = { Text(labelText) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            navItems.forEach { item ->
                                val selected = uiState.selectedTab == item.tab
                                val labelText = stringResource(item.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = labelText
                                        )
                                    },
                                    label = { Text(labelText) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        CollabWorkspaceBar(
                            activeWorkspace = uiState.activeWorkspace,
                            sharedTrips = uiState.sharedTrips,
                            activeSharedTrip = uiState.activeSharedTrip,
                            activeTripMembers = uiState.activeTripMembers,
                            userName = uiState.currentUserName,
                            userPhotoUrl = uiState.currentUserPhotoUrl,
                            onSelectWorkspace = viewModel::selectWorkspace,
                            onCreateTripClick = viewModel::openCreateTripDialog,
                            onJoinTripClick = { viewModel.openJoinTripDialog() },
                            onOpenMembersClick = viewModel::openMembersScreen,
                            onInviteClick = { viewModel.openInviteSheet() },
                            onAccountClick = viewModel::openAccountSheet
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            Crossfade(
                                targetState = uiState.selectedTab,
                                label = "TripTabTransition"
                            ) { currentTab ->
                                when (currentTab) {
                                    TripTab.BERANDA -> HomeScreen(
                                        uiState = uiState,
                                        onOpenCalendar = { viewModel.selectTab(TripTab.KALENDER) },
                                        onOpenMemories = { viewModel.selectTab(TripTab.KENANGAN) },
                                        onOpenAddToday = {
                                            viewModel.selectTab(TripTab.KALENDER)
                                            viewModel.openAddLocationSheet(uiState.todayEpochDay)
                                        },
                                        onOpenMapsForVisit = { visit -> viewModel.openMapsSearchSheet(visit) },
                                        onToggleComplete = { visit ->
                                            viewModel.toggleManualCompleted(visit)
                                            showMessage("\"${visit.locationName}\" dipindahkan ke Kenangan")
                                        },
                                        onTestNotificationClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                !TripNotificationHelper.hasNotificationPermission(context)
                                            ) {
                                                pendingTestAfterPermission = true
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                scheduleTestAndNotifyUser()
                                            }
                                        }
                                    )

                                    TripTab.KALENDER -> CalendarScreen(
                                        uiState = uiState,
                                        onBackToHome = { viewModel.selectTab(TripTab.BERANDA) },
                                        onPreviousMonth = viewModel::goToPreviousMonth,
                                        onNextMonth = viewModel::goToNextMonth,
                                        onJumpToToday = viewModel::jumpToToday,
                                        onDateClick = viewModel::onCalendarDateClicked,
                                        onAddLocationForSelectedDate = {
                                            viewModel.openAddLocationSheet(uiState.selectedDateEpochDay)
                                        },
                                        onOpenMapsForVisit = { visit -> viewModel.openMapsSearchSheet(visit) },
                                        onToggleComplete = { visit ->
                                            viewModel.toggleManualCompleted(visit)
                                            val msg = if (!visit.completedManually) {
                                                "\"${visit.locationName}\" ditandai selesai"
                                            } else {
                                                "\"${visit.locationName}\" dikembalikan ke rencana"
                                            }
                                            showMessage(msg)
                                        },
                                        onEditVisit = { visit -> viewModel.openEditLocationSheet(visit) },
                                        onDeleteVisit = { visit -> viewModel.deleteVisit(visit) },
                                        onSendComment = viewModel::sendDateComment,
                                        onDeleteComment = viewModel::deleteDateComment
                                    )

                                    TripTab.KENANGAN -> MemoriesScreen(
                                        uiState = uiState,
                                        onBackToHome = { viewModel.selectTab(TripTab.BERANDA) },
                                        onSearchQueryChange = viewModel::setMemorySearchQuery,
                                        onFilterChange = viewModel::setMemoryFilter,
                                        onMemoryClick = viewModel::openMemoryDetailSheet,
                                        onOpenMapsForVisit = { visit -> viewModel.openMapsSearchSheet(visit) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCreateTripDialog) {
        CreateSharedTripDialog(
            isSubmitting = uiState.isSubmittingCollab,
            errorMessage = uiState.createTripError,
            onDismiss = viewModel::dismissCreateTripDialog,
            onCreate = viewModel::createSharedTrip
        )
    }

    if (uiState.showJoinTripDialog) {
        JoinTripByCodeDialog(
            initialCode = uiState.joinTripPrefillCode,
            isSubmitting = uiState.isSubmittingCollab,
            errorMessage = uiState.joinTripError,
            onDismiss = viewModel::dismissJoinTripDialog,
            onJoin = viewModel::joinTripByCode
        )
    }

    uiState.showInviteSheetForTrip?.let { trip ->
        InviteMemberSheet(
            trip = trip,
            onDismiss = viewModel::dismissInviteSheet,
            onShowMessage = showMessage
        )
    }

    if (uiState.showAccountSheet) {
        AccountProfileSheet(
            displayName = uiState.currentUserName,
            email = uiState.currentUserEmail,
            photoUrl = uiState.currentUserPhotoUrl,
            projectId = uiState.activeProjectId,
            databaseId = uiState.activeDatabaseId,
            isTestingFirestore = uiState.isTestingFirestore,
            firestoreTestResult = uiState.firestoreTestResult,
            onDismiss = viewModel::dismissAccountSheet,
            onCreateTripClick = viewModel::openCreateTripDialog,
            onJoinTripClick = { viewModel.openJoinTripDialog() },
            onTestFirestoreConnection = viewModel::testFirestoreConnection,
            onSignOutClick = {
                signOutFromGoogle(
                    credentialManager = credentialManager,
                    scope = coroutineScope
                )
            }
        )
    }

    uiState.activeAddEditDateEpochDay?.let { epochDay ->
        AddLocationSheet(
            epochDay = epochDay,
            editingVisit = uiState.editingVisit,
            onDismiss = viewModel::dismissAddEditSheet,
            onSave = { locationName, tripTitle, hour, minute, planNotes ->
                viewModel.saveLocationVisit(
                    locationName = locationName,
                    tripTitle = tripTitle,
                    epochDay = epochDay,
                    hour = hour,
                    minute = minute,
                    planNotes = planNotes
                )
            }
        )
    }

    uiState.activeMapsVisit?.let { visit ->
        MapsSearchSheet(
            visit = visit,
            onDismiss = viewModel::dismissMapsSearchSheet,
            onUpdateLocationName = { updatedName ->
                viewModel.updateLocationNameFromMapsSheet(visit, updatedName)
            },
            onShowMessage = showMessage
        )
    }

    uiState.activeMemoryDetailVisit?.let { visit ->
        MemoryDetailSheet(
            visit = visit,
            todayEpochDay = uiState.todayEpochDay,
            onDismiss = viewModel::dismissMemoryDetailSheet,
            onSaveMemoryNotes = { notes ->
                viewModel.saveMemoryNotes(visit, notes)
            },
            onOpenMaps = { viewModel.openMapsSearchSheet(visit) },
            onRevertCompleted = {
                viewModel.toggleManualCompleted(visit)
                showMessage("\"${visit.locationName}\" dikembalikan ke rencana")
            },
            onDelete = {
                viewModel.deleteVisit(visit)
            }
        )
    }
}
