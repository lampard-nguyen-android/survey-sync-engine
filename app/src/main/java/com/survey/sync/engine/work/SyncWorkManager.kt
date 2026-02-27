package com.survey.sync.engine.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for scheduling survey sync work.
 *
 * Addresses Scenario 1 (Offline Storage):
 * - Schedules periodic sync to check for pending surveys
 * - Triggers sync when connectivity is restored
 * - Uses exponential backoff for failed syncs
 *
 * Features:
 * - Periodic sync (every 2 hours) with connectivity constraint
 * - Manual one-time sync
 * - Exponential backoff strategy (initial 30s, max 5 minutes)
 * - Battery-friendly (requires device not in low battery state)
 */
@Singleton
class SyncWorkManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule periodic background sync.
     * Runs every 2 hours when connected to network.
     * Addresses Scenario 1: Automatically syncs when agent reaches connectivity.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only run when connected
            .setRequiresBatteryNotLow(true) // Don't drain battery when low
            .build()

        val periodicSyncRequest = PeriodicWorkRequestBuilder<SurveySyncWorker>(
            repeatInterval = 2, // Every 2 hours
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 30, // Can run within 30 minute window
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL, // Exponential backoff
                30, // Initial backoff delay: 30 seconds
                TimeUnit.SECONDS
            )
            .addTag(SurveySyncWorker.TAG_SYNC)
            .addTag(SurveySyncWorker.TAG_PERIODIC)
            .build()

        // Use KEEP to avoid rescheduling if already scheduled
        workManager.enqueueUniquePeriodicWork(
            SurveySyncWorker.WORKER_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncRequest
        )
    }

    /**
     * Trigger immediate one-time sync.
     * Used for manual sync from UI or when new survey is created.
     */
    fun triggerImmediateSync(): Operation {
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
            .addTag(SurveySyncWorker.TAG_MANUAL)
            .build()

        return workManager.enqueueUniqueWork(
            "${SurveySyncWorker.WORKER_NAME}_manual",
            ExistingWorkPolicy.REPLACE, // Replace any existing manual sync
            oneTimeSyncRequest
        )
    }

    /**
     * Schedule sync with custom delay.
     * Useful for retrying after a failed sync.
     */
    fun scheduleSyncWithDelay(delayMinutes: Long): Operation {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val delayedSyncRequest = OneTimeWorkRequestBuilder<SurveySyncWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .addTag(SurveySyncWorker.TAG_SYNC)
            .build()

        return workManager.enqueueUniqueWork(
            "${SurveySyncWorker.WORKER_NAME}_delayed",
            ExistingWorkPolicy.REPLACE,
            delayedSyncRequest
        )
    }

    /**
     * Cancel all sync work.
     */
    fun cancelAllSync() {
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
    suspend fun isSyncRunning(): Boolean {
        val workInfos = workManager.getWorkInfosByTag(SurveySyncWorker.TAG_SYNC).await()
        return workInfos.any { it.state == WorkInfo.State.RUNNING }
    }

    /**
     * Get last sync result.
     */
    suspend fun getLastSyncResult(): SyncResult? {
        val workInfos = workManager.getWorkInfosByTag(SurveySyncWorker.TAG_SYNC).await()
        val lastWork = workInfos
            .filter { it.state.isFinished }
            .maxByOrNull {
                it.outputData.getLong(SurveySyncWorker.KEY_SYNC_TIMESTAMP, 0L)
            }

        return lastWork?.let { workInfo ->
            SyncResult(
                totalSurveys = workInfo.outputData.getInt(SurveySyncWorker.KEY_TOTAL_SURVEYS, 0),
                successCount = workInfo.outputData.getInt(SurveySyncWorker.KEY_SUCCESS_COUNT, 0),
                failureCount = workInfo.outputData.getInt(SurveySyncWorker.KEY_FAILURE_COUNT, 0),
                timestamp = workInfo.outputData.getLong(SurveySyncWorker.KEY_SYNC_TIMESTAMP, 0L),
                errorMessage = workInfo.outputData.getString(SurveySyncWorker.KEY_ERROR_MESSAGE)
            )
        }
    }

    /**
     * Result data from sync work.
     */
    data class SyncResult(
        val totalSurveys: Int,
        val successCount: Int,
        val failureCount: Int,
        val timestamp: Long,
        val errorMessage: String?
    )
}
