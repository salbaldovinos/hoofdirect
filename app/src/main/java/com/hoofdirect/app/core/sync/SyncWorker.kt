package com.hoofdirect.app.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoofdirect.app.core.network.NetworkMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncService: SyncService,
    private val networkMonitor: NetworkMonitor
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "sync_worker"
        const val TAG_PERIODIC = "sync_periodic"
        const val TAG_IMMEDIATE = "sync_immediate"

        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_COUNT = 5
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker starting, attempt: $runAttemptCount")

        // Check network availability
        if (!networkMonitor.isCurrentlyOnline()) {
            Log.d(TAG, "No network, retrying later")
            return Result.retry()
        }

        return try {
            val syncResult = syncService.performSync()

            if (syncResult.isSuccess) {
                val result = syncResult.getOrNull()!!
                Log.d(TAG, "Sync successful: pushed=${result.pushedCount}, pulled=${result.pulledCount}")
                Result.success()
            } else {
                val error = syncResult.exceptionOrNull()
                Log.e(TAG, "Sync failed", error)

                if (runAttemptCount < MAX_RETRY_COUNT) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker exception", e)

            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
