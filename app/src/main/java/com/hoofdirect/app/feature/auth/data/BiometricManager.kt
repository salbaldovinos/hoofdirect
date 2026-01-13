package com.hoofdirect.app.feature.auth.data

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricManager = AndroidBiometricManager.from(context)

    fun canAuthenticate(): BiometricStatus {
        return when (biometricManager.canAuthenticate(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            AndroidBiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            AndroidBiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            AndroidBiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            AndroidBiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            AndroidBiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sign in to Hoof Direct")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // This is called when a biometric is valid but not recognized
                    // Don't show error here, the system will handle it
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }
}

enum class BiometricStatus {
    AVAILABLE,
    NO_HARDWARE,
    UNAVAILABLE,
    NOT_ENROLLED,
    SECURITY_UPDATE_REQUIRED
}
