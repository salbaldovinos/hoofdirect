package com.hoofdirect.app.core.di

import android.content.Context
import com.hoofdirect.app.feature.auth.data.AuthRepository
import com.hoofdirect.app.feature.auth.data.AuthRepositoryImpl
import com.hoofdirect.app.feature.auth.data.BiometricManager
import com.hoofdirect.app.feature.auth.data.TokenManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AuthProvidesModule {

    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context
    ): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideBiometricManager(
        @ApplicationContext context: Context
    ): BiometricManager {
        return BiometricManager(context)
    }
}
