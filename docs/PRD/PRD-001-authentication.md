# PRD-001: Authentication & User Profiles

**Priority**: P0  
**Phase**: 1 - Foundation  
**Estimated Duration**: 2 weeks

---

## Overview

### Purpose
Establish secure user authentication and profile management as the foundation for all personalized app functionality.

### Business Value
- Enables multi-device access for paid tier users
- Secures farrier business data with industry-standard auth
- Provides home base location for route optimization
- Stores payment preferences for invoice generation

### Success Metrics
| Metric | Target |
|--------|--------|
| Account creation completion rate | > 90% |
| Login success rate | > 99% |
| Password reset completion rate | > 85% |
| Profile completion rate | > 80% |

---

## User Stories

| ID | As a... | I want to... | So that... | Priority |
|----|---------|--------------|------------|----------|
| US-001-01 | New user | Create an account with email/password | I can securely access my data | P0 |
| US-001-02 | New user | Verify my email address | My account is protected | P0 |
| US-001-03 | Returning user | Log in with saved credentials | I can quickly access my schedule | P0 |
| US-001-04 | User | Reset my password via email | I can recover my account | P0 |
| US-001-05 | User | Set up my business profile | The app knows my home base and defaults | P0 |
| US-001-06 | User | Configure my payment preferences | Invoices show how clients can pay me | P1 |
| US-001-07 | User | Set my working hours | The app respects my availability | P1 |
| US-001-08 | User | Upload a profile/business photo | My invoices look professional | P2 |
| US-001-09 | User | Log out from all devices | I can secure my account if needed | P1 |
| US-001-10 | User | Stay logged in across app restarts | I don't have to log in repeatedly | P0 |

---

## Functional Requirements

### FR-001-01: Account Creation
- Email field with RFC 5322 validation
- Password with strength indicator
- Password requirements: minimum 8 chars, 1 uppercase, 1 lowercase, 1 number
- Confirm password with match validation
- Terms of Service and Privacy Policy checkbox (required)
- "Create Account" disabled until all validations pass
- Loading state during API call
- Error handling: duplicate email, weak password, network errors
- Success transitions to email verification

### FR-001-02: Email Verification
- Display verification email sent confirmation
- "Resend Email" button with 60-second cooldown
- "Open Email App" convenience button
- Deep link handling for verification callback
- Auto-advance to onboarding on success
- Option to continue without verification (limited functionality)

### FR-001-03: Sign In
- Email field with validation
- Password field with show/hide toggle
- "Remember Me" checkbox (default: checked)
- "Forgot Password?" link
- Sign in button with loading state
- Error handling: invalid credentials, unverified email, network
- Biometric authentication option (if supported)
- Secure session token storage

### FR-001-04: Password Reset
- Email input field
- "Send Reset Link" button
- Success confirmation screen
- Deep link handling for reset callback
- New password entry with confirmation
- Password strength requirements enforced
- Success transitions to sign in

### FR-001-05: Business Profile Setup
- Business name (required, max 100 chars)
- Phone number (required, US format validation)
- Home address (required, Google Places autocomplete)
- Address geocoding to lat/long (automatic)
- Service radius slider (10-100 miles, default 50)
- Default appointment duration picker (30/45/60/90 min)
- Default shoeing cycle picker (4-12 weeks, default 6)
- Profile photo upload (optional, max 5MB)

### FR-001-06: Working Hours Configuration
- 7-day week grid
- Per-day enable/disable toggle
- Start time picker (5:00 AM - 12:00 PM)
- End time picker (12:00 PM - 10:00 PM)
- "Copy to All" convenience button

### FR-001-07: Payment Preferences
- Toggle switches for each method:
  - Venmo (handle input)
  - Cash App (cashtag input)
  - Zelle (email or phone)
  - Square (URL input)
  - Check (payable to name)
  - Cash (boolean)
- Primary/preferred method selector
- Preview of invoice appearance

### FR-001-08: Session Management
- JWT storage in EncryptedSharedPreferences
- Refresh token rotation
- Session timeout: 30 days inactivity
- Force logout capability
- Multi-device session tracking

---

## Non-Functional Requirements

### Performance
- Sign in completion: < 2 seconds (p95)
- Profile load: < 500ms from cache
- Address autocomplete: < 300ms

### Security
- All traffic over HTTPS/TLS 1.3
- Passwords never logged or stored plain text
- Tokens in EncryptedSharedPreferences
- Biometric prompt for sensitive operations
- Rate limiting: 5 failed attempts → 15-min lockout
- OWASP Mobile Top 10 compliance

### Accessibility
- Screen reader support for all inputs
- Minimum touch targets 48dp
- Error messages announced to TalkBack
- High contrast mode support
- Font scaling up to 200%

---

## Technical Implementation

### Architecture
```
feature/auth/
├── data/
│   ├── AuthRepository.kt
│   ├── AuthRemoteDataSource.kt
│   └── TokenManager.kt
├── domain/
│   ├── model/
│   │   ├── User.kt
│   │   └── AuthState.kt
│   └── usecase/
│       ├── SignInUseCase.kt
│       ├── SignUpUseCase.kt
│       ├── SignOutUseCase.kt
│       ├── ResetPasswordUseCase.kt
│       └── UpdateProfileUseCase.kt
└── ui/
    ├── SignInScreen.kt
    ├── SignUpScreen.kt
    ├── ForgotPasswordScreen.kt
    ├── ProfileSetupScreen.kt
    └── AuthViewModel.kt
```

### Key Code Patterns

```kotlin
// TokenManager.kt - Secure token storage
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("token_timestamp", System.currentTimeMillis())
            .apply()
    }
    
    fun getAccessToken(): String? = encryptedPrefs.getString("access_token", null)
    
    fun clearTokens() {
        encryptedPrefs.edit().clear().apply()
    }
}

// AuthRepository.kt
class AuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val tokenManager: TokenManager,
    private val userDao: UserDao
) {
    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val response = supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            tokenManager.saveTokens(
                response.accessToken,
                response.refreshToken ?: ""
            )
            val user = fetchUserProfile(response.user?.id ?: "")
            userDao.upsert(user.toEntity())
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(mapAuthException(e))
        }
    }
}
```

---

## Data Model

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    @ColumnInfo(name = "business_name") val businessName: String,
    val phone: String,
    val address: String,
    @ColumnInfo(name = "home_latitude") val homeLatitude: Double?,
    @ColumnInfo(name = "home_longitude") val homeLongitude: Double?,
    @ColumnInfo(name = "service_radius_miles") val serviceRadiusMiles: Int = 50,
    @ColumnInfo(name = "default_duration_minutes") val defaultDurationMinutes: Int = 45,
    @ColumnInfo(name = "default_cycle_weeks") val defaultCycleWeeks: Int = 6,
    @ColumnInfo(name = "working_hours") val workingHours: String, // JSON
    @ColumnInfo(name = "payment_preferences") val paymentPreferences: String, // JSON
    @ColumnInfo(name = "subscription_tier") val subscriptionTier: String = "free",
    @ColumnInfo(name = "profile_photo_url") val profilePhotoUrl: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant
)
```

---

## Security Considerations

### Threat Model
| Threat | Impact | Likelihood | Mitigation |
|--------|--------|------------|------------|
| Credential stuffing | High | Medium | Rate limiting, CAPTCHA |
| Token theft | High | Low | Encrypted storage, short expiry |
| Session hijacking | High | Low | Token rotation, device binding |
| Phishing | Medium | Medium | Email verification |
| Brute force | Medium | Medium | Account lockout |

### Security Controls
1. **Input Validation**: Email format, password complexity
2. **Authentication**: Argon2id hashing, JWT RS256
3. **Session**: 30-day timeout, force logout on password change
4. **Network**: Certificate pinning, TLS 1.3

---

## Testing Requirements

### Unit Tests
```kotlin
class AuthViewModelTest {
    @Test
    fun `signIn with valid credentials updates state to success`() = runTest {
        coEvery { signInUseCase(email, password) } returns Result.success(user)
        viewModel.signIn(email, password)
        assertEquals(AuthUiState.Success(user), viewModel.uiState.first())
    }
    
    @Test
    fun `email validation rejects invalid formats`() {
        assertFalse(viewModel.isValidEmail("notanemail"))
        assertTrue(viewModel.isValidEmail("valid@email.com"))
    }
    
    @Test
    fun `password validation enforces requirements`() {
        assertFalse(viewModel.isValidPassword("short"))
        assertTrue(viewModel.isValidPassword("ValidPass123"))
    }
}
```

### Integration Tests
```kotlin
@HiltAndroidTest
class AuthRepositoryTest {
    @Test
    fun signIn_savesUserToLocalDatabase() = runTest {
        val result = authRepository.signIn(email, password)
        assertTrue(result.isSuccess)
        assertNotNull(userDao.getUser(result.getOrThrow().id))
    }
}
```

### E2E Tests (Maestro)
```yaml
appId: com.hoofdirect.app
---
- launchApp
- tapOn: "Sign Up"
- tapOn: { id: "email_input" }
- inputText: "test@example.com"
- tapOn: { id: "password_input" }
- inputText: "TestPass123!"
- tapOn: { id: "terms_checkbox" }
- tapOn: "Create Account"
- assertVisible: "Check your email"
```

---

## AI-Assisted Development Guidelines

### Code Generation Guardrails
1. **NEVER** log passwords, even in debug builds
2. **NEVER** store passwords in SharedPreferences
3. **ALWAYS** use EncryptedSharedPreferences for tokens
4. **NEVER** hardcode API keys or secrets
5. **ALWAYS** validate inputs before API calls
6. **ALWAYS** catch and map exceptions to user-friendly messages

### Review Checklist
- [ ] No hardcoded credentials
- [ ] Passwords never logged
- [ ] Tokens in encrypted storage
- [ ] All inputs validated
- [ ] Error messages don't leak info
- [ ] HTTPS only
- [ ] Proper exception handling

---

## Acceptance Criteria

| ID | Criteria | Verification |
|----|----------|--------------|
| AC-001-01 | User can create account with valid email/password | E2E test |
| AC-001-02 | Email verification link works | Manual test |
| AC-001-03 | User can sign in with correct credentials | Unit + E2E |
| AC-001-04 | Invalid credentials show appropriate error | Unit test |
| AC-001-05 | Password reset email works | Manual test |
| AC-001-06 | Profile persists after app restart | Integration test |
| AC-001-07 | Home address is geocoded | Integration test |
| AC-001-08 | Session persists across restarts | Integration test |
| AC-001-09 | Biometric login works | Manual test |
| AC-001-10 | Accessibility requirements met | Audit |

---

## Dependencies

| Dependency | Type | Status |
|------------|------|--------|
| Supabase project | Infrastructure | Required |
| Supabase Auth | Infrastructure | Required |
| Google Places API | External | Required |
| EncryptedSharedPreferences | Library | Available |
| Biometric library | Library | Available |

---

## Out of Scope
- Social login (Google, Apple)
- Two-factor authentication
- Multi-language support
- Password-less login (magic link)
- Account deletion (covered in PRD-019)
