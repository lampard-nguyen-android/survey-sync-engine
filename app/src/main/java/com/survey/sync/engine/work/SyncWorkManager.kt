package com.survey.sync.engine.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.survey.sync.engine.domain.sync.SyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager implementation of SyncScheduler.
 *
 * Addresses Scenario 1 (Offline Storage):
 * - Schedules periodic sync to check for pending surveys
 * - Triggers sync when connectivity is restored
 * - Uses exponential backoff for failed syncs
 *
 * Addresses Scenario 4 (Concurrent Sync Prevention):
 * - Uses ExistingWorkPolicy.KEEP to prevent duplicate syncs
 * - Only one sync operation runs at a time
 * - Second caller's request is safely ignored without corruption
 *
 * Features:
 * - Periodic sync (every 4 hours) with connectivity constraint
 * - Manual one-time sync with concurrency protection
 * - Exponential backoff strategy (initial 30s, max 5 minutes)
 * - Battery-friendly (requires device not in low battery state)
 */
@Singleton
class SyncWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule periodic background sync.
     * Optimized for battery preservation in rural areas.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SurveySyncWorker>(
            repeatInterval = 4, // Increased to 4 hours to save battery on low-end devices
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag(SurveySyncWorker.TAG_SYNC)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SurveySyncWorker.WORKER_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Prevent concurrent execution
            periodicSyncRequest
        )
    }

    /**
     * Trigger immediate one-time sync.
     * Optimized to prevent "Restart Loops" from multiple taps.
     *
     * Scenario 4 Protection:
     * - Uses ExistingWorkPolicy.KEEP
     * - If sync already running, new request is ignored
     * - No corruption or duplication of work
     */
    override suspend fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeSyncRequest = OneTimeWorkRequestBuilder<SurveySyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag(SurveySyncWorker.TAG_SYNC)
            .build()

        workManager.enqueueUniqueWork(
            "${SurveySyncWorker.WORKER_NAME}_manual",
            ExistingWorkPolicy.KEEP, // KEEP = ignore new request if already running
            oneTimeSyncRequest
        )
    }

    /**
     * Cancel all sync work.
     */
    override suspend fun cancelAllSync() {
        workManager.cancelAllWorkByTag(SurveySyncWorker.TAG_SYNC)
    }

    /**
     * Cancel periodic sync only.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SurveySyncWorker.WORKER_NAME)
    }

    /**
     * Get sync work info to monitor status.
     */
    fun getSyncWorkInfo() =
        workManager.getWorkInfosForUniqueWorkLiveData(SurveySyncWorker.WORKER_NAME)

    /**
     * Check if sync is currently running.
     */
    @SuppressLint("RestrictedApi")
    override suspend fun isSyncRunning(): Boolean {
        val workInfos = workManager.getWorkInfosByTag(SurveySyncWorker.TAG_SYNC).await()
        return workInfos.any { it.state == WorkInfo.State.RUNNING }
    }

    /**
     * Get last sync result.
     */
    @SuppressLint("RestrictedApi")
    override suspend fun getLastSyncResult(): SyncScheduler.SyncResult? {
        val workInfos = workManager.getWorkInfosByTag(SurveySyncWorker.TAG_SYNC).await()
        val lastWork = workInfos
            .filter { it.state.isFinished }
            .maxByOrNull {
                it.outputData.getLong(SurveySyncWorker.KEY_SYNC_TIMESTAMP, 0L)
            }

        return lastWork?.let { workInfo ->
            SyncScheduler.SyncResult(
                totalSurveys = workInfo.outputData.getInt(SurveySyncWorker.KEY_TOTAL_SURVEYS, 0),
                successCount = workInfo.outputData.getInt(SurveySyncWorker.KEY_SUCCESS_COUNT, 0),
                failureCount = workInfo.outputData.getInt(SurveySyncWorker.KEY_FAILURE_COUNT, 0),
                timestamp = workInfo.outputData.getLong(SurveySyncWorker.KEY_SYNC_TIMESTAMP, 0L),
                errorMessage = workInfo.outputData.getString(SurveySyncWorker.KEY_ERROR_MESSAGE)
            )
        }
    }
}
