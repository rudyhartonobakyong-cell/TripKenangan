package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches TripBuddy`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TripBuddy", appName)
  }

  @Test
  fun `epochDay round trip and Indonesian formatting work accurately`() {
    val epochDay = DateUtils.epochDayFromYmd(2026, 10, 25) // 25 November 2026 (0-based month)
    val (y, m, d) = DateUtils.ymdFromEpochDay(epochDay)
    assertEquals(2026, y)
    assertEquals(10, m)
    assertEquals(25, d)
    assertTrue(DateUtils.formatIndonesianFullDate(epochDay).contains("25 November 2026"))
    assertEquals("25 Nov 2026", DateUtils.formatIndonesianShortDate(epochDay))
  }
}
