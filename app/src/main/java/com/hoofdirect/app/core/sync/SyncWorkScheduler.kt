package com.hoofdirect.app.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG_PERIODIC)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateWork = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag(SyncWorker.TAG_IMMEDIATE)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueue(immediateWork)
    }

    fun cancelAllSync() {
        workManager.cancelAllWorkByTag(SyncWorker.TAG_PERIODIC)
        workManager.cancelAllWorkByTag(SyncWorker.TAG_IMMEDIATE)
    }

    fun observeSyncStatus(): Flow<SyncStatusState> {
        return workManager.getWorkInfosByTagFlow(SyncWorker.TAG_IMMEDIATE)
            .map { workInfos ->
                when {
                    workInfos.any { it.state == WorkInfo.State.RUNNING } ->
                        SyncStatusState.SYNCING
                    workInfos.any { it.state == WorkInfo.State.ENQUEUED } ->
                        SyncStatusState.PENDING
                    workInfos.any { it.state == WorkInfo.State.FAILED } ->
                        SyncStatusState.FAILED
                    else ->
                        SyncStatusState.IDLE
                }
            }
    }
}

enum class SyncStatusState {
    IDLE,
    PENDING,
    SYNCING,
    FAILED
}
