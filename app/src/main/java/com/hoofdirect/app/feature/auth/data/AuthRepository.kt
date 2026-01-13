package com.hoofdirect.app.feature.auth.data

import com.hoofdirect.app.feature.auth.domain.model.AuthError
import com.hoofdirect.app.feature.auth.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    val isAuthenticated: Flow<Boolean>

    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit>
    suspend fun resendVerificationEmail(): Result<Unit>
    suspend fun refreshSession(): Result<Unit>
    suspend fun updateProfile(user: User): Result<User>

    fun hasValidSession(): Boolean
}
