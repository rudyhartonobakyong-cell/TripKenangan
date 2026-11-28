package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.authStateFlow
import com.example.auth.signOutFromGoogle
import com.example.collab.CollabRepository
import com.example.data.Trip
import com.example.data.TripBuddyDatabase
import com.example.data.TripRepository
import com.example.notifications.TripAlarmScheduler
import com.example.notifications.TripNotificationHelper
import com.example.ui.DisplayVisit
import com.example.ui.SpaceMode
import com.example.ui.TripBuddyViewModel
import com.example.ui.TripTab
import com.example.ui.components.AccountProfileSheet
import com.example.ui.components.AddLocationSheet
import com.example.ui.components.BackupImportConfirmDialog
import com.example.ui.components.BackupProgressDialog
import com.example.ui.components.BackupStatusDialog
import com.example.ui.components.CameraCaptureModeDialog
import com.example.ui.components.CameraVisitPickerDialog
import com.example.ui.components.CollabWorkspaceBar
import com.example.ui.components.CreateSharedTripDialog
import com.example.ui.components.InviteMemberSheet
import com.example.ui.components.JoinTripByCodeDialog
import com.example.ui.components.MapsSearchSheet
import com.example.ui.components.PdfExportOptionsSheet
import com.example.ui.components.PdfExportProgressDialog
import com.example.ui.components.PdfExportReadyDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.GroupDirectoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.TripMembersScreen
import com.example.ui.screens.VisitMemoryDetailScreen
import com.example.ui.theme.AppThemeOption
import com.example.ui.theme.ThemePreferences
import com.example.ui.theme.TripBuddyTheme
import com.example.util.PdfExportScope
import com.example.util.PdfExporter
import com.example.util.PhotoStorageHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private var deepLinkInviteCode by mutableStateOf<String?>(null)
    private var deepLinkVisitId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        TripNotificationHelper.createNotificationChannel(applicationContext)
        deepLinkInviteCode = extractInviteCodeFromIntent(intent)
        deepLinkVisitId = extractVisitIdFromIntent(intent)

        val database = TripBuddyDatabase.getInstance(applicationContext)
        val localRepository = TripRepository(
            tripDao = database.tripDao(),
            kunjunganDao = database.kunjunganDao(),
            fotoDao = database.fotoDao()
        )
        val alarmScheduler = TripAlarmScheduler(applicationContext)
        val firestore = CollabRepository.getConfiguredFirestore(applicationContext)
        val configuredDatabaseId = CollabRepository.resolveConfiguredDatabaseId(applicationContext)
        val collabRepository = CollabRepository(firestore, Firebase.auth, configuredDatabaseId)

        setContent {
            var selectedTheme by remember {
                mutableStateOf(ThemePreferences.loadTheme(applicationContext))
            }

            TripBuddyTheme(appTheme = selectedTheme) {
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

                        LaunchedEffect(deepLinkVisitId) {
                            val visitId = deepLinkVisitId
                            if (!visitId.isNullOrBlank()) {
                                viewModel.openVisitMemoryDetail(visitId)
                                deepLinkVisitId = null
                            }
                        }

                        TripBuddyApp(
                            viewModel = viewModel,
                            selectedTheme = selectedTheme,
                            onSelectTheme = { newTheme ->
                                selectedTheme = newTheme
                                ThemePreferences.saveTheme(applicationContext, newTheme)
                            }
                        )
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
        extractVisitIdFromIntent(intent)?.let { visitId ->
            deepLinkVisitId = visitId
        }
    }

    private fun extractInviteCodeFromIntent(intent: Intent?): String? {
        val dataString = intent?.dataString ?: return null
        return CollabRepository.extractInviteCode(dataString)
    }

    private fun extractVisitIdFromIntent(intent: Intent?): String? {
        return intent?.getStringExtra(TripNotificationHelper.EXTRA_OPEN_KUNJUNGAN_ID)
            ?.takeIf { it.isNotBlank() }
    }
}

private data class NavTabItem(
    val tab: TripTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private class CaptureVideoWithLimitContract(
    private val maxDurationSeconds: Int = 60
) : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxDurationSeconds)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}

@Composable
fun TripBuddyApp(
    viewModel: TripBuddyViewModel,
    selectedTheme: AppThemeOption,
    onSelectTheme: (AppThemeOption) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var pendingTestAfterPermission by remember { mutableStateOf(false) }

    val alarmScheduler = remember(context) { TripAlarmScheduler(context.applicationContext) }
    var hasNotificationPermission by remember {
        mutableStateOf(TripNotificationHelper.hasNotificationPermission(context))
    }
    var canScheduleExactAlarms by remember {
        mutableStateOf(alarmScheduler.canScheduleExactAlarms())
    }
    var showNotificationRationaleDialog by remember { mutableStateOf(false) }
    var showExactAlarmRationaleDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = TripNotificationHelper.hasNotificationPermission(context)
                canScheduleExactAlarms = alarmScheduler.canScheduleExactAlarms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Survive Activity recreation while the external camera app is open
    var pendingCameraModeVisit by remember { mutableStateOf<DisplayVisit?>(null) }
    var pendingCameraVisitId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTempCapturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTempVideoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val showMessage: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val visitId = pendingCameraVisitId
        val capturePath = pendingTempCapturePath
        pendingCameraVisitId = null
        pendingTempCapturePath = null

        val captureFile = capturePath?.let { File(it) }
        if (success && !visitId.isNullOrBlank() && captureFile != null && captureFile.exists() && captureFile.length() > 0L) {
            viewModel.saveCapturedPhoto(
                context = context,
                kunjunganId = visitId,
                tempCaptureFile = captureFile
            )
        } else {
            PhotoStorageHelper.deleteFileQuietly(capturePath)
        }
    }

    val recordVideoLauncher = rememberLauncherForActivityResult(
        contract = CaptureVideoWithLimitContract(maxDurationSeconds = 60)
    ) { success ->
        val visitId = pendingCameraVisitId
        val videoPath = pendingTempVideoPath
        pendingCameraVisitId = null
        pendingTempVideoPath = null

        val videoFile = videoPath?.let { File(it) }
        if (success && !visitId.isNullOrBlank() && videoFile != null && videoFile.exists() && videoFile.length() > 0L) {
            viewModel.saveCapturedVideo(
                context = context,
                kunjunganId = visitId,
                tempVideoFile = videoFile
            )
        } else {
            PhotoStorageHelper.deleteFileQuietly(videoPath)
        }
    }

    val launchCameraForVisitId: (String) -> Unit = { visitId ->
        try {
            val tempFile = PhotoStorageHelper.createTempCaptureFile(context)
            val uri = PhotoStorageHelper.getUriForFile(context, tempFile)
            pendingCameraVisitId = visitId
            pendingTempCapturePath = tempFile.absolutePath
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCameraVisitId = null
            pendingTempCapturePath = null
            showMessage("Kamera tidak dapat dibuka: ${e.localizedMessage ?: "periksa aplikasi kamera"}")
        }
    }

    val launchVideoForVisitId: (String) -> Unit = { visitId ->
        try {
            val tempFile = PhotoStorageHelper.createTempVideoFile(context)
            val uri = PhotoStorageHelper.getUriForFile(context, tempFile)
            pendingCameraVisitId = visitId
            pendingTempVideoPath = tempFile.absolutePath
            recordVideoLauncher.launch(uri)
        } catch (e: Exception) {
            pendingCameraVisitId = null
            pendingTempVideoPath = null
            showMessage("Perekam video tidak dapat dibuka: ${e.localizedMessage ?: "periksa aplikasi kamera"}")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val visitId = pendingCameraVisitId
        if (granted && !visitId.isNullOrBlank()) {
            launchCameraForVisitId(visitId)
        } else {
            pendingCameraVisitId = null
            showMessage("Izin kamera diperlukan untuk memotret foto kenangan.")
        }
    }

    val videoPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val visitId = pendingCameraVisitId
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
            PhotoStorageHelper.hasCameraPermission(context)
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
            PhotoStorageHelper.hasVideoPermissions(context)
        if (cameraGranted && audioGranted && !visitId.isNullOrBlank()) {
            launchVideoForVisitId(visitId)
        } else {
            pendingCameraVisitId = null
            showMessage("Izin kamera dan mikrofon diperlukan untuk merekam video kenangan.")
        }
    }

    val startPhotoCaptureForVisit: (DisplayVisit) -> Unit = { visit ->
        if (PhotoStorageHelper.hasCameraPermission(context)) {
            launchCameraForVisitId(visit.id)
        } else {
            pendingCameraVisitId = visit.id
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val startVideoCaptureForVisit: (DisplayVisit) -> Unit = { visit ->
        if (PhotoStorageHelper.hasVideoPermissions(context)) {
            launchVideoForVisitId(visit.id)
        } else {
            pendingCameraVisitId = visit.id
            videoPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    val requestCameraForVisit: (DisplayVisit) -> Unit = { visit ->
        pendingCameraModeVisit = visit
    }

    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val exported = uiState.exportedPdfResult ?: return@rememberLauncherForActivityResult
        if (destUri != null) {
            coroutineScope.launch {
                val copyResult = withContext(Dispatchers.IO) {
                    PdfExporter.copyFileToUri(context, exported.cacheFile, destUri)
                }
                copyResult.onSuccess {
                    showMessage("PDF berhasil disimpan: ${exported.fileName}")
                }.onFailure { err ->
                    showMessage("Gagal menyimpan PDF: ${err.localizedMessage ?: "terjadi kesalahan"}")
                }
            }
        }
    }

    val importBackupZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupZipForImport(context, uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareBackupEvents.collect { exported ->
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, exported.contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, exported.fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Simpan atau Bagikan Cadangan TripBuddy")
                )
            } catch (e: Exception) {
                showMessage(
                    "File cadangan siap (${exported.fileName}), namun dialog bagikan gagal dibuka: ${e.localizedMessage ?: "periksa aplikasi tujuan"}"
                )
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.themeRestoredEvents.collect { restoredTheme ->
            onSelectTheme(restoredTheme)
        }
    }

    LaunchedEffect(uiState.showAccountSheet, uiState.backupIncludeVideos) {
        if (uiState.showAccountSheet) {
            viewModel.refreshBackupEstimate(context, uiState.backupIncludeVideos)
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

    val openExactAlarmSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val settingsIntent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(settingsIntent)
        }
    }

    val openBatteryAppInfoSettings: () -> Unit = {
        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(detailsIntent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || TripNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            if (pendingTestAfterPermission) {
                pendingTestAfterPermission = false
                scheduleTestAndNotifyUser()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmScheduler.canScheduleExactAlarms()) {
                val prefs = context.getSharedPreferences("tripbuddy_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("asked_exact_alarm_rationale", false)) {
                    prefs.edit().putBoolean("asked_exact_alarm_rationale", true).apply()
                    showExactAlarmRationaleDialog = true
                }
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
        val prefs = context.getSharedPreferences("tripbuddy_prefs", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TripNotificationHelper.hasNotificationPermission(context) &&
            !prefs.getBoolean("asked_post_notifications", false)
        ) {
            prefs.edit().putBoolean("asked_post_notifications", true).apply()
            showNotificationRationaleDialog = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmScheduler.canScheduleExactAlarms() &&
            !prefs.getBoolean("asked_exact_alarm_rationale", false)
        ) {
            prefs.edit().putBoolean("asked_exact_alarm_rationale", true).apply()
            showExactAlarmRationaleDialog = true
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
        val activeGroup = uiState.activeSharedTrip!!
        TripMembersScreen(
            trip = activeGroup,
            members = uiState.activeTripMembers,
            currentUserId = uiState.currentUserId,
            isSubmitting = uiState.isSubmittingCollab,
            isExportingPdf = uiState.isExportingPdf,
            onBack = viewModel::closeMembersScreen,
            onInviteClick = { viewModel.openInviteSheet(activeGroup) },
            onExportGroupPdf = {
                viewModel.openPdfExportDialog(
                    defaultScope = PdfExportScope.SPECIFIC_GROUP,
                    selectedGroupUuid = Trip.sharedTripUuid(activeGroup.id)
                )
            },
            onTransferAdmin = { newAdmin -> viewModel.transferGroupAdmin(activeGroup, newAdmin) },
            onLeaveGroup = { viewModel.leaveSharedTrip(activeGroup) },
            onDeleteGroup = { viewModel.deleteSharedTrip(activeGroup) }
        )
    } else if (uiState.activeVisitMemoryDetail != null) {
        val detail = uiState.activeVisitMemoryDetail!!
        VisitMemoryDetailScreen(
            detail = detail,
            isExportingPdf = uiState.isExportingPdf,
            onBack = viewModel::closeVisitMemoryDetail,
            onTakePhotoForVisit = requestCameraForVisit,
            onDeletePhoto = { media -> viewModel.deleteMemoryPhoto(media, context.filesDir) },
            onSaveMemoryNotes = { kunjungan, notes -> viewModel.saveMemoryNotes(kunjungan, notes) },
            onOpenMapsForVisit = { visit -> viewModel.openMapsSearchSheet(visit) },
            onExportPdf = {
                viewModel.openPdfExportDialog(
                    defaultScope = PdfExportScope.THIS_VISIT,
                    selectedVisitId = detail.kunjungan.id,
                    selectedGroupUuid = detail.trip.id.takeIf { !detail.trip.isPersonal }
                )
            }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp
            val isInGroupDirectory = uiState.spaceMode == SpaceMode.GRUP && uiState.showGroupDirectory

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = !isInGroupDirectory &&
                            (uiState.selectedTab == TripTab.BERANDA || uiState.selectedTab == TripTab.KENANGAN),
                        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                            scaleIn(
                                initialScale = 0.88f,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                            scaleOut(
                                targetScale = 0.88f,
                                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                            )
                    ) {
                        ExtendedFloatingActionButton(
                            onClick = viewModel::openCameraVisitPicker,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("general_camera_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = stringResource(R.string.cd_take_photo)
                            )
                            Text(
                                text = "Kamera",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                },
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
                            spaceMode = uiState.spaceMode,
                            showGroupDirectory = uiState.showGroupDirectory,
                            activeSpaceTitle = uiState.activeSpaceTitle,
                            activeSharedTrip = uiState.activeSharedTrip,
                            activeTripMembers = uiState.activeTripMembers,
                            userName = uiState.currentUserName,
                            userPhotoUrl = uiState.currentUserPhotoUrl,
                            onSelectSpaceMode = viewModel::selectSpaceMode,
                            onOpenGroupDirectory = viewModel::openGroupDirectory,
                            onOpenMembersClick = viewModel::openMembersScreen,
                            onInviteClick = { viewModel.openInviteSheet() },
                            onAccountClick = viewModel::openAccountSheet
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                        ) {
                            if (isInGroupDirectory) {
                                GroupDirectoryScreen(
                                    sharedTrips = uiState.sharedTrips,
                                    activeWorkspace = uiState.activeWorkspace,
                                    currentUserId = uiState.currentUserId,
                                    onCreateGroupClick = viewModel::openCreateTripDialog,
                                    onJoinByCodeClick = { viewModel.openJoinTripDialog() },
                                    onOpenGroupTrip = { trip -> viewModel.selectGroupTrip(trip, openMembersPage = false) },
                                    onOpenGroupPage = { trip -> viewModel.selectGroupTrip(trip, openMembersPage = true) },
                                    onInviteGroup = { trip -> viewModel.openInviteSheet(trip) }
                                )
                            } else {
                                AnimatedContent(
                                    targetState = uiState.selectedTab,
                                    transitionSpec = { tripTabContentTransform() },
                                    label = "TripTabTransition",
                                    modifier = Modifier.fillMaxSize()
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
                                            onTakePhotoForVisit = requestCameraForVisit,
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
                                            onTakePhotoForVisit = requestCameraForVisit,
                                            onEditVisit = { visit -> viewModel.openEditLocationSheet(visit) },
                                            onDeleteVisit = { visit -> viewModel.deleteVisit(visit) },
                                            onSendComment = viewModel::sendDateComment,
                                            onDeleteComment = viewModel::deleteDateComment
                                        )

                                        TripTab.KENANGAN -> MemoriesScreen(
                                            uiState = uiState,
                                            onBackToHome = { viewModel.selectTab(TripTab.BERANDA) },
                                            onSearchQueryChange = viewModel::setMemorySearchQuery,
                                            onMemoriesTabChange = viewModel::selectMemoriesTab,
                                            onSelectMemoryGroup = viewModel::selectMemoryGroup,
                                            onVisitMemoryClick = viewModel::openVisitMemoryDetail,
                                            onExportGroupPdf = { summary ->
                                                viewModel.openPdfExportDialog(
                                                    defaultScope = PdfExportScope.SPECIFIC_GROUP,
                                                    selectedGroupUuid = summary.trip.id
                                                )
                                            },
                                            onExportPdf = {
                                                val defaultScope = when (uiState.selectedMemoriesTab) {
                                                    com.example.ui.MemoriesFilterTab.PRIBADI -> PdfExportScope.PERSONAL_ONLY
                                                    else -> PdfExportScope.ALL
                                                }
                                                viewModel.openPdfExportDialog(defaultScope = defaultScope)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showCameraVisitPicker) {
        CameraVisitPickerDialog(
            todayVisits = uiState.todayVisitsAllTrips,
            defaultVisit = uiState.defaultCameraVisit,
            onDismiss = viewModel::dismissCameraVisitPicker,
            onSelectVisit = { chosen -> requestCameraForVisit(chosen) },
            onAddVisitToday = {
                viewModel.selectTab(TripTab.KALENDER)
                viewModel.openAddLocationSheet(uiState.todayEpochDay)
            }
        )
    }

    pendingCameraModeVisit?.let { chosenVisit ->
        CameraCaptureModeDialog(
            visit = chosenVisit,
            onDismiss = { pendingCameraModeVisit = null },
            onSelectPhoto = { startPhotoCaptureForVisit(chosenVisit) },
            onSelectVideo = { startVideoCaptureForVisit(chosenVisit) }
        )
    }

    uiState.pdfExportOptions?.let { options ->
        val previewSpec = uiState.pdfExportPreviewSpec
        if (previewSpec != null) {
            PdfExportOptionsSheet(
                options = options,
                allowThisVisitScope = uiState.pdfAllowThisVisitScope,
                groupTrips = uiState.allTrips.filter { !it.isPersonal },
                previewSpec = previewSpec,
                isExporting = uiState.isExportingPdf,
                onUpdateOptions = viewModel::updatePdfExportOptions,
                onConfirmExport = { viewModel.confirmPdfExport(context) },
                onDismiss = viewModel::dismissPdfExportDialog
            )
        }
    }

    uiState.pdfExportProgress?.let { progress ->
        PdfExportProgressDialog(progress = progress)
    }

    uiState.exportedPdfResult?.let { exported ->
        PdfExportReadyDialog(
            result = exported,
            onSharePdf = {
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, exported.contentUri)
                        putExtra(Intent.EXTRA_SUBJECT, exported.fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan PDF Kenangan"))
                } catch (e: Exception) {
                    showMessage("Tidak dapat membuka dialog bagikan: ${e.localizedMessage ?: "periksa aplikasi tujuan"}")
                }
            },
            onSavePdfAs = {
                createPdfDocumentLauncher.launch(exported.fileName)
            },
            onDismiss = viewModel::dismissExportedPdfDialog
        )
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
            selectedTheme = selectedTheme,
            onSelectTheme = onSelectTheme,
            reminderSettings = uiState.reminderSettings,
            hasNotificationPermission = hasNotificationPermission,
            canScheduleExactAlarms = canScheduleExactAlarms,
            onSetRemindersEnabled = viewModel::setRemindersEnabled,
            onToggleReminderTiming = viewModel::toggleReminderTiming,
            onSetAnnualMemoryReminderEnabled = viewModel::setAnnualMemoryReminderEnabled,
            onRequestNotificationPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showNotificationRationaleDialog = true
                }
            },
            onRequestExactAlarmPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    showExactAlarmRationaleDialog = true
                }
            },
            onOpenBatteryAppInfo = openBatteryAppInfoSettings,
            backupIncludeVideos = uiState.backupIncludeVideos,
            backupEstimate = uiState.backupEstimate,
            isEstimatingBackup = uiState.isEstimatingBackup,
            isBackupBusy = uiState.backupProgress != null,
            onToggleBackupIncludeVideos = { include ->
                viewModel.setBackupIncludeVideos(context, include)
            },
            onExportBackupClick = {
                viewModel.exportPersonalBackup(context)
            },
            onImportBackupClick = {
                importBackupZipLauncher.launch(arrayOf("*/*"))
            },
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

    uiState.backupProgress?.let { progress ->
        BackupProgressDialog(progress = progress)
    }

    uiState.pendingBackupImportSummary?.let { summary ->
        BackupImportConfirmDialog(
            summary = summary,
            onConfirm = { viewModel.confirmBackupRestore(context) },
            onDismiss = viewModel::dismissBackupImportConfirm
        )
    }

    if (!uiState.backupStatusDialogTitle.isNullOrBlank() && !uiState.backupStatusDialogMessage.isNullOrBlank()) {
        BackupStatusDialog(
            title = uiState.backupStatusDialogTitle!!,
            message = uiState.backupStatusDialogMessage!!,
            isError = uiState.backupStatusIsError,
            onDismiss = viewModel::dismissBackupStatusDialog
        )
    }

    if (showNotificationRationaleDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AlertDialog(
            onDismissRequest = { showNotificationRationaleDialog = false },
            title = { Text("Izinkan Notifikasi Pengingat") },
            text = {
                Text(
                    "TripBuddy membutuhkan izin notifikasi agar dapat mengingatkan Anda H-1 jam 09.00, menjelang jam kunjungan, serta pengingat kenangan setiap tahun."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotificationRationaleDialog = false
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.testTag("confirm_notification_rationale_button")
                ) {
                    Text("Izinkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationaleDialog = false }) {
                    Text("Nanti")
                }
            }
        )
    }

    if (showExactAlarmRationaleDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        AlertDialog(
            onDismissRequest = { showExactAlarmRationaleDialog = false },
            title = { Text("Izinkan Alarm Tepat Waktu") },
            text = {
                Text(
                    "Izin Alarm & Pengingat memastikan notifikasi H-1 pukul 09.00 dan pengingat sebelum kunjungan muncul tepat waktu meskipun HP sedang idle."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExactAlarmRationaleDialog = false
                        openExactAlarmSettings()
                    },
                    modifier = Modifier.testTag("confirm_exact_alarm_rationale_button")
                ) {
                    Text("Buka Pengaturan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmRationaleDialog = false }) {
                    Text("Nanti")
                }
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
}

private fun AnimatedContentTransitionScope<TripTab>.tripTabContentTransform(): ContentTransform {
    val direction = targetState.ordinal.compareTo(initialState.ordinal)
    return if (direction == 0) {
        (fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 150)))
            .using(SizeTransform(clip = false))
    } else {
        val enterOffsetFraction = if (direction > 0) 0.16f else -0.16f
        val exitOffsetFraction = if (direction > 0) -0.10f else 0.10f
        val enter = slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialOffsetX = { fullWidth -> (fullWidth * enterOffsetFraction).toInt() }
        ) + fadeIn(
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
        )
        val exit = slideOutHorizontally(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> (fullWidth * exitOffsetFraction).toInt() }
        ) + fadeOut(
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
        (enter togetherWith exit).using(SizeTransform(clip = false))
    }
}
