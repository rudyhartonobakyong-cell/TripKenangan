package com.example.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class CountdownParts(
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isToday: Boolean,
    val isTomorrow: Boolean,
    val isOngoingOrPassedToday: Boolean
)

object DateUtils {
    private const val MILLIS_PER_DAY = 86_400_000L

    private val indonesianMonths = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

    private val indonesianShortMonths = listOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
    )

    private val indonesianDays = listOf(
        "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
    )

    val weekdayShortHeaders = listOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")

    fun todayEpochDay(nowMillis: Long = System.currentTimeMillis()): Long {
        val local = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return epochDayFromYmd(
            year = local.get(Calendar.YEAR),
            monthZeroBased = local.get(Calendar.MONTH),
            dayOfMonth = local.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun currentYearAndMonth(nowMillis: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val local = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return local.get(Calendar.YEAR) to local.get(Calendar.MONTH)
    }

    fun epochDayFromYmd(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utc.timeInMillis / MILLIS_PER_DAY
    }

    fun ymdFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
        return Triple(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun daysInMonth(year: Int, monthZeroBased: Int): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Returns 0 for Minggu (Sunday) through 6 for Sabtu (Saturday). */
    fun firstDayOfWeekInMonth(year: Int, monthZeroBased: Int): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, 1)
        }
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun dayOfWeekIndex(epochDay: Long): Int {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            timeInMillis = epochDay * MILLIS_PER_DAY
        }
        return utc.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun targetMillisForVisit(epochDay: Long, hour: Int, minute: Int): Long {
        val (year, month, day) = ymdFromEpochDay(epochDay)
        val local = Calendar.getInstance().apply {
            set(year, month, day, hour.coerceIn(0, 23), minute.coerceIn(0, 59), 0)
            set(Calendar.MILLISECOND, 0)
        }
        return local.timeInMillis
    }

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val monthName = indonesianMonths.getOrElse(monthZeroBased) { "" }
        return "$monthName $year"
    }

    fun formatIndonesianFullDate(epochDay: Long): String {
        val (year, month, day) = ymdFromEpochDay(epochDay)
        val dayName = indonesianDays.getOrElse(dayOfWeekIndex(epochDay)) { "" }
        val monthName = indonesianMonths.getOrElse(month) { "" }
        return "$dayName, $day $monthName $year"
    }

    fun formatIndonesianShortDate(epochDay: Long): String {
        val (year, month, day) = ymdFromEpochDay(epochDay)
        val monthName = indonesianShortMonths.getOrElse(month) { "" }
        return "$day $monthName $year"
    }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun computeCountdownParts(
        visitEpochDay: Long,
        visitHour: Int,
        visitMinute: Int,
        nowMillis: Long
    ): CountdownParts {
        val today = todayEpochDay(nowMillis)
        val targetMillis = targetMillisForVisit(visitEpochDay, visitHour, visitMinute)
        val diffMillis = (targetMillis - nowMillis).coerceAtLeast(0L)

        val totalSeconds = diffMillis / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        val dayDelta = visitEpochDay - today
        return CountdownParts(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            isToday = dayDelta == 0L,
            isTomorrow = dayDelta == 1L,
            isOngoingOrPassedToday = dayDelta == 0L && diffMillis == 0L
        )
    }
}
