package com.hoofdirect.app.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

data class UserPreferences(
    // Scheduling
    val defaultDurationMinutes: Int = 45,
    val defaultCycleWeeks: Int = 6,
    val reminderDaysBefore: Int = 3,

    // Notifications
    val pushNotificationsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = false,
    val dailyDigestTime: String = "08:00", // HH:mm format

    // Display
    val theme: String = "system" // "light", "dark", "system"
)

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object PreferenceKeys {
        val DEFAULT_DURATION = intPreferencesKey("default_duration_minutes")
        val DEFAULT_CYCLE = intPreferencesKey("default_cycle_weeks")
        val REMINDER_DAYS = intPreferencesKey("reminder_days_before")
        val PUSH_NOTIFICATIONS = booleanPreferencesKey("push_notifications_enabled")
        val DAILY_DIGEST = booleanPreferencesKey("daily_digest_enabled")
        val DAILY_DIGEST_TIME = stringPreferencesKey("daily_digest_time")
        val THEME = stringPreferencesKey("theme")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            defaultDurationMinutes = prefs[PreferenceKeys.DEFAULT_DURATION] ?: 45,
            defaultCycleWeeks = prefs[PreferenceKeys.DEFAULT_CYCLE] ?: 6,
            reminderDaysBefore = prefs[PreferenceKeys.REMINDER_DAYS] ?: 3,
            pushNotificationsEnabled = prefs[PreferenceKeys.PUSH_NOTIFICATIONS] ?: true,
            dailyDigestEnabled = prefs[PreferenceKeys.DAILY_DIGEST] ?: false,
            dailyDigestTime = prefs[PreferenceKeys.DAILY_DIGEST_TIME] ?: "08:00",
            theme = prefs[PreferenceKeys.THEME] ?: "system"
        )
    }

    suspend fun setDefaultDuration(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEFAULT_DURATION] = minutes
        }
    }

    suspend fun setDefaultCycle(weeks: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEFAULT_CYCLE] = weeks
        }
    }

    suspend fun setReminderDays(days: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.REMINDER_DAYS] = days
        }
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.PUSH_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setDailyDigestEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DAILY_DIGEST] = enabled
        }
    }

    suspend fun setDailyDigestTime(time: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DAILY_DIGEST_TIME] = time
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME] = theme
        }
    }
}
