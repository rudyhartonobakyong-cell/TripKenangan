package com.example

import androidx.compose.ui.graphics.Color
import com.example.collab.CollabRepository
import com.example.collab.SharedTrip
import com.example.collab.SharedVisit
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.drive.DriveBackupFrequency
import com.example.drive.DriveBackupManifest
import com.example.drive.DriveBackupModels
import com.example.drive.DriveBackupScheduleSettings
import com.example.drive.DriveBackupScheduler
import com.example.drive.DriveMediaManifestEntry
import com.example.drive.DriveRemoteBackupSummary
import com.example.drive.LocalMediaFileCandidate
import com.example.notifications.ReminderKind
import com.example.notifications.ReminderTimingOption
import com.example.notifications.TripAlarmScheduler
import com.example.ui.DisplayVisit
import com.example.ui.MemoriesFilterTab
import com.example.ui.SpaceMode
import com.example.ui.TripBuddyViewModel
import com.example.ui.VisitMemoryItem
import com.example.ui.Workspace
import com.example.ui.theme.AppThemeOption
import com.example.util.BackupCorruptException
import com.example.util.BackupInsufficientSpaceException
import com.example.util.BackupJsonCodec
import com.example.util.BackupManager
import com.example.util.BackupSettingsPayload
import com.example.util.DateUtils
import com.example.util.PdfExportOptions
import com.example.util.PdfExportScope
import com.example.util.PdfExporter
import com.example.util.PdfGridLayout
import com.example.util.PdfPageSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ExampleUnitTest {

  @Test
  fun `epochDay round trip and Indonesian date formatting are accurate`() {
    val epochDay = DateUtils.epochDayFromYmd(2026, 10, 25) // 25 November 2026
    val (year, month, day) = DateUtils.ymdFromEpochDay(epochDay)
    assertEquals(2026, year)
    assertEquals(10, month)
    assertEquals(25, day)
    assertEquals("November 2026", DateUtils.formatMonthYear(2026, 10))
    assertEquals("25 Nov 2026", DateUtils.formatIndonesianShortDate(epochDay))
    assertEquals("Rabu, 25 November 2026", DateUtils.formatIndonesianFullDate(epochDay))
  }

  @Test
  fun `countdown calculation returns expected days hours minutes seconds`() {
    val todayDay = DateUtils.epochDayFromYmd(2026, 10, 25)
    val nowMillis = DateUtils.targetMillisForVisit(todayDay, 10, 0)
    val targetDay = todayDay + 2L

    val countdown = DateUtils.computeCountdownParts(
      visitEpochDay = targetDay,
      visitHour = 13,
      visitMinute = 30,
      nowMillis = nowMillis
    )

    assertEquals(2L, countdown.days)
    assertEquals(3L, countdown.hours)
    assertEquals(30L, countdown.minutes)
    assertEquals(0L, countdown.seconds)
    assertFalse(countdown.isToday)
    assertFalse(countdown.isTomorrow)
  }

  @Test
  fun `leap year February has 29 days`() {
    assertEquals(29, DateUtils.daysInMonth(2024, 1))
    assertEquals(28, DateUtils.daysInMonth(2026, 1))
    assertTrue(DateUtils.firstDayOfWeekInMonth(2026, 10) in 0..6)
  }

  @Test
  fun `extractInviteCode parses raw codes and deep links correctly`() {
    assertEquals("BALI26", CollabRepository.extractInviteCode("bali26"))
    assertEquals("AB12CD", CollabRepository.extractInviteCode("tripbuddy://join/ab12cd"))
    assertEquals("XY89ZW", CollabRepository.extractInviteCode("https://tripbuddy.app/?code=xy89zw"))
    assertNull(CollabRepository.extractInviteCode("ABC12"))
    assertNull(CollabRepository.extractInviteCode(""))
  }

  @Test
  fun `DisplayVisit maps Kunjungan and SharedVisit to deterministic UUIDs`() {
    val localKunjungan = Kunjungan(
      id = "11111111-1111-4111-8111-111111111111",
      tripId = Trip.PERSONAL_TRIP_ID,
      locationName = "Ubud",
      tripTitle = "Bali",
      visitDateEpochDay = 20500L,
      visitHour = 10,
      visitMinute = 15,
      isCompletedManually = true
    )
    val localDisplay = DisplayVisit.fromKunjungan(localKunjungan, "Pribadi")
    assertFalse(localDisplay.isShared)
    assertEquals(localKunjungan.id, localDisplay.id)
    assertEquals(Trip.PERSONAL_TRIP_ID, localDisplay.tripId)
    assertEquals("Pribadi", localDisplay.tripName)
    assertTrue(localDisplay.completedManually)

    val shared = SharedVisit(
      id = "doc123",
      tripId = "trip456",
      locationName = "Tanah Lot",
      tripTitle = "Liburan",
      visitDateEpochDay = 20501L,
      visitHour = 16L,
      visitMinute = 45L,
      completedManually = false,
      createdByName = "Budi"
    )
    val sharedDisplay = DisplayVisit.fromShared(shared, "Trip Bali")
    assertTrue(sharedDisplay.isShared)
    assertEquals(Kunjungan.sharedVisitUuid("trip456", "doc123"), sharedDisplay.id)
    assertEquals(Trip.sharedTripUuid("trip456"), sharedDisplay.tripId)
    assertEquals("doc123", sharedDisplay.sharedDocId)
    assertEquals("trip456", sharedDisplay.sharedTripId)
    assertEquals("Trip Bali", sharedDisplay.tripName)
    assertEquals("Budi", sharedDisplay.createdByName)
    assertFalse(sharedDisplay.completedManually)
  }

  @Test
  fun `Trip Kunjungan Foto entities maintain distinct UUIDs and foreign links`() {
    val trip = Trip(name = "Trip Yogyakarta")
    val k1 = Kunjungan(
      tripId = trip.id,
      locationName = "Candi Prambanan",
      visitDateEpochDay = 20700L,
      visitHour = 9,
      visitMinute = 0
    )
    val k2 = Kunjungan(
      tripId = trip.id,
      locationName = "Malioboro",
      visitDateEpochDay = 20700L,
      visitHour = 18,
      visitMinute = 0
    )
    val foto1 = Foto(
      kunjunganId = k1.id,
      tripId = trip.id,
      filePath = "/data/user/0/com.example/files/fotos/${trip.id}/${k1.id}/photo1.jpg"
    )
    assertNotEquals(k1.id, k2.id)
    assertEquals(trip.id, k1.tripId)
    assertEquals(k1.id, foto1.kunjunganId)
    assertEquals(trip.id, foto1.tripId)
  }

  @Test
  fun `findClosestVisitToNow selects today visit nearest to current time`() {
    val today = DateUtils.epochDayFromYmd(2026, 10, 27)
    val now = DateUtils.targetMillisForVisit(today, 14, 20)
    val morning = DisplayVisit(
      id = "v1",
      tripId = Trip.PERSONAL_TRIP_ID,
      locationName = "Sarapan Gudeg",
      tripTitle = "",
      visitDateEpochDay = today,
      visitHour = 8,
      visitMinute = 0,
      planNotes = "",
      memoryNotes = "",
      completedManually = false,
      createdByName = "",
      createdAtMillis = 0L
    )
    val afternoon = morning.copy(id = "v2", locationName = "Pantai Parangtritis", visitHour = 15, visitMinute = 0)
    val night = morning.copy(id = "v3", locationName = "Alun-Alun Kidul", visitHour = 20, visitMinute = 0)

    val closest = TripBuddyViewModel.findClosestVisitToNow(listOf(morning, afternoon, night), now)
    assertEquals("v2", closest?.id)
    assertNull(TripBuddyViewModel.findClosestVisitToNow(emptyList(), now))
  }

  @Test
  fun `AppThemeOption exposes all 6 themes with expected colors and default fallback`() {
    assertEquals(6, AppThemeOption.entries.size)
    assertEquals(AppThemeOption.SAMUDRA, AppThemeOption.fromId(null))
    assertEquals(AppThemeOption.SAMUDRA, AppThemeOption.fromId("unknown_theme"))
    assertEquals(AppThemeOption.PINK_MANIS, AppThemeOption.fromId("pink_manis"))
    assertEquals(AppThemeOption.HITAM_ELEGAN, AppThemeOption.fromId("hitam_elegan"))
    assertEquals(AppThemeOption.HIJAU_HUTAN, AppThemeOption.fromId("hijau_hutan"))
    assertEquals(AppThemeOption.UNGU_SENJA, AppThemeOption.fromId("ungu_senja"))
    assertEquals(AppThemeOption.ORANYE_PANTAI, AppThemeOption.fromId("oranye_pantai"))

    assertEquals(Color(0xFF0A1018), AppThemeOption.SAMUDRA.previewBackground)
    assertEquals(Color(0xFF4FC3F7), AppThemeOption.SAMUDRA.previewAccent)
    assertTrue(AppThemeOption.SAMUDRA.isDark)

    assertEquals(Color(0xFFFFF0F5), AppThemeOption.PINK_MANIS.previewBackground)
    assertEquals(Color(0xFFEC4899), AppThemeOption.PINK_MANIS.previewAccent)
    assertFalse(AppThemeOption.PINK_MANIS.isDark)

    assertEquals(Color(0xFF000000), AppThemeOption.HITAM_ELEGAN.previewBackground)
    assertEquals(Color(0xFFD4AF37), AppThemeOption.HITAM_ELEGAN.previewAccent)
    assertTrue(AppThemeOption.HITAM_ELEGAN.isDark)

    assertEquals(Color(0xFF0F1F17), AppThemeOption.HIJAU_HUTAN.previewBackground)
    assertEquals(Color(0xFF4ADE80), AppThemeOption.HIJAU_HUTAN.previewAccent)
    assertTrue(AppThemeOption.HIJAU_HUTAN.isDark)

    assertEquals(Color(0xFF1A1033), AppThemeOption.UNGU_SENJA.previewBackground)
    assertEquals(Color(0xFFA78BFA), AppThemeOption.UNGU_SENJA.previewAccent)
    assertTrue(AppThemeOption.UNGU_SENJA.isDark)

    assertEquals(Color(0xFFFFF7ED), AppThemeOption.ORANYE_PANTAI.previewBackground)
    assertEquals(Color(0xFFF97316), AppThemeOption.ORANYE_PANTAI.previewAccent)
    assertFalse(AppThemeOption.ORANYE_PANTAI.isDark)
  }

  @Test
  fun `Workspace storageKey maps local and shared trips cleanly`() {
    assertEquals(Trip.PERSONAL_TRIP_ID, Workspace.Local.storageKey())
    assertEquals(Trip.sharedTripUuid("trip123"), Workspace.Shared("trip123").storageKey())
  }

  @Test
  fun `formatActiveSpaceTitle formats Pribadi and Grup titles clearly`() {
    assertEquals(
      "Pribadi - hanya di HP ini",
      TripBuddyViewModel.formatActiveSpaceTitle(
        spaceMode = SpaceMode.PRIBADI,
        showGroupDirectory = false,
        activeSharedTrip = null,
        memberCount = 0,
        totalGroups = 2
      )
    )

    val baliTrip = SharedTrip(
      id = "bali",
      title = "Bali 2026",
      ownerId = "owner_1",
      inviteCode = "BALI26",
      memberUids = listOf("owner_1", "u2", "u3", "u4")
    )
    assertEquals(
      "Grup: Bali 2026 - 4 anggota",
      TripBuddyViewModel.formatActiveSpaceTitle(
        spaceMode = SpaceMode.GRUP,
        showGroupDirectory = false,
        activeSharedTrip = baliTrip,
        memberCount = 4,
        totalGroups = 1
      )
    )

    assertEquals(
      "Grup - 3 grup tersedia",
      TripBuddyViewModel.formatActiveSpaceTitle(
        spaceMode = SpaceMode.GRUP,
        showGroupDirectory = true,
        activeSharedTrip = baliTrip,
        memberCount = 4,
        totalGroups = 3
      )
    )
  }

  @Test
  fun `group delete, leave permissions, and name confirmation rules are enforced`() {
    val group = SharedTrip(
      id = "g1",
      title = "Bali 2026",
      ownerId = "admin_uid",
      inviteCode = "BALI26",
      memberUids = listOf("admin_uid", "member_uid")
    )

    assertTrue(TripBuddyViewModel.canDeleteGroup(group, "admin_uid"))
    assertFalse(TripBuddyViewModel.canDeleteGroup(group, "member_uid"))
    assertFalse(TripBuddyViewModel.canDeleteGroup(group, "outsider_uid"))

    assertFalse(TripBuddyViewModel.canLeaveGroupDirectly(group, "admin_uid"))
    assertTrue(TripBuddyViewModel.canLeaveGroupDirectly(group, "member_uid"))
    assertFalse(TripBuddyViewModel.canLeaveGroupDirectly(group, "outsider_uid"))

    assertTrue(TripBuddyViewModel.isValidDeleteGroupConfirmation("Bali 2026", "Bali 2026"))
    assertTrue(TripBuddyViewModel.isValidDeleteGroupConfirmation("  Bali 2026  ", "Bali 2026"))
    assertFalse(TripBuddyViewModel.isValidDeleteGroupConfirmation("bali 2026", "Bali 2026"))
    assertFalse(TripBuddyViewModel.isValidDeleteGroupConfirmation("", "Bali 2026"))
  }

  @Test
  fun `Kenangan 3 tabs filter personal, mixed, and per-group memories correctly`() {
    val personalTrip = Trip.personalTrip()
    val baliTrip = Trip(id = Trip.sharedTripUuid("bali"), name = "Bali 2026", isPersonal = false, remoteTripId = "bali")
    val jogjaTrip = Trip(id = Trip.sharedTripUuid("jogja"), name = "Jogja Trip", isPersonal = false, remoteTripId = "jogja")

    val day1 = DateUtils.epochDayFromYmd(2026, 9, 10)
    val day2 = DateUtils.epochDayFromYmd(2026, 9, 12)
    val day3 = DateUtils.epochDayFromYmd(2026, 10, 5)

    val kPersonal = Kunjungan(id = "kp", tripId = personalTrip.id, locationName = "Kafe Senja", visitDateEpochDay = day3, visitHour = 16, visitMinute = 0)
    val kBali1 = Kunjungan(id = "kb1", tripId = baliTrip.id, locationName = "Pantai Kuta", visitDateEpochDay = day1, visitHour = 9, visitMinute = 0)
    val kBali2 = Kunjungan(id = "kb2", tripId = baliTrip.id, locationName = "Ubud", visitDateEpochDay = day2, visitHour = 14, visitMinute = 0)
    val kJogja = Kunjungan(id = "kj1", tripId = jogjaTrip.id, locationName = "Prambanan", visitDateEpochDay = day1, visitHour = 10, visitMinute = 0)

    val fBali1 = Foto(id = "f1", kunjunganId = kBali1.id, tripId = baliTrip.id, filePath = "/fotos/bali1.jpg", createdAtMillis = 100L)
    val fBali2 = Foto(id = "f2", kunjunganId = kBali2.id, tripId = baliTrip.id, filePath = "/fotos/bali2.jpg", createdAtMillis = 200L)

    // Newest first order as returned by Room
    val allMemories = listOf(
      VisitMemoryItem(kPersonal, personalTrip, emptyList()),
      VisitMemoryItem(kBali2, baliTrip, listOf(fBali2)),
      VisitMemoryItem(kJogja, jogjaTrip, emptyList()),
      VisitMemoryItem(kBali1, baliTrip, listOf(fBali1))
    )

    val semua = TripBuddyViewModel.filterMemoriesForTab(allMemories, MemoriesFilterTab.SEMUA, null, "")
    assertEquals(4, semua.size)

    val pribadi = TripBuddyViewModel.filterMemoriesForTab(allMemories, MemoriesFilterTab.PRIBADI, null, "")
    assertEquals(listOf("kp"), pribadi.map { it.kunjungan.id })

    val grupListMode = TripBuddyViewModel.filterMemoriesForTab(allMemories, MemoriesFilterTab.GRUP, null, "")
    assertTrue(grupListMode.isEmpty())

    val insideBali = TripBuddyViewModel.filterMemoriesForTab(allMemories, MemoriesFilterTab.GRUP, baliTrip.id, "")
    assertEquals(listOf("kb2", "kb1"), insideBali.map { it.kunjungan.id })

    val summaries = TripBuddyViewModel.buildGroupMemorySummaries(
      roomTrips = listOf(personalTrip, baliTrip, jogjaTrip),
      sharedTrips = emptyList(),
      allKunjungan = listOf(kPersonal, kBali1, kBali2, kJogja),
      memoryItems = allMemories
    )
    assertEquals(2, summaries.size)
    // Bali has newer visit (day2) than Jogja (day1), so Bali comes first
    assertEquals(baliTrip.id, summaries[0].trip.id)
    assertEquals(2, summaries[0].memoryCount)
    assertEquals(2, summaries[0].photoCount)
    assertEquals("/fotos/bali1.jpg", summaries[0].coverPhotoPath)
    assertEquals("10 Okt 2026 – 12 Okt 2026", summaries[0].dateRangeLabel)

    assertEquals(jogjaTrip.id, summaries[1].trip.id)
    assertEquals(1, summaries[1].memoryCount)
    assertEquals(0, summaries[1].photoCount)
    assertNull(summaries[1].coverPhotoPath)
    assertEquals("10 Okt 2026", summaries[1].dateRangeLabel)
  }

  @Test
  fun `Foto distinguishes PHOTO and VIDEO media types correctly`() {
    val photo = Foto(
      id = "p1",
      kunjunganId = "k1",
      tripId = "t1",
      filePath = "/fotos/t1/k1/p1.jpg"
    )
    val video = Foto(
      id = "v1",
      kunjunganId = "k1",
      tripId = "t1",
      filePath = "/fotos/t1/k1/v1.mp4",
      mediaType = Foto.MEDIA_TYPE_VIDEO
    )

    assertEquals(Foto.MEDIA_TYPE_PHOTO, photo.mediaType)
    assertFalse(photo.isVideo)
    assertEquals(Foto.MEDIA_TYPE_VIDEO, video.mediaType)
    assertTrue(video.isVideo)

    val item = VisitMemoryItem(
      kunjungan = Kunjungan(
        id = "k1",
        tripId = "t1",
        locationName = "Pantai Pandawa",
        visitDateEpochDay = DateUtils.epochDayFromYmd(2026, 10, 10),
        visitHour = 17,
        visitMinute = 15
      ),
      trip = Trip.personalTrip(),
      fotos = listOf(photo, video)
    )
    assertEquals(1, item.photoCount)
    assertEquals(1, item.videoCount)
  }

  @Test
  fun `DateUtils formatPhotoTimestamp formats Indonesian short month and 24h time`() {
    val nov28 = DateUtils.epochDayFromYmd(2026, 10, 28) // Month 10 = November
    val millis = DateUtils.targetMillisForVisit(nov28, 14, 32)
    assertEquals("28 Nov 2026 14:32", DateUtils.formatPhotoTimestamp(millis))
  }

  @Test
  fun `PdfExporter computeGridMetrics produces 12mm margin, 6mm gap, and exact 1-to-1 square cells`() {
    PdfPageSize.entries.forEach { pageSize ->
      PdfGridLayout.entries.forEach { grid ->
        val metrics = PdfExporter.computeGridMetrics(pageSize, grid)
        assertEquals(PdfExporter.MARGIN_MM * PdfExporter.MM_TO_PT, metrics.marginPt, 0.01f)
        assertEquals(PdfExporter.PHOTO_GAP_MM * PdfExporter.MM_TO_PT, metrics.gapPt, 0.01f)
        assertTrue(metrics.cellSizePt > 10f)

        val cell0 = metrics.cellBounds(0, grid.columns)
        val cell1 = metrics.cellBounds(1, grid.columns)
        assertEquals(cell0.width, cell0.height, 0.01f)
        assertEquals(metrics.gapPt, cell1.left - cell0.right, 0.01f)
      }
    }

    val a4 = PdfExporter.computeGridMetrics(PdfPageSize.A4_PORTRAIT, PdfGridLayout.GRID_2X3)
    val square20 = PdfExporter.computeGridMetrics(PdfPageSize.SQUARE_20X20, PdfGridLayout.GRID_2X3)
    assertTrue(a4.pageHeightPt > a4.pageWidthPt)
    assertEquals(square20.pageWidthPt, square20.pageHeightPt)
  }

  @Test
  fun `PdfExporter buildPhotoAlbumSpec excludes videos, separates visits onto new pages, and supports scopes and date range`() {
    val defaultOpts = PdfExportOptions()
    assertFalse(defaultOpts.includeCover)
    assertTrue(defaultOpts.printTimestamp)
    assertEquals(PdfGridLayout.GRID_2X3, defaultOpts.gridLayout)
    assertEquals(PdfPageSize.A4_PORTRAIT, defaultOpts.pageSize)

    val day1 = DateUtils.epochDayFromYmd(2026, 10, 28) // 28 Nov 2026
    val day2 = DateUtils.epochDayFromYmd(2026, 10, 29) // 29 Nov 2026

    val personalTrip = Trip.personalTrip()
    val baliTrip = Trip(id = Trip.sharedTripUuid("bali"), name = "Bali 2026", isPersonal = false, remoteTripId = "bali")

    val kSameDay1 = Kunjungan(
      id = "k1",
      tripId = baliTrip.id,
      locationName = "Tanjung Benoa",
      visitDateEpochDay = day1,
      visitHour = 8,
      visitMinute = 0
    )
    val kSameDay2 = Kunjungan(
      id = "k2",
      tripId = baliTrip.id,
      locationName = "Tanah Lot",
      visitDateEpochDay = day1,
      visitHour = 17,
      visitMinute = 0
    )
    val kVideoOnly = Kunjungan(
      id = "k3",
      tripId = baliTrip.id,
      locationName = "Video Only Spot",
      visitDateEpochDay = day2,
      visitHour = 10,
      visitMinute = 0
    )
    val kPersonal = Kunjungan(
      id = "kp",
      tripId = personalTrip.id,
      locationName = "Kafe Pribadi",
      visitDateEpochDay = day2,
      visitHour = 16,
      visitMinute = 0
    )

    // 7 photos in Visit 1 (2x3 grid -> 6 + 1 = 2 pages) + 2 photos in Visit 2 (1 page)
    val k1Photos = (1..7).map { idx ->
      Foto(
        id = "p1_$idx",
        kunjunganId = kSameDay1.id,
        tripId = baliTrip.id,
        filePath = "/p1_$idx.jpg",
        createdAtMillis = (1000L * (10 - idx)) // deliberately out of order
      )
    }
    val k2Photos = listOf(
      Foto(id = "p2_1", kunjunganId = kSameDay2.id, tripId = baliTrip.id, filePath = "/p2_1.jpg", createdAtMillis = 200L),
      Foto(id = "p2_2", kunjunganId = kSameDay2.id, tripId = baliTrip.id, filePath = "/p2_2.jpg", createdAtMillis = 100L)
    )
    val k2Video = Foto(
      id = "v2_1",
      kunjunganId = kSameDay2.id,
      tripId = baliTrip.id,
      filePath = "/v2_1.mp4",
      mediaType = Foto.MEDIA_TYPE_VIDEO,
      createdAtMillis = 300L
    )
    val k3VideoOnly = Foto(
      id = "v3_1",
      kunjunganId = kVideoOnly.id,
      tripId = baliTrip.id,
      filePath = "/v3_1.mp4",
      mediaType = Foto.MEDIA_TYPE_VIDEO,
      createdAtMillis = 400L
    )
    val kpPhoto = Foto(
      id = "pp_1",
      kunjunganId = kPersonal.id,
      tripId = personalTrip.id,
      filePath = "/pp_1.jpg",
      createdAtMillis = 500L
    )

    val allTrips = listOf(personalTrip, baliTrip)
    val allVisits = listOf(kPersonal, kSameDay2, kVideoOnly, kSameDay1)
    val allFotos = k1Photos + k2Photos + listOf(k2Video, k3VideoOnly, kpPhoto)

    val groupSpec = PdfExporter.buildPhotoAlbumSpec(
      options = PdfExportOptions(
        scope = PdfExportScope.SPECIFIC_GROUP,
        selectedGroupUuid = baliTrip.id
      ),
      allTrips = allTrips,
      allKunjungan = allVisits,
      allFotos = allFotos,
      resolveTakenTimeMillis = { it.createdAtMillis }
    )

    // Videos and video-only visits must be completely excluded
    assertEquals(2, groupSpec.visitSections.size)
    assertEquals("k1", groupSpec.visitSections[0].kunjungan.id)
    assertEquals("k2", groupSpec.visitSections[1].kunjungan.id)
    assertEquals(9, groupSpec.totalPhotos)
    // Visit 1: 2 pages (6, 1) + Visit 2: 1 page (2), no cover page by default
    assertEquals(3, groupSpec.totalPages)
    assertEquals(listOf(6, 1), groupSpec.visitSections[0].photoPages.map { it.size })
    assertEquals(listOf(2), groupSpec.visitSections[1].photoPages.map { it.size })
    // Chronological ordering of photos within visit
    assertEquals("p1_7", groupSpec.visitSections[0].photos.first().foto.id)
    assertEquals("p2_2", groupSpec.visitSections[1].photos.first().foto.id)
    assertTrue(groupSpec.suggestedFileName.startsWith("TripBuddy_"))
    assertTrue(groupSpec.suggestedFileName.endsWith(".pdf"))

    val withCoverSpec = PdfExporter.buildPhotoAlbumSpec(
      options = PdfExportOptions(
        scope = PdfExportScope.SPECIFIC_GROUP,
        selectedGroupUuid = baliTrip.id,
        includeCover = true
      ),
      allTrips = allTrips,
      allKunjungan = allVisits,
      allFotos = allFotos,
      resolveTakenTimeMillis = { it.createdAtMillis }
    )
    assertEquals(4, withCoverSpec.totalPages)

    val personalSpec = PdfExporter.buildPhotoAlbumSpec(
      options = PdfExportOptions(scope = PdfExportScope.PERSONAL_ONLY),
      allTrips = allTrips,
      allKunjungan = allVisits,
      allFotos = allFotos,
      resolveTakenTimeMillis = { it.createdAtMillis }
    )
    assertEquals(1, personalSpec.totalPhotos)
    assertEquals("kp", personalSpec.visitSections.single().kunjungan.id)

    val dateFilteredAllSpec = PdfExporter.buildPhotoAlbumSpec(
      options = PdfExportOptions(
        scope = PdfExportScope.ALL,
        useDateRange = true,
        startEpochDay = day2,
        endEpochDay = day2
      ),
      allTrips = allTrips,
      allKunjungan = allVisits,
      allFotos = allFotos,
      resolveTakenTimeMillis = { it.createdAtMillis }
    )
    assertEquals(1, dateFilteredAllSpec.totalPhotos)
    assertEquals("kp", dateFilteredAllSpec.visitSections.single().kunjungan.id)

    val singleVisitSpec = PdfExporter.buildPhotoAlbumSpec(
      options = PdfExportOptions(
        scope = PdfExportScope.THIS_VISIT,
        selectedVisitId = kSameDay2.id
      ),
      allTrips = allTrips,
      allKunjungan = allVisits,
      allFotos = allFotos,
      resolveTakenTimeMillis = { it.createdAtMillis }
    )
    assertEquals(2, singleVisitSpec.totalPhotos)
    assertEquals(1, singleVisitSpec.totalPages)
  }

  @Test
  fun `TripAlarmScheduler computes H-1, 3h, 1h, and annual memory triggers accurately`() {
    val visitDay = DateUtils.epochDayFromYmd(2026, 10, 20) // 20 Nov 2026
    val beforeVisitNow = DateUtils.targetMillisForVisit(visitDay - 2L, 12, 0)

    val hMinus1 = TripAlarmScheduler.computeReminderTriggerMillis(
      visitDateEpochDay = visitDay,
      visitHour = 14,
      visitMinute = 30,
      kind = ReminderKind.H_MINUS_1,
      nowMillis = beforeVisitNow
    )
    assertEquals(DateUtils.targetMillisForVisit(visitDay - 1L, 9, 0), hMinus1)

    val threeHours = TripAlarmScheduler.computeReminderTriggerMillis(
      visitDateEpochDay = visitDay,
      visitHour = 14,
      visitMinute = 30,
      kind = ReminderKind.THREE_HOURS_BEFORE,
      nowMillis = beforeVisitNow
    )
    assertEquals(DateUtils.targetMillisForVisit(visitDay, 11, 30), threeHours)

    val oneHour = TripAlarmScheduler.computeReminderTriggerMillis(
      visitDateEpochDay = visitDay,
      visitHour = 14,
      visitMinute = 30,
      kind = ReminderKind.ONE_HOUR_BEFORE,
      nowMillis = beforeVisitNow
    )
    assertEquals(DateUtils.targetMillisForVisit(visitDay, 13, 30), oneHour)

    val passedNow = DateUtils.targetMillisForVisit(visitDay, 15, 0)
    assertNull(
      TripAlarmScheduler.computeReminderTriggerMillis(
        visitDateEpochDay = visitDay,
        visitHour = 14,
        visitMinute = 30,
        kind = ReminderKind.ONE_HOUR_BEFORE,
        nowMillis = passedNow
      )
    )

    // Leap day Feb 29, 2024 clamps cleanly to Feb 28 in non-leap year 2026
    val leapVisit = DateUtils.epochDayFromYmd(2024, 1, 29)
    val jan2026Now = DateUtils.targetMillisForVisit(DateUtils.epochDayFromYmd(2026, 0, 15), 10, 0)
    val nextAnnual = TripAlarmScheduler.computeNextAnnualMemoryTriggerMillis(leapVisit, jan2026Now)
    assertEquals(
      DateUtils.targetMillisForVisit(DateUtils.epochDayFromYmd(2026, 1, 28), 9, 0),
      nextAnnual
    )
  }

  @Test
  fun `TripAlarmScheduler formats group, personal, and annual notification strings cleanly`() {
    val (h1Title, h1Body) = TripAlarmScheduler.formatNotificationTitleAndBody(
      kind = ReminderKind.H_MINUS_1,
      locationName = "Tanah Lot",
      timeLabel = "09:30",
      groupOrSpaceLabel = "Bali 2026"
    )
    assertEquals("Pengingat Kunjungan Besok", h1Title)
    assertEquals("Besok: Tanah Lot jam 09:30 - Bali 2026", h1Body)

    val (oneHourTitle, oneHourBody) = TripAlarmScheduler.formatNotificationTitleAndBody(
      kind = ReminderKind.ONE_HOUR_BEFORE,
      locationName = "Kafe Senja",
      timeLabel = "16:00",
      groupOrSpaceLabel = ""
    )
    assertEquals("Pengingat 1 Jam Lagi", oneHourTitle)
    assertEquals("1 jam lagi: Kafe Senja - Pribadi", oneHourBody)

    val (annualTitle, annualBody) = TripAlarmScheduler.formatNotificationTitleAndBody(
      kind = ReminderKind.ANNUAL_MEMORY,
      locationName = "Gunung Bromo",
      timeLabel = "05:00",
      groupOrSpaceLabel = "Jawa Timur"
    )
    assertEquals("Kenangan TripBuddy", annualTitle)
    assertEquals("Setahun lalu: Gunung Bromo", annualBody)

    assertEquals(
      setOf(ReminderTimingOption.H_MINUS_1, ReminderTimingOption.ONE_HOUR_BEFORE),
      ReminderTimingOption.fromIds(null)
    )
    assertEquals(
      setOf(ReminderTimingOption.THREE_HOURS_BEFORE),
      ReminderTimingOption.fromIds(setOf("3_hours_before", "invalid"))
    )
  }

  @Test
  fun `BackupJsonCodec round-trips special characters and rejects malformed JSON`() {
    val payload = linkedMapOf<String, Any?>(
      "title" to "Kenangan \"Bali\" \n Baris 2 \\ OK",
      "count" to 42L,
      "ok" to true,
      "empty" to null,
      "tags" to listOf("pantai", "kuliner")
    )
    val encoded = BackupJsonCodec.encodePretty(payload)
    val decoded = BackupJsonCodec.decodeObject(encoded)
    assertEquals("Kenangan \"Bali\" \n Baris 2 \\ OK", decoded["title"])
    assertEquals(42L, decoded["count"])
    assertEquals(true, decoded["ok"])
    assertNull(decoded["empty"])
    assertEquals(listOf("pantai", "kuliner"), decoded["tags"])

    var failed = false
    try {
      BackupJsonCodec.decodeObject("{broken json")
    } catch (_: BackupCorruptException) {
      failed = true
    }
    assertTrue(failed)
  }

  @Test
  fun `BackupManager exports personal data only, preserves media structure and original bytes, respects video toggle, and restores without duplicates`() {
    val sourceFilesDir = Files.createTempDirectory("tb_src_files").toFile()
    val destFilesDir = Files.createTempDirectory("tb_dst_files").toFile()
    val zipDir = Files.createTempDirectory("tb_zip_out").toFile()

    try {
      val visitId1 = "visit-personal-1"
      val visitId2 = "visit-personal-2"
      val groupVisitId = "visit-group-1"
      val groupTripId = Trip.sharedTripUuid("bali_group")

      val photoDir = File(sourceFilesDir, "fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1").apply { mkdirs() }
      val photoFile = File(photoDir, "foto-1.jpg")
      val originalPhotoBytes = ByteArray(2048) { idx -> ((idx * 17) % 251).toByte() }
      photoFile.writeBytes(originalPhotoBytes)

      val videoFile = File(photoDir, "video-1.mp4")
      val originalVideoBytes = ByteArray(4096) { idx -> ((idx * 31) % 241).toByte() }
      videoFile.writeBytes(originalVideoBytes)

      val dayPast = DateUtils.epochDayFromYmd(2026, 9, 10)
      val dayFuture = DateUtils.epochDayFromYmd(2027, 1, 15)

      val k1 = Kunjungan(
        id = visitId1,
        tripId = Trip.PERSONAL_TRIP_ID,
        locationName = "Pantai Kuta",
        visitDateEpochDay = dayPast,
        visitHour = 17,
        visitMinute = 30,
        memoryNotes = "Sunset pertama"
      )
      val k2 = Kunjungan(
        id = visitId2,
        tripId = Trip.PERSONAL_TRIP_ID,
        locationName = "Ubud",
        visitDateEpochDay = dayFuture,
        visitHour = 10,
        visitMinute = 0
      )
      val kGroup = Kunjungan(
        id = groupVisitId,
        tripId = groupTripId,
        locationName = "Grup Only Spot",
        visitDateEpochDay = dayPast,
        remoteDocId = "doc_1",
        remoteTripId = "bali_group"
      )

      val fPhoto = Foto(
        id = "foto-1",
        kunjunganId = visitId1,
        tripId = Trip.PERSONAL_TRIP_ID,
        filePath = photoFile.absolutePath,
        mediaType = Foto.MEDIA_TYPE_PHOTO
      )
      val fVideo = Foto(
        id = "video-1",
        kunjunganId = visitId1,
        tripId = Trip.PERSONAL_TRIP_ID,
        filePath = videoFile.absolutePath,
        mediaType = Foto.MEDIA_TYPE_VIDEO
      )

      // Estimate with & without video
      val estWithVideo = BackupManager.computeBackupEstimate(
        filesDir = sourceFilesDir,
        personalKunjungan = listOf(k1, k2),
        personalFotos = listOf(fPhoto, fVideo),
        includeVideos = true,
        todayEpochDay = dayPast + 1
      )
      val estWithoutVideo = BackupManager.computeBackupEstimate(
        filesDir = sourceFilesDir,
        personalKunjungan = listOf(k1, k2),
        personalFotos = listOf(fPhoto, fVideo),
        includeVideos = false,
        todayEpochDay = dayPast + 1
      )
      assertEquals(1, estWithVideo.photoCount)
      assertEquals(1, estWithVideo.videoCount)
      assertEquals(0, estWithoutVideo.videoCount)
      assertTrue(estWithVideo.estimatedZipBytes > estWithoutVideo.estimatedZipBytes)

      // Export WITH videos
      val zipWithVideo = File(zipDir, "backup_full.zip")
      val settings = BackupSettingsPayload(
        themeId = AppThemeOption.UNGU_SENJA.id,
        remindersEnabled = false,
        selectedTimingIds = listOf(ReminderTimingOption.THREE_HOURS_BEFORE.id),
        annualMemoryReminderEnabled = false
      )
      val manifest = BackupManager.createBackupZipToFile(
        filesDir = sourceFilesDir,
        destZipFile = zipWithVideo,
        personalTrip = Trip.personalTrip(),
        personalKunjungan = listOf(k1, k2, kGroup),
        personalFotos = listOf(fPhoto, fVideo),
        settings = settings,
        includeVideos = true,
        todayEpochDay = dayPast + 1,
        exportedAtMillis = 1_764_298_800_000L,
        availableDestBytesProvider = { 500_000_000L }
      )
      assertEquals(2, manifest.kunjunganCount) // kGroup excluded!
      assertEquals(1, manifest.memoryCount)
      assertEquals(1, manifest.photoCount)
      assertEquals(1, manifest.videoCount)

      // Verify ZIP entries preserve folder structure and exclude group visits from data.json
      ZipFile(zipWithVideo).use { zf ->
        assertTrue(zf.getEntry("manifest.json") != null)
        assertTrue(zf.getEntry("data.json") != null)
        assertTrue(zf.getEntry("fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1/foto-1.jpg") != null)
        assertTrue(zf.getEntry("fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1/video-1.mp4") != null)
        val parsedData = BackupManager.parseDataJson(
          zf.getInputStream(zf.getEntry("data.json")).bufferedReader().readText()
        )
        assertEquals(setOf(visitId1, visitId2), parsedData.kunjunganList.map { it.id }.toSet())
      }

      // Export WITHOUT videos
      val zipNoVideo = File(zipDir, "backup_no_vid.zip")
      val manifestNoVid = BackupManager.createBackupZipToFile(
        filesDir = sourceFilesDir,
        destZipFile = zipNoVideo,
        personalTrip = Trip.personalTrip(),
        personalKunjungan = listOf(k1, k2),
        personalFotos = listOf(fPhoto, fVideo),
        settings = settings,
        includeVideos = false,
        todayEpochDay = dayPast + 1,
        availableDestBytesProvider = { 500_000_000L }
      )
      assertEquals(1, manifestNoVid.photoCount)
      assertEquals(0, manifestNoVid.videoCount)
      ZipFile(zipNoVideo).use { zf ->
        assertNull(zf.getEntry("fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1/video-1.mp4"))
      }

      // Restore onto clean destination, pre-populating k1 & fPhoto to verify zero duplication
      val restoreResult = BackupManager.extractBackupZipFromStream(
        streamProvider = { zipWithVideo.inputStream() },
        filesDir = destFilesDir,
        existingKunjunganIds = setOf(visitId1),
        existingFotoIds = setOf("foto-1"),
        expectedTotalBytes = manifest.totalMediaBytes,
        expectedTotalMediaFiles = manifest.mediaFileCount,
        availableDestBytesProvider = { 500_000_000L }
      )
      assertEquals(listOf(visitId2), restoreResult.newKunjungan.map { it.id })
      assertEquals(1, restoreResult.skippedKunjunganCount)
      assertEquals(listOf("video-1"), restoreResult.newFotos.map { it.id })
      assertEquals(1, restoreResult.skippedFotoCount)
      assertEquals(AppThemeOption.UNGU_SENJA.id, restoreResult.restoredSettings.themeId)

      val restoredPhoto = File(destFilesDir, "fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1/foto-1.jpg")
      val restoredVideo = File(destFilesDir, "fotos/${Trip.PERSONAL_TRIP_ID}/$visitId1/video-1.mp4")
      assertTrue(restoredPhoto.exists())
      assertTrue(restoredVideo.exists())
      assertArrayEquals(originalPhotoBytes, restoredPhoto.readBytes())
      assertArrayEquals(originalVideoBytes, restoredVideo.readBytes())

      // Low storage space error check
      var lowSpaceThrown = false
      try {
        BackupManager.createBackupZipToFile(
          filesDir = sourceFilesDir,
          destZipFile = File(zipDir, "fail.zip"),
          personalTrip = Trip.personalTrip(),
          personalKunjungan = listOf(k1),
          personalFotos = listOf(fPhoto),
          settings = settings,
          includeVideos = true,
          todayEpochDay = dayPast + 1,
          availableDestBytesProvider = { 1024L }
        )
      } catch (_: BackupInsufficientSpaceException) {
        lowSpaceThrown = true
      }
      assertTrue(lowSpaceThrown)

      // Corrupt / unsafe path checks
      var corruptThrown = false
      try {
        BackupManager.extractBackupZipFromStream(
          streamProvider = { ByteArrayInputStream("not a zip".toByteArray()) },
          filesDir = destFilesDir,
          existingKunjunganIds = emptySet(),
          existingFotoIds = emptySet(),
          expectedTotalBytes = 0L,
          expectedTotalMediaFiles = 0,
          availableDestBytesProvider = { 500_000_000L }
        )
      } catch (_: BackupCorruptException) {
        corruptThrown = true
      }
      assertTrue(corruptThrown)

      val traversalZip = File(zipDir, "bad_traversal.zip")
      ZipOutputStream(traversalZip.outputStream()).use { zos ->
        zos.putNextEntry(ZipEntry("../../evil.txt"))
        zos.write("x".toByteArray())
        zos.closeEntry()
      }
      var traversalBlocked = false
      try {
        BackupManager.extractBackupZipFromStream(
          streamProvider = { traversalZip.inputStream() },
          filesDir = destFilesDir,
          existingKunjunganIds = emptySet(),
          existingFotoIds = emptySet(),
          expectedTotalBytes = 0L,
          expectedTotalMediaFiles = 0,
          availableDestBytesProvider = { 500_000_000L }
        )
      } catch (_: BackupCorruptException) {
        traversalBlocked = true
      }
      assertTrue(traversalBlocked)
    } finally {
      sourceFilesDir.deleteRecursively()
      destFilesDir.deleteRecursively()
      zipDir.deleteRecursively()
    }
  }

  @Test
  fun `DriveBackupModels round-trips manifest, computes SHA-256, plans incremental diff, and enforces 3-version retention`() {
    val tmpDir = Files.createTempDirectory("tb_drive_test").toFile()
    try {
      val f1 = File(tmpDir, "photo1.jpg").apply { writeBytes("photo-one-bytes".toByteArray()) }
      val f2 = File(tmpDir, "photo2.jpg").apply { writeBytes("photo-two-updated".toByteArray()) }
      val f3 = File(tmpDir, "video3.mp4").apply { writeBytes("video-three-new".toByteArray()) }

      val sha1 = DriveBackupModels.computeSha256(f1)
      val sha2 = DriveBackupModels.computeSha256(f2)
      val sha3 = DriveBackupModels.computeSha256(f3)
      assertEquals(64, sha1.length)
      assertNotEquals(sha1, sha2)

      val entry1Prev = DriveMediaManifestEntry(
        relativePath = "fotos/t/k/photo1.jpg",
        driveFileId = "drive_m1",
        sizeBytes = f1.length(),
        lastModifiedMillis = 100L,
        sha256 = sha1,
        isVideo = false
      )
      val entry2Old = DriveMediaManifestEntry(
        relativePath = "fotos/t/k/photo2.jpg",
        driveFileId = "drive_m2_old",
        sizeBytes = 5L,
        lastModifiedMillis = 100L,
        sha256 = "oldhash",
        isVideo = false
      )
      val entryMissingRemote = DriveMediaManifestEntry(
        relativePath = "fotos/t/k/missing.jpg",
        driveFileId = "drive_deleted_remotely",
        sizeBytes = f1.length(),
        lastModifiedMillis = 100L,
        sha256 = sha1,
        isVideo = false
      )

      val prevManifest = DriveBackupManifest(
        createdAtMillis = 1_764_000_000_000L,
        includesVideos = true,
        kunjunganCount = 2,
        memoryCount = 1,
        photoCount = 2,
        videoCount = 0,
        totalMediaBytes = 100L,
        dataFileId = "drive_data_prev",
        dataSha256 = "datasha",
        mediaFiles = listOf(entry1Prev, entry2Old, entryMissingRemote)
      )

      val encoded = DriveBackupModels.encodeDriveManifestJson(prevManifest)
      val decoded = DriveBackupModels.parseDriveManifestJson(encoded)
      assertEquals(prevManifest.createdAtMillis, decoded.createdAtMillis)
      assertEquals("drive_data_prev", decoded.dataFileId)
      assertEquals(3, decoded.mediaFiles.size)
      assertEquals("drive_m1", decoded.mediaFiles[0].driveFileId)

      val localCandidates = listOf(
        LocalMediaFileCandidate(f1, "fotos/t/k/photo1.jpg", f1.length(), 100L, sha1, false),
        LocalMediaFileCandidate(f2, "fotos/t/k/photo2.jpg", f2.length(), 200L, sha2, false),
        LocalMediaFileCandidate(f3, "fotos/t/k/video3.mp4", f3.length(), 300L, sha3, true)
      )
      val plan = DriveBackupModels.planIncrementalMediaUpload(
        localFiles = localCandidates,
        previousManifest = decoded,
        existingRemoteMediaFileIds = setOf("drive_m1", "drive_m2_old")
      )
      assertEquals(listOf("drive_m1"), plan.reusedEntries.map { it.driveFileId })
      assertEquals(
        listOf("fotos/t/k/photo2.jpg", "fotos/t/k/video3.mp4"),
        plan.filesToUpload.map { it.relativePath }
      )

      // 4 manifest versions -> keep newest 3, delete 4th manifest + orphaned data + orphaned media
      fun mkSummary(ver: Long, dataId: String, mediaIds: List<String>) = DriveRemoteBackupSummary(
        manifestFileId = "man_$ver",
        fileName = "tripbuddy_manifest_$ver.json",
        manifest = DriveBackupManifest(
          createdAtMillis = ver,
          includesVideos = true,
          kunjunganCount = 1,
          memoryCount = 1,
          photoCount = mediaIds.size,
          videoCount = 0,
          totalMediaBytes = 10L,
          dataFileId = dataId,
          dataSha256 = "s$ver",
          mediaFiles = mediaIds.map { mid ->
            DriveMediaManifestEntry("fotos/$mid.jpg", mid, 10L, ver, "h", false)
          }
        )
      )

      val v4 = mkSummary(400L, "data_4", listOf("m_shared", "m_v4"))
      val v3 = mkSummary(300L, "data_3", listOf("m_shared"))
      val v2 = mkSummary(200L, "data_2", listOf("m_v2"))
      val v1 = mkSummary(100L, "data_1", listOf("m_v1_orphan"))

      val retention = DriveBackupModels.selectRetentionDeletions(
        manifestsNewestFirst = listOf(v4, v3, v2, v1),
        allRemoteMediaFileIds = setOf("m_shared", "m_v4", "m_v2", "m_v1_orphan", "m_abandoned"),
        allRemoteDataFileIds = setOf("data_4", "data_3", "data_2", "data_1"),
        maxVersions = 3
      )
      assertEquals(listOf("man_400", "man_300", "man_200"), retention.keptManifests.map { it.manifestFileId })
      assertEquals(listOf("man_100"), retention.manifestIdsToDelete)
      assertEquals(listOf("data_1"), retention.dataFileIdsToDelete)
      assertEquals(listOf("m_v1_orphan", "m_abandoned"), retention.mediaFileIdsToDelete)
    } finally {
      tmpDir.deleteRecursively()
    }
  }

  @Test
  fun `DriveBackupScheduler computes DAILY, WEEKLY, and MONTHLY initial delays accurately`() {
    val nov28 = DateUtils.epochDayFromYmd(2026, 10, 28) // Saturday, 28 Nov 2026
    val now10am = DateUtils.targetMillisForVisit(nov28, 10, 0)

    val offDelay = DriveBackupScheduler.computeInitialDelayMillis(
      DriveBackupScheduleSettings(frequency = DriveBackupFrequency.OFF),
      now10am
    )
    assertEquals(0L, offDelay)

    // DAILY at 02:00 when now is 10:00 -> next day 02:00 (+16 hours)
    val dailyDelay = DriveBackupScheduler.computeInitialDelayMillis(
      DriveBackupScheduleSettings(frequency = DriveBackupFrequency.DAILY, hour = 2, minute = 0),
      now10am
    )
    assertEquals(16L * 60L * 60L * 1000L, dailyDelay)

    // DAILY at 23:00 same day when now is 10:00 -> +13 hours
    val dailyLaterSameDay = DriveBackupScheduler.computeInitialDelayMillis(
      DriveBackupScheduleSettings(frequency = DriveBackupFrequency.DAILY, hour = 23, minute = 0),
      now10am
    )
    assertEquals(13L * 60L * 60L * 1000L, dailyLaterSameDay)

    // WEEKLY on Sunday at 02:00 when now is Saturday 10:00 -> next day 02:00 (+16 hours)
    val weeklyDelay = DriveBackupScheduler.computeInitialDelayMillis(
      DriveBackupScheduleSettings(
        frequency = DriveBackupFrequency.WEEKLY,
        dayOfWeek = java.util.Calendar.SUNDAY,
        hour = 2,
        minute = 0
      ),
      now10am
    )
    assertEquals(16L * 60L * 60L * 1000L, weeklyDelay)

    // MONTHLY on 1st at 02:00 when now is Nov 28 10:00 -> Dec 1 02:00 (2 days + 16 hours)
    val monthlyDelay = DriveBackupScheduler.computeInitialDelayMillis(
      DriveBackupScheduleSettings(
        frequency = DriveBackupFrequency.MONTHLY,
        dayOfMonth = 1,
        hour = 2,
        minute = 0
      ),
      now10am
    )
    assertEquals((2L * 24L + 16L) * 60L * 60L * 1000L, monthlyDelay)
  }
}
