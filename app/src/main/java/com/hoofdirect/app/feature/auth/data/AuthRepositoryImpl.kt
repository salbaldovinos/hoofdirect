package com.hoofdirect.app.feature.auth.data

import com.hoofdirect.app.core.database.dao.UserDao
import com.hoofdirect.app.core.network.NetworkMonitor
import com.hoofdirect.app.feature.auth.domain.model.AuthError
import com.hoofdirect.app.feature.auth.domain.model.User
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,
    val business_name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val home_latitude: Double? = null,
    val home_longitude: Double? = null,
    val service_radius_miles: Int = 50,
    val default_duration_minutes: Int = 45,
    val default_cycle_weeks: Int = 6,
    val subscription_tier: String = "free",
    val profile_photo_url: String? = null,
    val profile_completed: Boolean = false,
    val updated_at: String? = null
) {
    companion object {
        fun fromUser(user: User): ProfileDto = ProfileDto(
            id = user.id,
            email = user.email,
            business_name = user.businessName,
            phone = user.phone,
            address = user.address,
            home_latitude = user.homeLatitude,
            home_longitude = user.homeLongitude,
            service_radius_miles = user.serviceRadiusMiles,
            default_duration_minutes = user.defaultDurationMinutes,
            default_cycle_weeks = user.defaultCycleWeeks,
            subscription_tier = user.subscriptionTier,
            profile_photo_url = user.profilePhotoUrl,
            profile_completed = user.profileCompleted,
            updated_at = Instant.now().toString()
        )
    }
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val userDao: UserDao,
    private val tokenManager: TokenManager,
    private val networkMonitor: NetworkMonitor
) : AuthRepository {

    private val auth: Auth get() = supabaseClient.auth

    override val currentUser: Flow<User?> = userDao.getCurrentUser()
        .map { entity -> entity?.let { User.fromEntity(it) } }

    override val isAuthenticated: Flow<Boolean> = currentUser.map { it != null }

    override suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                // Attempt offline login with cached credentials
                val savedEmail = tokenManager.getSavedEmail()
                val savedPassword = tokenManager.getSavedPassword()
                if (savedEmail == email && savedPassword == password) {
                    val userId = tokenManager.getUserId()
                    if (userId != null) {
                        val cachedUser = userDao.getUserByIdOnce(userId)
                        if (cachedUser != null) {
                            return Result.success(User.fromEntity(cachedUser))
                        }
                    }
                }
                return Result.failure(AuthError.NetworkError)
            }

            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val session = auth.currentSessionOrNull()
            val userInfo = auth.currentUserOrNull()

            if (session != null && userInfo != null) {
                // Save tokens
                tokenManager.saveTokens(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken ?: "",
                    expiresIn = session.expiresIn
                )
                tokenManager.saveUserId(userInfo.id)

                // Save credentials for biometric login if remember me is enabled
                if (tokenManager.isRememberMeEnabled()) {
                    tokenManager.saveCredentialsForBiometric(email, password)
                }

                // Clear any other user's cached data (if a different user signs in)
                // This preserves the current user's cached profile data
                userDao.deleteUsersExcept(userInfo.id)

                // Try to load existing profile from Supabase or local cache
                val user = loadOrCreateUser(userInfo)
                userDao.insertUser(user.toEntity())

                Result.success(user)
            } else {
                Result.failure(AuthError.InvalidCredentials)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun signUp(email: String, password: String): Result<User> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.failure(AuthError.NetworkError)
            }

            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val session = auth.currentSessionOrNull()
            val userInfo = auth.currentUserOrNull()

            if (userInfo != null) {
                // Save tokens if session exists
                if (session != null) {
                    tokenManager.saveTokens(
                        accessToken = session.accessToken,
                        refreshToken = session.refreshToken ?: "",
                        expiresIn = session.expiresIn
                    )
                }
                tokenManager.saveUserId(userInfo.id)

                // Create local user (email not verified yet)
                val user = User(
                    id = userInfo.id,
                    email = email,
                    emailVerified = false
                )
                userDao.insertUser(user.toEntity())

                Result.success(user)
            } else {
                Result.failure(AuthError.Unknown("Sign up failed"))
            }
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            if (networkMonitor.isCurrentlyOnline()) {
                auth.signOut()
            }
            // Only clear auth tokens, NOT user data
            // User data is preserved so if the same user signs back in,
            // their profile data is still cached locally
            tokenManager.clearAll()
            Result.success(Unit)
        } catch (e: Exception) {
            // Still clear tokens even if remote signout fails
            tokenManager.clearAll()
            Result.success(Unit)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.failure(AuthError.NetworkError)
            }

            auth.resetPasswordForEmail(email)
            Result.success(Unit)
        } catch (e: Exception) {
            // Don't reveal if email exists or not
            Result.success(Unit)
        }
    }

    override suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.failure(AuthError.NetworkError)
            }

            auth.updateUser {
                password = newPassword
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun resendVerificationEmail(): Result<Unit> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                return Result.failure(AuthError.NetworkError)
            }

            val userInfo = auth.currentUserOrNull()
            if (userInfo != null && userInfo.email != null) {
                auth.resendEmail(OtpType.Email.SIGNUP, userInfo.email!!)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(mapException(e))
        }
    }

    override suspend fun refreshSession(): Result<Unit> {
        return try {
            if (!networkMonitor.isCurrentlyOnline()) {
                // Allow offline operation if we have valid cached session
                return if (tokenManager.getAccessToken() != null) {
                    Result.success(Unit)
                } else {
                    Result.failure(AuthError.NoSession)
                }
            }

            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrEmpty()) {
                return Result.failure(AuthError.NoSession)
            }

            auth.refreshCurrentSession()

            val session = auth.currentSessionOrNull()
            if (session != null) {
                tokenManager.saveTokens(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken ?: refreshToken,
                    expiresIn = session.expiresIn
                )
                Result.success(Unit)
            } else {
                tokenManager.clearTokens()
                Result.failure(AuthError.SessionExpired)
            }
        } catch (e: Exception) {
            tokenManager.clearTokens()
            Result.failure(AuthError.SessionExpired)
        }
    }

    override suspend fun updateProfile(user: User): Result<User> {
        return try {
            // Always save to local database first (offline-first)
            val isOnline = networkMonitor.isCurrentlyOnline()
            val entity = user.toEntity().copy(
                updatedAt = Instant.now(),
                syncStatus = if (isOnline) "SYNCED" else "PENDING_UPDATE"
            )
            userDao.updateUser(entity)

            // Sync to Supabase if online
            if (isOnline) {
                try {
                    val profileDto = ProfileDto.fromUser(user)
                    supabaseClient.postgrest["profiles"]
                        .upsert(profileDto) {
                            filter {
                                eq("id", user.id)
                            }
                        }
                } catch (e: Exception) {
                    // If Supabase sync fails, mark as pending update
                    val pendingEntity = entity.copy(syncStatus = "PENDING_UPDATE")
                    userDao.updateUser(pendingEntity)
                    // Still return success since local save worked
                }
            }

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(AuthError.Unknown(e.message ?: "Update failed"))
        }
    }

    override fun hasValidSession(): Boolean {
        return tokenManager.getAccessToken() != null && !tokenManager.isTokenExpired()
    }

    /**
     * Load existing user profile from Supabase or local cache, or create new if doesn't exist.
     * This preserves profile data for existing users during sign-in.
     */
    private suspend fun loadOrCreateUser(userInfo: UserInfo): User {
        // First, try to fetch from Supabase profiles table
        try {
            val profiles = supabaseClient.postgrest["profiles"]
                .select {
                    filter {
                        eq("id", userInfo.id)
                    }
                }
                .decodeList<ProfileDto>()

            if (profiles.isNotEmpty()) {
                val profile = profiles.first()
                return User(
                    id = profile.id,
                    email = profile.email ?: userInfo.email ?: "",
                    emailVerified = userInfo.emailConfirmedAt != null,
                    businessName = profile.business_name,
                    phone = profile.phone,
                    address = profile.address,
                    homeLatitude = profile.home_latitude,
                    homeLongitude = profile.home_longitude,
                    serviceRadiusMiles = profile.service_radius_miles,
                    defaultDurationMinutes = profile.default_duration_minutes,
                    defaultCycleWeeks = profile.default_cycle_weeks,
                    subscriptionTier = profile.subscription_tier,
                    profilePhotoUrl = profile.profile_photo_url,
                    profileCompleted = profile.profile_completed
                )
            }
        } catch (e: Exception) {
            // Supabase fetch failed, try local cache
        }

        // Fall back to local cache
        val cachedUser = userDao.getUserByIdOnce(userInfo.id)
        if (cachedUser != null) {
            // Preserve existing profile data, just update auth-related fields
            return User.fromEntity(cachedUser).copy(
                emailVerified = userInfo.emailConfirmedAt != null
            )
        }

        // No existing profile found - create new bare-bones user
        return User(
            id = userInfo.id,
            email = userInfo.email ?: "",
            emailVerified = userInfo.emailConfirmedAt != null
        )
    }

    private fun createUserFromInfo(userInfo: UserInfo): User {
        return User(
            id = userInfo.id,
            email = userInfo.email ?: "",
            emailVerified = userInfo.emailConfirmedAt != null
        )
    }

    private fun mapException(e: Exception): AuthError {
        val message = e.message?.lowercase() ?: ""
        return when {
            message.contains("invalid login credentials") ||
                    message.contains("invalid_credentials") -> AuthError.InvalidCredentials

            message.contains("email not confirmed") ||
                    message.contains("email_not_confirmed") -> AuthError.EmailNotVerified

            message.contains("user already registered") ||
                    message.contains("already exists") -> AuthError.EmailAlreadyRegistered

            message.contains("weak password") -> AuthError.WeakPassword

            message.contains("rate limit") ||
                    message.contains("too many requests") -> AuthError.RateLimited

            message.contains("network") ||
                    message.contains("timeout") ||
                    message.contains("connection") -> AuthError.NetworkError

            else -> AuthError.Unknown(e.message ?: "An error occurred")
        }
    }
}
