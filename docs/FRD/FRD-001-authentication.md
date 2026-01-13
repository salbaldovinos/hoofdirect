# FRD-001: Authentication & User Profiles

**PRD Reference:** PRD-001  
**Priority:** P0  
**Phase:** 1 - Foundation  
**Estimated Duration:** 2 weeks

---

## 1. Overview

### 1.1 Purpose

Implement secure user authentication and profile management as the foundation for all personalized app functionality. This includes account creation, sign-in, password reset, session management, and business profile configuration.

### 1.2 Scope

| In Scope | Out of Scope |
|----------|--------------|
| Email/password authentication | Social login (Google, Apple) |
| Email verification | Two-factor authentication |
| Password reset flow | Magic link authentication |
| Biometric authentication | Multi-language support |
| Business profile setup | Account deletion (see FRD-019) |
| Session management | Team/multi-user accounts |
| Working hours configuration | |
| Payment preferences setup | |

### 1.3 Key Differentiators

- Home address geocoding enables route optimization
- Payment preferences flow directly to invoice generation
- Offline profile access after initial sync

---

## 2. Functional Specifications

### 2.1 Account Creation

#### 2.1.1 Sign Up Screen

**Screen:** `SignUpScreen.kt`  
**ViewModel:** `AuthViewModel.kt`  
**Route:** `/signup`

**UI Elements:**

| Element | Type | Validation | Error State |
|---------|------|------------|-------------|
| Email | TextField | RFC 5322 format | "Please enter a valid email address" |
| Password | TextField (obscured) | See 2.1.2 | See 2.1.2 |
| Confirm Password | TextField (obscured) | Must match password | "Passwords do not match" |
| Show Password | IconButton toggle | — | — |
| Terms Checkbox | Checkbox | Must be checked | "You must accept the Terms of Service" |
| Create Account | Button | All validations pass | Disabled until valid |

**Password Strength Indicator:**

| Strength | Criteria | Visual |
|----------|----------|--------|
| Weak | <8 chars or missing requirements | Red bar (20%) |
| Fair | 8+ chars, 2 of 3 requirements | Orange bar (50%) |
| Good | 8+ chars, all 3 requirements | Yellow bar (75%) |
| Strong | 12+ chars, all requirements + special char | Green bar (100%) |

#### 2.1.2 Password Requirements

| Requirement | Validation Rule | Error Message |
|-------------|-----------------|---------------|
| Minimum length | ≥8 characters | "Password must be at least 8 characters" |
| Uppercase | ≥1 uppercase letter [A-Z] | "Password must contain an uppercase letter" |
| Lowercase | ≥1 lowercase letter [a-z] | "Password must contain a lowercase letter" |
| Number | ≥1 digit [0-9] | "Password must contain a number" |

```kotlin
// Password validation implementation
object PasswordValidator {
    fun validate(password: String): PasswordValidationResult {
        val errors = mutableListOf<String>()
        
        if (password.length < 8) {
            errors.add("Password must be at least 8 characters")
        }
        if (!password.any { it.isUpperCase() }) {
            errors.add("Password must contain an uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            errors.add("Password must contain a lowercase letter")
        }
        if (!password.any { it.isDigit() }) {
            errors.add("Password must contain a number")
        }
        
        return PasswordValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            strength = calculateStrength(password)
        )
    }
    
    private fun calculateStrength(password: String): PasswordStrength {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        
        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.FAIR
            score <= 5 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }
    }
}
```

#### 2.1.3 Account Creation Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ACCOUNT CREATION FLOW                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────┐                                                    │
│  │ Sign Up     │                                                    │
│  │ Screen      │                                                    │
│  └──────┬──────┘                                                    │
│         │                                                           │
│         ▼                                                           │
│  ┌─────────────────────┐    ┌─────────────────────┐                │
│  │ User fills form     │───▶│ Validate inputs     │                │
│  └─────────────────────┘    └──────────┬──────────┘                │
│                                        │                            │
│                          ┌─────────────┴─────────────┐              │
│                          ▼                           ▼              │
│                   ┌──────────────┐          ┌──────────────┐       │
│                   │ Invalid     │          │ Valid        │       │
│                   │ Show errors │          │ Call API     │       │
│                   └──────────────┘          └──────┬───────┘       │
│                                                    │                │
│                          ┌─────────────────────────┴────────┐      │
│                          ▼                                  ▼      │
│                   ┌──────────────┐               ┌──────────────┐  │
│                   │ API Error   │               │ API Success  │  │
│                   └──────┬───────┘               └──────┬───────┘  │
│                          │                              │          │
│         ┌────────────────┼────────────────┐             ▼          │
│         ▼                ▼                ▼      ┌──────────────┐  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ │ Verification │  │
│  │ Duplicate   │ │ Network     │ │ Server      │ │ Screen       │  │
│  │ Email       │ │ Error       │ │ Error       │ └──────────────┘  │
│  │             │ │             │ │             │                   │
│  │ "Email is   │ │ "Unable to  │ │ "Something  │                   │
│  │ already     │ │ connect.    │ │ went wrong. │                   │
│  │ registered" │ │ Check your  │ │ Please try  │                   │
│  └─────────────┘ │ connection" │ │ again."     │                   │
│                  └─────────────┘ └─────────────┘                   │
└─────────────────────────────────────────────────────────────────────┘
```

#### 2.1.4 API Error Handling

| Supabase Error | User Message | UI Behavior |
|----------------|--------------|-------------|
| `user_already_exists` | "This email is already registered. Try signing in instead." | Show "Sign In" link |
| `weak_password` | "Password doesn't meet requirements" | Highlight password field |
| `invalid_email` | "Please enter a valid email address" | Highlight email field |
| Network timeout | "Unable to connect. Check your internet connection and try again." | Show retry button |
| 500 error | "Something went wrong. Please try again later." | Show retry button |
| Rate limited | "Too many attempts. Please wait a few minutes." | Disable button, show countdown |

### 2.2 Email Verification

#### 2.2.1 Verification Screen

**Screen:** `EmailVerificationScreen.kt`  
**Route:** `/verify-email`

**UI Elements:**

| Element | Behavior |
|---------|----------|
| Confirmation message | "We've sent a verification email to {email}" |
| Check inbox icon | Animated envelope icon |
| Resend button | "Resend Email" (60-second cooldown after each send) |
| Open Email button | Opens device email client via Intent |
| Continue without verifying | Link to proceed with limited functionality |

**Resend Cooldown Logic:**

```kotlin
// ResendEmailCooldown.kt
class ResendEmailCooldown {
    private var lastSentTime: Long = 0
    private val cooldownMs = 60_000L // 60 seconds
    
    fun canResend(): Boolean {
        return System.currentTimeMillis() - lastSentTime >= cooldownMs
    }
    
    fun getSecondsRemaining(): Int {
        val elapsed = System.currentTimeMillis() - lastSentTime
        val remaining = (cooldownMs - elapsed) / 1000
        return maxOf(0, remaining.toInt())
    }
    
    fun markSent() {
        lastSentTime = System.currentTimeMillis()
    }
}
```

**Cooldown UI States:**

| State | Button Text | Button Enabled |
|-------|-------------|----------------|
| Can resend | "Resend Email" | Yes |
| Cooling down | "Resend in 45s" | No (grayed) |
| Sending | "Sending..." | No (loading indicator) |
| Sent | "Email Sent ✓" | No (2 seconds, then revert) |

#### 2.2.2 Deep Link Handling

**Deep Link Format:** `hoofdirect://verify?token={token}`

```kotlin
// DeepLinkHandler.kt
fun handleVerificationDeepLink(uri: Uri): VerificationResult {
    val token = uri.getQueryParameter("token") 
        ?: return VerificationResult.Error("Invalid verification link")
    
    return try {
        supabaseClient.auth.verifyOtp(
            token = token,
            type = OtpType.Email
        )
        VerificationResult.Success
    } catch (e: Exception) {
        when {
            e.message?.contains("expired") == true -> 
                VerificationResult.Expired
            else -> 
                VerificationResult.Error("Verification failed. Please try again.")
        }
    }
}
```

**Verification Results:**

| Result | UI Behavior |
|--------|-------------|
| Success | Show success animation, navigate to onboarding after 2 seconds |
| Expired | Show "This link has expired" with "Resend Email" button |
| Invalid | Show "Invalid verification link" with "Contact Support" option |
| Already verified | Show "Email already verified" and navigate to app |

### 2.3 Sign In

#### 2.3.1 Sign In Screen

**Screen:** `SignInScreen.kt`  
**ViewModel:** `AuthViewModel.kt`  
**Route:** `/signin`

**UI Elements:**

| Element | Type | Validation | Default |
|---------|------|------------|---------|
| Email | TextField | RFC 5322 format | Empty |
| Password | TextField (obscured) | Non-empty | Empty |
| Show Password | IconButton toggle | — | Hidden |
| Remember Me | Checkbox | — | Checked |
| Forgot Password | TextButton | — | — |
| Sign In | Button | Both fields valid | Disabled |
| Biometric | IconButton | If available | Hidden if unavailable |
| Create Account | TextButton | — | — |

#### 2.3.2 Sign In State Machine

```
┌──────────────────────────────────────────────────────────────────┐
│                      SIGN IN STATE MACHINE                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────┐                                                     │
│  │  IDLE   │◀─────────────────────────────────────────┐          │
│  └────┬────┘                                          │          │
│       │ Submit                                        │          │
│       ▼                                               │          │
│  ┌──────────┐                                         │          │
│  │ LOADING  │                                         │          │
│  └────┬─────┘                                         │          │
│       │                                               │          │
│   ┌───┴───────────────────┬───────────────────┐       │          │
│   ▼                       ▼                   ▼       │          │
│ ┌─────────┐         ┌───────────┐      ┌──────────┐   │          │
│ │ SUCCESS │         │ ERROR     │      │ LOCKED   │   │          │
│ └────┬────┘         └─────┬─────┘      └────┬─────┘   │          │
│      │                    │                 │         │          │
│      ▼                    │                 ▼         │          │
│ ┌──────────────┐          │         ┌──────────────┐  │          │
│ │ Navigate to  │          │         │ Show lockout │  │          │
│ │ Home/Onboard │          │         │ timer (15m)  │──┘          │
│ └──────────────┘          │         └──────────────┘             │
│                           │                                      │
│         ┌─────────────────┼─────────────────┐                    │
│         ▼                 ▼                 ▼                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐               │
│  │ Invalid     │  │ Unverified  │  │ Network     │               │
│  │ credentials │  │ email       │  │ error       │               │
│  │             │  │             │  │             │               │
│  │ "Invalid    │  │ "Please     │  │ "Unable to  │               │
│  │ email or    │  │ verify your │  │ connect"    │───────────────┘
│  │ password"   │  │ email first"│  │             │
│  └─────────────┘  └──────┬──────┘  └─────────────┘
│                          │
│                          ▼
│                   ┌─────────────┐
│                   │ Show resend │
│                   │ verification│
│                   │ option      │
│                   └─────────────┘
└──────────────────────────────────────────────────────────────────┘
```

#### 2.3.3 Sign In Error Handling

| Error Condition | Error Message | Additional Action |
|-----------------|---------------|-------------------|
| Invalid credentials | "Invalid email or password" | Clear password field |
| Unverified email | "Please verify your email before signing in" | Show "Resend verification" button |
| Account locked (5 failures) | "Too many failed attempts. Try again in 15 minutes." | Show countdown timer |
| Network error | "Unable to connect. Check your internet connection." | Show retry button |
| Server error | "Something went wrong. Please try again." | Show retry button |

#### 2.3.4 Biometric Authentication

**Availability Check:**

```kotlin
// BiometricManager.kt
class BiometricManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricManager = BiometricManager.from(context)
    
    fun canAuthenticate(): BiometricStatus {
        return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNAVAILABLE
        }
    }
    
    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sign in to Hoof Direct")
            .setSubtitle("Use your fingerprint or face to sign in")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
        
        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != ERROR_USER_CANCELED && errorCode != ERROR_NEGATIVE_BUTTON) {
                        onError(errString.toString())
                    }
                }
            }
        )
        
        biometricPrompt.authenticate(promptInfo)
    }
}
```

**Biometric Flow:**

| State | Behavior |
|-------|----------|
| User taps biometric button | Show system biometric prompt |
| Authentication succeeds | Retrieve stored credentials, auto-sign in |
| Authentication fails | Show "Try again" or fall back to password |
| User cancels | Return to normal sign-in screen |
| No biometrics enrolled | Hide biometric button, suggest enrollment in system settings |

### 2.4 Password Reset

#### 2.4.1 Forgot Password Screen

**Screen:** `ForgotPasswordScreen.kt`  
**Route:** `/forgot-password`

**UI Elements:**

| Element | Type | Validation |
|---------|------|------------|
| Email | TextField | RFC 5322 format |
| Send Reset Link | Button | Valid email |
| Back to Sign In | TextButton | — |

**Flow States:**

| State | UI |
|-------|-----|
| Initial | Email input + Send button |
| Loading | Button shows spinner, disabled |
| Success | "Check your email" message with email address displayed |
| Error (no account) | "If an account exists, we've sent a reset link." (don't reveal if account exists) |
| Error (network) | "Unable to connect. Please try again." with retry |

#### 2.4.2 Reset Password Screen

**Screen:** `ResetPasswordScreen.kt`  
**Route:** `/reset-password?token={token}`

**Deep Link:** `hoofdirect://reset-password?token={token}`

**UI Elements:**

| Element | Type | Validation |
|---------|------|------------|
| New Password | TextField (obscured) | Same as sign-up requirements |
| Confirm Password | TextField (obscured) | Must match |
| Password Strength | Indicator | Visual feedback |
| Reset Password | Button | All validations pass |

**Token Validation:**

| Scenario | Behavior |
|----------|----------|
| Valid token | Show password reset form |
| Expired token (>24 hours) | Show "This link has expired" with "Request new link" button |
| Invalid token | Show "Invalid reset link" with "Request new link" button |
| Already used | Show "This link has already been used" with "Request new link" |

**Success Flow:**

1. Password reset succeeds
2. Show success message: "Your password has been reset"
3. Clear all existing sessions (force logout everywhere)
4. Navigate to sign-in screen after 2 seconds
5. Show "Sign in with your new password" message

### 2.5 Business Profile Setup

#### 2.5.1 Profile Setup Screen

**Screen:** `ProfileSetupScreen.kt`  
**ViewModel:** `ProfileSetupViewModel.kt`  
**Route:** `/profile-setup`

**UI Sections:**

**Section 1: Business Information**

| Field | Type | Validation | Required |
|-------|------|------------|----------|
| Business Name | TextField | 1-100 characters | Yes |
| Phone | TextField | US phone format (10 digits) | Yes |
| Profile Photo | Image picker | Max 5MB, JPG/PNG | No |

**Phone Validation:**

```kotlin
// PhoneValidator.kt
object PhoneValidator {
    private val US_PHONE_REGEX = Regex("""^(\+1)?[\s.-]?\(?[0-9]{3}\)?[\s.-]?[0-9]{3}[\s.-]?[0-9]{4}$""")
    
    fun validate(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length == 10 || (digits.length == 11 && digits.startsWith("1"))
    }
    
    fun format(phone: String): String {
        val digits = phone.filter { it.isDigit() }.takeLast(10)
        return if (digits.length == 10) {
            "(${digits.substring(0,3)}) ${digits.substring(3,6)}-${digits.substring(6)}"
        } else phone
    }
}
```

**Section 2: Home Address**

| Field | Type | Behavior |
|-------|------|----------|
| Address | TextField + Autocomplete | Google Places autocomplete |
| Map Preview | Google Map | Shows pin at geocoded location |

**Address Autocomplete Flow:**

```
User types → Debounce 300ms → Places API query → Show dropdown → User selects → Geocode → Update map
```

**Geocoding Error Handling:**

| Scenario | Behavior |
|----------|----------|
| Places API unavailable | Allow manual address entry, show warning |
| Geocoding fails | "Unable to locate address. Please verify and try again." |
| No results | "No matching addresses found. Try a different format." |
| Network error | "Check your connection and try again." |

**Section 3: Service Defaults**

| Field | Type | Options | Default |
|-------|------|---------|---------|
| Service Radius | Slider | 10-100 miles (5-mile increments) | 50 miles |
| Default Duration | Dropdown | 30/45/60/90 minutes | 45 minutes |
| Default Shoeing Cycle | Dropdown | 4-12 weeks (1-week increments) | 6 weeks |

#### 2.5.2 Working Hours Configuration

**Screen Section:** Within Profile Setup or Settings

| Day | Toggle | Start Time | End Time |
|-----|--------|------------|----------|
| Sunday | Off by default | 8:00 AM | 5:00 PM |
| Monday | On by default | 7:00 AM | 6:00 PM |
| Tuesday | On by default | 7:00 AM | 6:00 PM |
| Wednesday | On by default | 7:00 AM | 6:00 PM |
| Thursday | On by default | 7:00 AM | 6:00 PM |
| Friday | On by default | 7:00 AM | 6:00 PM |
| Saturday | Off by default | 8:00 AM | 5:00 PM |

**Time Picker Constraints:**

| Constraint | Rule |
|------------|------|
| Start time range | 5:00 AM - 12:00 PM |
| End time range | 12:00 PM - 10:00 PM |
| End must be after start | Minimum 1 hour between |
| Increment | 30 minutes |

**"Copy to All" Feature:**

1. User configures one day completely
2. User taps "Copy to All"
3. Confirmation dialog: "Copy Monday's hours to all enabled days?"
4. On confirm: Apply start/end times to all days with toggle ON
5. Toast: "Hours copied to all work days"

#### 2.5.3 Payment Preferences

See FRD-014 for complete specification. Summary for profile setup:

| Method | Input Field | Placeholder |
|--------|-------------|-------------|
| Venmo | Handle | @yourhandle |
| Cash App | Cashtag | $yourcashtag |
| Zelle | Email or Phone | email@example.com |
| Square | URL | https://square.link/... |
| Check | Payable To | Your Business Name |
| Cash | Toggle only | — |

### 2.6 Session Management

#### 2.6.1 Token Storage

```kotlin
// TokenManager.kt
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
    
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
        return System.currentTimeMillis() >= expiry
    }
    
    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    fun isBiometricEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }
}
```

#### 2.6.2 Session Lifecycle

| Event | Action |
|-------|--------|
| App launch | Check token validity, refresh if needed |
| Token expires | Attempt silent refresh with refresh token |
| Refresh fails | Force sign out, navigate to sign-in |
| 30 days inactivity | Session expires, force sign out |
| Password change | Invalidate all sessions, force re-auth |
| User signs out | Clear all tokens and cached data |

#### 2.6.3 Token Refresh Flow

```kotlin
// AuthRepository.kt
suspend fun refreshTokenIfNeeded(): Result<Unit> {
    if (!tokenManager.isTokenExpired()) {
        return Result.success(Unit)
    }
    
    val refreshToken = tokenManager.getRefreshToken()
        ?: return Result.failure(AuthError.NoSession)
    
    return try {
        val response = supabaseClient.auth.refreshSession(refreshToken)
        tokenManager.saveTokens(
            response.accessToken,
            response.refreshToken ?: refreshToken,
            response.expiresIn
        )
        Result.success(Unit)
    } catch (e: Exception) {
        tokenManager.clearTokens()
        Result.failure(AuthError.SessionExpired)
    }
}
```

#### 2.6.4 Force Logout (All Devices)

**Trigger:** User taps "Sign out everywhere" in settings

**Flow:**

1. Call Supabase `signOut(scope = SignOutScope.GLOBAL)`
2. Clear local tokens
3. Clear cached user data
4. Navigate to sign-in screen
5. Show toast: "Signed out from all devices"

---

## 3. Data Models

### 3.1 User Entity

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey 
    val id: String,
    
    val email: String,
    
    @ColumnInfo(name = "email_verified")
    val emailVerified: Boolean = false,
    
    @ColumnInfo(name = "business_name")
    val businessName: String,
    
    val phone: String,
    
    val address: String,
    
    @ColumnInfo(name = "home_latitude")
    val homeLatitude: Double?,
    
    @ColumnInfo(name = "home_longitude")
    val homeLongitude: Double?,
    
    @ColumnInfo(name = "service_radius_miles")
    val serviceRadiusMiles: Int = 50,
    
    @ColumnInfo(name = "default_duration_minutes")
    val defaultDurationMinutes: Int = 45,
    
    @ColumnInfo(name = "default_cycle_weeks")
    val defaultCycleWeeks: Int = 6,
    
    @ColumnInfo(name = "working_hours")
    val workingHours: String, // JSON: WorkingHours
    
    @ColumnInfo(name = "payment_preferences")
    val paymentPreferences: String, // JSON: PaymentPreferences
    
    @ColumnInfo(name = "subscription_tier")
    val subscriptionTier: String = "free",
    
    @ColumnInfo(name = "profile_photo_url")
    val profilePhotoUrl: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "SYNCED"
)
```

### 3.2 Working Hours Model

```kotlin
@Serializable
data class WorkingHours(
    val sunday: DayHours = DayHours(enabled = false),
    val monday: DayHours = DayHours(enabled = true),
    val tuesday: DayHours = DayHours(enabled = true),
    val wednesday: DayHours = DayHours(enabled = true),
    val thursday: DayHours = DayHours(enabled = true),
    val friday: DayHours = DayHours(enabled = true),
    val saturday: DayHours = DayHours(enabled = false)
)

@Serializable
data class DayHours(
    val enabled: Boolean,
    val startTime: String = "07:00", // HH:mm format
    val endTime: String = "18:00"
)
```

### 3.3 Authentication State

```kotlin
sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    object Unauthenticated : AuthState()
    data class Error(val error: AuthError) : AuthState()
}

sealed class AuthError {
    object InvalidCredentials : AuthError()
    object EmailNotVerified : AuthError()
    object AccountLocked : AuthError()
    object NetworkError : AuthError()
    object SessionExpired : AuthError()
    object NoSession : AuthError()
    data class Unknown(val message: String) : AuthError()
}
```

---

## 4. Security Specifications

### 4.1 Rate Limiting

| Action | Limit | Window | Lockout |
|--------|-------|--------|---------|
| Sign in attempts | 5 failures | Per session | 15 minutes |
| Password reset requests | 3 per email | 1 hour | — |
| Verification email resend | 1 per minute | Per session | — |
| Account creation | 3 per IP | 1 hour | — |

### 4.2 Security Controls

| Control | Implementation |
|---------|----------------|
| Password hashing | Argon2id (server-side via Supabase) |
| Token encryption | AES-256-GCM via EncryptedSharedPreferences |
| Network security | TLS 1.3, certificate pinning |
| Session timeout | 30 days inactivity |
| Biometric | Android BiometricPrompt with BIOMETRIC_STRONG |

### 4.3 Security Audit Checklist

- [ ] Passwords never logged (including debug builds)
- [ ] Tokens stored only in EncryptedSharedPreferences
- [ ] No hardcoded secrets in codebase
- [ ] All API calls over HTTPS
- [ ] Error messages don't leak account existence
- [ ] Input validation on all user inputs
- [ ] Deep links validated before processing
- [ ] Session invalidated on password change

---

## 5. Acceptance Criteria

### 5.1 Account Creation

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-01 | User is on sign-up screen | User enters valid email, password meeting requirements, confirms password, accepts terms, taps Create Account | Account is created, verification email sent, user sees verification screen |
| AC-001-02 | User is on sign-up screen | User enters email already registered | Error message "This email is already registered. Try signing in instead." with Sign In link |
| AC-001-03 | User is on sign-up screen | User enters password "short" | Password field shows "Password must be at least 8 characters", Create Account disabled |
| AC-001-04 | User is on sign-up screen | User enters password without uppercase | Password field shows "Password must contain an uppercase letter" |
| AC-001-05 | User is on sign-up screen | Passwords don't match | Confirm password shows "Passwords do not match" |
| AC-001-06 | User is on sign-up screen | Terms checkbox unchecked | Create Account button disabled |
| AC-001-07 | User has no network connection | User attempts sign up | Error message "Unable to connect. Check your internet connection and try again." with retry |

### 5.2 Email Verification

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-08 | User is on verification screen | User taps Resend Email | Email sent, button disabled for 60 seconds showing countdown |
| AC-001-09 | User tapped Resend 5 seconds ago | User views button | Button shows "Resend in 55s" and is disabled |
| AC-001-10 | User receives verification email | User taps link in email | App opens, verification succeeds, navigates to onboarding |
| AC-001-11 | Verification link is expired (>24 hours) | User taps link | App shows "This link has expired" with "Resend verification" button |

### 5.3 Sign In

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-12 | User is on sign-in screen | User enters correct email and password, taps Sign In | User signed in, navigates to home (or onboarding if profile incomplete) |
| AC-001-13 | User is on sign-in screen | User enters incorrect password | Error "Invalid email or password", password field cleared |
| AC-001-14 | User has failed sign-in 5 times | User attempts 6th sign-in | Error "Too many failed attempts. Try again in 15 minutes." with countdown |
| AC-001-15 | User email not verified | User attempts sign-in | Error "Please verify your email before signing in" with resend option |
| AC-001-16 | User has biometrics set up | User taps biometric button | System biometric prompt appears |
| AC-001-17 | Biometric authentication succeeds | — | User automatically signed in |
| AC-001-18 | Remember Me is checked | User signs in successfully | Session persists after app restart |
| AC-001-19 | Remember Me is unchecked | User signs in, closes app, reopens after 24 hours | User must sign in again |

### 5.4 Password Reset

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-20 | User is on forgot password screen | User enters registered email, taps Send Reset Link | Success message "Check your email for reset link", email sent |
| AC-001-21 | User is on forgot password screen | User enters unregistered email | Same success message shown (don't reveal account existence) |
| AC-001-22 | User receives reset email | User taps link within 24 hours | App opens to password reset form |
| AC-001-23 | User on reset form | User enters valid new password matching requirements | Password reset, all sessions invalidated, navigates to sign-in |
| AC-001-24 | Reset link older than 24 hours | User taps link | "This link has expired" with "Request new link" button |

### 5.5 Profile Setup

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-25 | User is on profile setup | User enters business name, phone, selects address from autocomplete | Map shows pin at address location, Save enabled |
| AC-001-26 | User is on profile setup | User enters phone "555-1234" (7 digits) | Error "Please enter a valid 10-digit phone number" |
| AC-001-27 | User is on profile setup | User types address, no autocomplete results | Message "No matching addresses found" with option to enter manually |
| AC-001-28 | User is on profile setup | Network unavailable during address entry | Warning "Address lookup unavailable offline. You can set this later." |
| AC-001-29 | User saves profile | — | Profile saved locally, synced when online, user proceeds to home |

### 5.6 Session Management

| ID | Given | When | Then |
|----|-------|------|------|
| AC-001-30 | User is signed in | User inactive for 30 days | Session expires, user sees sign-in screen on next app open |
| AC-001-31 | User changes password | — | All other sessions invalidated immediately |
| AC-001-32 | User taps "Sign out everywhere" | — | All sessions invalidated, user signed out on all devices |
| AC-001-33 | Access token expires | User performs action | Token silently refreshed, action completes |
| AC-001-34 | Refresh token invalid | Token refresh attempted | User signed out, navigates to sign-in with message |

---

## 6. Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Sign in completion | < 2 seconds (p95) | From button tap to navigation |
| Profile load from cache | < 500ms | From request to render |
| Address autocomplete response | < 300ms | From query to results |
| Biometric prompt appearance | < 200ms | From button tap to prompt |
| Token refresh | < 1 second | Silent, non-blocking |

---

## 7. Accessibility Requirements

| Requirement | Implementation |
|-------------|----------------|
| Screen reader | All inputs have contentDescription |
| Touch targets | Minimum 48dp × 48dp |
| Error announcements | Errors announced via `announceForAccessibility` |
| High contrast | All text meets WCAG AA contrast (4.5:1) |
| Font scaling | Layouts support 200% text scaling |
| Focus order | Logical top-to-bottom, left-to-right |

---

## 8. Test Specifications

### 8.1 Unit Tests

```kotlin
class PasswordValidatorTest {
    @Test
    fun `password shorter than 8 chars is invalid`() {
        val result = PasswordValidator.validate("Short1")
        assertFalse(result.isValid)
        assertContains(result.errors, "Password must be at least 8 characters")
    }
    
    @Test
    fun `password without uppercase is invalid`() {
        val result = PasswordValidator.validate("alllowercase1")
        assertFalse(result.isValid)
        assertContains(result.errors, "Password must contain an uppercase letter")
    }
    
    @Test
    fun `valid password passes all checks`() {
        val result = PasswordValidator.validate("ValidPass123")
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `strong password with special char gets STRONG rating`() {
        val result = PasswordValidator.validate("SuperStrong123!")
        assertEquals(PasswordStrength.STRONG, result.strength)
    }
}

class PhoneValidatorTest {
    @Test
    fun `10 digit phone is valid`() {
        assertTrue(PhoneValidator.validate("5551234567"))
    }
    
    @Test
    fun `formatted phone is valid`() {
        assertTrue(PhoneValidator.validate("(555) 123-4567"))
    }
    
    @Test
    fun `7 digit phone is invalid`() {
        assertFalse(PhoneValidator.validate("5551234"))
    }
    
    @Test
    fun `phone formats correctly`() {
        assertEquals("(555) 123-4567", PhoneValidator.format("5551234567"))
    }
}

class AuthViewModelTest {
    @Test
    fun `signIn with valid credentials updates state to Authenticated`() = runTest {
        coEvery { signInUseCase(email, password) } returns Result.success(user)
        
        viewModel.signIn(email, password)
        advanceUntilIdle()
        
        assertEquals(AuthState.Authenticated(user), viewModel.authState.value)
    }
    
    @Test
    fun `signIn with invalid credentials shows error`() = runTest {
        coEvery { signInUseCase(email, password) } returns 
            Result.failure(AuthError.InvalidCredentials)
        
        viewModel.signIn(email, password)
        advanceUntilIdle()
        
        assertEquals(
            AuthState.Error(AuthError.InvalidCredentials), 
            viewModel.authState.value
        )
    }
    
    @Test
    fun `5 failed attempts triggers lockout`() = runTest {
        repeat(5) {
            coEvery { signInUseCase(any(), any()) } returns 
                Result.failure(AuthError.InvalidCredentials)
            viewModel.signIn("test@test.com", "wrongpassword")
            advanceUntilIdle()
        }
        
        assertEquals(AuthState.Error(AuthError.AccountLocked), viewModel.authState.value)
    }
}
```

### 8.2 Integration Tests

```kotlin
@HiltAndroidTest
class AuthRepositoryTest {
    @Test
    fun signIn_savesUserToLocalDatabase() = runTest {
        val result = authRepository.signIn(testEmail, testPassword)
        
        assertTrue(result.isSuccess)
        assertNotNull(userDao.getUser(result.getOrThrow().id))
    }
    
    @Test
    fun signIn_storesTokensSecurely() = runTest {
        authRepository.signIn(testEmail, testPassword)
        
        assertNotNull(tokenManager.getAccessToken())
        assertNotNull(tokenManager.getRefreshToken())
    }
    
    @Test
    fun signOut_clearsAllData() = runTest {
        authRepository.signIn(testEmail, testPassword)
        authRepository.signOut()
        
        assertNull(tokenManager.getAccessToken())
        assertNull(userDao.getCurrentUser())
    }
}
```

### 8.3 E2E Tests (Maestro)

```yaml
# sign_up_flow.yaml
appId: com.hoofdirect.app
---
- launchApp
- tapOn: "Create Account"
- tapOn:
    id: "email_input"
- inputText: "newuser@test.com"
- tapOn:
    id: "password_input"
- inputText: "TestPassword123"
- tapOn:
    id: "confirm_password_input"
- inputText: "TestPassword123"
- tapOn:
    id: "terms_checkbox"
- tapOn: "Create Account"
- assertVisible: "Check your email"
- assertVisible: "newuser@test.com"
```

```yaml
# sign_in_invalid_password.yaml
appId: com.hoofdirect.app
---
- launchApp
- tapOn:
    id: "email_input"
- inputText: "existing@test.com"
- tapOn:
    id: "password_input"
- inputText: "wrongpassword"
- tapOn: "Sign In"
- assertVisible: "Invalid email or password"
```

---

## 9. File References

| Purpose | File Path |
|---------|-----------|
| Sign In Screen | `feature/auth/ui/SignInScreen.kt` |
| Sign Up Screen | `feature/auth/ui/SignUpScreen.kt` |
| Profile Setup Screen | `feature/auth/ui/ProfileSetupScreen.kt` |
| Auth ViewModel | `feature/auth/ui/AuthViewModel.kt` |
| Auth Repository | `feature/auth/data/AuthRepository.kt` |
| Token Manager | `feature/auth/data/TokenManager.kt` |
| Biometric Manager | `feature/auth/data/BiometricManager.kt` |
| User Entity | `core/database/entity/UserEntity.kt` |
| User DAO | `core/database/dao/UserDao.kt` |
| Password Validator | `feature/auth/domain/PasswordValidator.kt` |
| Phone Validator | `core/common/PhoneValidator.kt` |
| Deep Link Handler | `app/navigation/DeepLinkHandler.kt` |

---

## 10. Open Questions (Resolved)

| Question | Resolution |
|----------|------------|
| Support social login? | No - Phase 1 email/password only |
| Password complexity requirements? | 8+ chars, 1 upper, 1 lower, 1 number |
| Session timeout duration? | 30 days inactivity |
| Lockout after failed attempts? | 5 attempts → 15 minute lockout |
| Email verification required? | Required for full functionality, can proceed limited |
