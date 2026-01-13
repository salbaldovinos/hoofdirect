package com.hoofdirect.app.feature.auth.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        encryptedPrefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
            .apply()
    }

    fun getAccessToken(): String? = encryptedPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)

    fun isTokenExpired(): Boolean {
        val expiry = encryptedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        // Consider token expired 5 minutes before actual expiry for safety
        return System.currentTimeMillis() >= (expiry - 300_000)
    }

    fun saveUserId(userId: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun getUserId(): String? = encryptedPrefs.getString(KEY_USER_ID, null)

    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    fun isBiometricEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setRememberMe(enabled: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_REMEMBER_ME, enabled)
            .apply()
    }

    fun isRememberMeEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_REMEMBER_ME, true)
    }

    fun saveCredentialsForBiometric(email: String, password: String) {
        encryptedPrefs.edit()
            .putString(KEY_SAVED_EMAIL, email)
            .putString(KEY_SAVED_PASSWORD, password)
            .apply()
    }

    fun getSavedEmail(): String? = encryptedPrefs.getString(KEY_SAVED_EMAIL, null)

    fun getSavedPassword(): String? = encryptedPrefs.getString(KEY_SAVED_PASSWORD, null)

    fun clearTokens() {
        encryptedPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_USER_ID)
            .apply()
    }

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_SAVED_PASSWORD = "saved_password"
    }
}
