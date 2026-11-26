package com.example

import com.example.collab.CollabRepository
import com.example.collab.SharedVisit
import com.example.data.TripVisitEntity
import com.example.ui.DisplayVisit
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
}
