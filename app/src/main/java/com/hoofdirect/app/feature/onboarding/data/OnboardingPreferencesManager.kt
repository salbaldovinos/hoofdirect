package com.hoofdirect.app.feature.onboarding.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hoofdirect.app.core.data.preferences.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Current onboarding version. Increment this to force re-onboarding
 * when significant changes are made to the onboarding flow.
 */
private const val CURRENT_ONBOARDING_VERSION = 1

/**
 * Manages onboarding state persistence using DataStore.
 */
@Singleton
class OnboardingPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ONBOARDING_VERSION = intPreferencesKey("onboarding_version")
        val COMPLETED_AT = longPreferencesKey("onboarding_completed_at")
        val SKIP_COUNT = intPreferencesKey("onboarding_skip_count")
        val LAST_STEP_COMPLETED = stringPreferencesKey("onboarding_last_step")
        val PROFILE_BUSINESS_NAME = stringPreferencesKey("onboarding_profile_business_name")
        val PROFILE_PHONE = stringPreferencesKey("onboarding_profile_phone")
        val PROFILE_ADDRESS = stringPreferencesKey("onboarding_profile_address")
    }

    /**
     * Whether onboarding has been completed for the current version.
     */
    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val complete = prefs[Keys.ONBOARDING_COMPLETE] ?: false
        val version = prefs[Keys.ONBOARDING_VERSION] ?: 0
        // Only consider complete if the version matches current
        complete && version == CURRENT_ONBOARDING_VERSION
    }

    /**
     * The timestamp when onboarding was completed, if available.
     */
    val completedAt: Flow<Instant?> = context.dataStore.data.map { prefs ->
        prefs[Keys.COMPLETED_AT]?.let { Instant.ofEpochMilli(it) }
    }

    /**
     * Number of times the user has skipped onboarding.
     */
    val skipCount: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SKIP_COUNT] ?: 0
    }

    /**
     * The last step completed during onboarding (for resuming).
     */
    val lastStepCompleted: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_STEP_COMPLETED]
    }

    /**
     * Mark onboarding as complete.
     */
    suspend fun setOnboardingComplete() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = true
            prefs[Keys.ONBOARDING_VERSION] = CURRENT_ONBOARDING_VERSION
            prefs[Keys.COMPLETED_AT] = Instant.now().toEpochMilli()
        }
    }

    /**
     * Reset onboarding state (useful for testing or if user wants to redo).
     */
    suspend fun resetOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = false
            prefs.remove(Keys.COMPLETED_AT)
            prefs.remove(Keys.LAST_STEP_COMPLETED)
            prefs.remove(Keys.PROFILE_BUSINESS_NAME)
            prefs.remove(Keys.PROFILE_PHONE)
            prefs.remove(Keys.PROFILE_ADDRESS)
        }
    }

    /**
     * Increment the skip count when user skips onboarding.
     */
    suspend fun incrementSkipCount() {
        context.dataStore.edit { prefs ->
            val currentCount = prefs[Keys.SKIP_COUNT] ?: 0
            prefs[Keys.SKIP_COUNT] = currentCount + 1
        }
    }

    /**
     * Save the last completed step for resuming later.
     */
    suspend fun saveLastStep(step: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_STEP_COMPLETED] = step
        }
    }

    /**
     * Save profile data during onboarding for recovery if user leaves.
     */
    suspend fun saveProfileData(businessName: String, phone: String, address: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILE_BUSINESS_NAME] = businessName
            prefs[Keys.PROFILE_PHONE] = phone
            prefs[Keys.PROFILE_ADDRESS] = address
        }
    }

    /**
     * Get saved profile data for resuming onboarding.
     */
    val savedProfileData: Flow<SavedProfileData> = context.dataStore.data.map { prefs ->
        SavedProfileData(
            businessName = prefs[Keys.PROFILE_BUSINESS_NAME] ?: "",
            phone = prefs[Keys.PROFILE_PHONE] ?: "",
            address = prefs[Keys.PROFILE_ADDRESS] ?: ""
        )
    }
}

/**
 * Saved profile data from incomplete onboarding.
 */
data class SavedProfileData(
    val businessName: String,
    val phone: String,
    val address: String
) {
    val isEmpty: Boolean get() = businessName.isEmpty() && phone.isEmpty() && address.isEmpty()
}
