package com.example

import androidx.compose.ui.graphics.Color
import com.example.collab.CollabRepository
import com.example.collab.SharedVisit
import com.example.data.TripVisitEntity
import com.example.ui.DisplayVisit
import com.example.ui.Workspace
import com.example.ui.theme.AppThemeOption
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun `DisplayVisit maps local and shared visits cleanly`() {
    val local = TripVisitEntity(
      id = 42L,
      locationName = "Ubud",
      tripTitle = "Bali",
      visitDateEpochDay = 20500L,
      visitHour = 10,
      visitMinute = 15,
      isCompletedManually = true
    )
    val localDisplay = DisplayVisit.fromLocal(local)
    assertFalse(localDisplay.isShared)
    assertEquals(42L, localDisplay.localRoomId)
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
    val sharedDisplay = DisplayVisit.fromShared(shared)
    assertTrue(sharedDisplay.isShared)
    assertEquals("doc123", sharedDisplay.sharedDocId)
    assertEquals("trip456", sharedDisplay.sharedTripId)
    assertEquals("Budi", sharedDisplay.createdByName)
    assertFalse(sharedDisplay.completedManually)
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
    assertEquals("local", Workspace.Local.storageKey())
    assertEquals("shared_trip123", Workspace.Shared("trip123").storageKey())
  }
}
