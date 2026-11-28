package com.example.notifications

import android.content.Context

enum class ReminderTimingOption(
    val id: String,
    val label: String,
    val description: String
) {
    H_MINUS_1(
        id = "h_minus_1",
        label = "H-1 pukul 09.00",
        description = "Sehari sebelum kunjungan jam 09.00"
    ),
    THREE_HOURS_BEFORE(
        id = "3_hours_before",
        label = "3 jam sebelumnya",
        description = "3 jam sebelum waktu kunjungan"
    ),
    ONE_HOUR_BEFORE(
        id = "1_hour_before",
        label = "1 jam sebelumnya",
        description = "1 jam sebelum waktu kunjungan"
    );

    companion object {
        val DEFAULT_TIMINGS: Set<ReminderTimingOption> = setOf(H_MINUS_1, ONE_HOUR_BEFORE)

        fun fromIds(ids: Set<String>?): Set<ReminderTimingOption> {
            if (ids == null) return DEFAULT_TIMINGS
            return ids.mapNotNull { id -> entries.find { it.id == id } }.toSet()
        }
    }
}

data class UserReminderSettings(
    val enabled: Boolean = true,
    val selectedTimings: Set<ReminderTimingOption> = ReminderTimingOption.DEFAULT_TIMINGS,
    val annualMemoryReminderEnabled: Boolean = true
)

object ReminderPreferences {
    private const val PREFS_NAME = "tripbuddy_prefs"
    private const val KEY_LAST_ACTIVE_USER = "reminder_last_active_uid"

    private fun keyEnabled(uid: String) = "reminder_enabled_$uid"
    private fun keyTimings(uid: String) = "reminder_timings_$uid"
    private fun keyAnnual(uid: String) = "reminder_annual_$uid"

    fun setLastActiveUserId(context: Context, userId: String) {
        if (userId.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ACTIVE_USER, userId)
            .apply()
    }

    fun loadSettings(context: Context, userId: String): UserReminderSettings {
        val uidKey = userId.ifBlank { "default" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(keyEnabled(uidKey), true)
        val rawTimings = prefs.getStringSet(keyTimings(uidKey), null)
        val timings = if (rawTimings == null) {
            ReminderTimingOption.DEFAULT_TIMINGS
        } else {
            ReminderTimingOption.fromIds(rawTimings)
        }
        val annualEnabled = prefs.getBoolean(keyAnnual(uidKey), true)
        return UserReminderSettings(
            enabled = enabled,
            selectedTimings = timings,
            annualMemoryReminderEnabled = annualEnabled
        )
    }

    fun saveSettings(context: Context, userId: String, settings: UserReminderSettings) {
        val uidKey = userId.ifBlank { "default" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_LAST_ACTIVE_USER, uidKey)
            .putBoolean(keyEnabled(uidKey), settings.enabled)
            .putStringSet(keyTimings(uidKey), settings.selectedTimings.map { it.id }.toSet())
            .putBoolean(keyAnnual(uidKey), settings.annualMemoryReminderEnabled)
            .apply()
    }

    fun loadActiveUserSettings(context: Context): UserReminderSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val activeUid = prefs.getString(KEY_LAST_ACTIVE_USER, null) ?: "default"
        return loadSettings(context, activeUid)
    }
}
