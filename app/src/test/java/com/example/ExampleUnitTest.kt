package com.example

import androidx.compose.ui.graphics.Color
import com.example.collab.CollabRepository
import com.example.collab.SharedTrip
import com.example.collab.SharedVisit
import com.example.data.Foto
import com.example.data.Kunjungan
import com.example.data.Trip
import com.example.ui.DisplayVisit
import com.example.ui.MemoriesFilterTab
import com.example.ui.SpaceMode
import com.example.ui.TripBuddyViewModel
import com.example.ui.VisitMemoryItem
import com.example.ui.Workspace
import com.example.ui.theme.AppThemeOption
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
