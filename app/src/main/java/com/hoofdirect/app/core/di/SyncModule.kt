package com.hoofdirect.app.core.di

import com.hoofdirect.app.core.database.dao.SyncQueueDao
import com.hoofdirect.app.core.sync.SyncQueueManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }
    }

    @Provides
    @Singleton
    fun provideSyncQueueManager(
        syncQueueDao: SyncQueueDao,
        json: Json
    ): SyncQueueManager {
        return SyncQueueManager(syncQueueDao, json)
    }
}
