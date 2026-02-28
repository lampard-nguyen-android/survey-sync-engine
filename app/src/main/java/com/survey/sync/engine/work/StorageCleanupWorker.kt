package com.survey.sync.engine.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.survey.sync.engine.data.manager.DeviceResourceManager
import com.survey.sync.engine.data.util.StorageConfig
import com.survey.sync.engine.domain.usecase.StorageManagementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager worker for periodic storage cleanup.
 *
 * Purpose:
 * - Runs daily at 2 AM to proactively manage storage
 * - Deletes old synced media attachments using FIFO strategy
 * - Prevents storage from reaching critical levels
 * - Conservative cleanup approach for scheduled maintenance
 *
 * Cleanup Strategy:
 * - SCHEDULED cleanup: Delete synced attachments older than 30 days (conservative)
 * - Only runs if storage is getting low or on forced schedule
 * - Never deletes PENDING (unsynced) attachments
 * - Uses multi-tier cleanup based on storage severity
 *
 * Device-Aware Execution:
 * - Runs when battery not low (prevents draining battery)
 * - Runs during device idle time when possible (minimal user impact)
 * - No network required (local operation)
 * - Reports detailed metrics for monitoring
 */
@HiltWorker
class StorageCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val storageManagementUseCase: StorageManagementUseCase,
    private val deviceResourceManager: DeviceResourceManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.i("StorageCleanupWorker: Starting scheduled storage cleanup")

            // Get current storage status
            val storageStatus = deviceResourceManager.currentResources.storage

            Timber.d(
                "StorageCleanupWorker: Storage status - " +
                        "Available: ${storageStatus.availableMB} MB, " +
                        "Total: ${storageStatus.totalBytes / (1024 * 1024)} MB, " +
                        "Usage: ${storageStatus.usagePercentage}%"
            )

            // Record start time for execution metrics
            val startTime = System.currentTimeMillis()

            // Perform storage cleanup with scheduled maintenance reason
            // Note: The usecase will determine if cleanup is actually needed
            val cleanupResult = storageManagementUseCase(
                storageStatus = storageStatus,
                force = inputData.getBoolean(KEY_FORCE_CLEANUP, false)
            )

            val executionTime = System.currentTimeMillis() - startTime

            cleanupResult.handle(
                onSuccess = { result ->
                    Timber.i(
                        "StorageCleanupWorker: Cleanup completed - " +
                                "Deleted: ${result.deletedCount} attachments, " +
                                "Freed: ${result.freedMB} MB, " +
                                "Reason: ${result.reason}, " +
                                "Duration: ${executionTime}ms"
                    )

                    // Get storage status after cleanup
                    val afterStorage = deviceResourceManager.currentResources.storage

                    // Create detailed output data for monitoring
                    val outputData = workDataOf(
                        KEY_DELETED_COUNT to result.deletedCount,
                        KEY_FREED_MB to result.freedMB,
                        KEY_CLEANUP_REASON to result.reason.name,
                        KEY_BEFORE_AVAILABLE_MB to storageStatus.availableMB,
                        KEY_AFTER_AVAILABLE_MB to afterStorage.availableMB,
                        KEY_EXECUTION_TIME_MS to executionTime,
                        KEY_TIMESTAMP to System.currentTimeMillis(),
                        KEY_SUCCESS to true
                    )

                    // Log cleanup summary for analytics
                    if (result.deletedCount > 0) {
                        Timber.i(result.getFormattedSummary())
                    } else {
                        Timber.d("StorageCleanupWorker: No cleanup needed - Storage OK")
                    }

                    // Always return success - cleanup is best-effort
                    // Even if nothing was deleted, the check was successful
                    Result.success(outputData)
                },
                onError = { error ->
                    // Cleanup failed - log error and retry
                    Timber.e("StorageCleanupWorker: Cleanup failed - ${error.errorMessage}")

                    val errorData = workDataOf(
                        KEY_ERROR_MESSAGE to error.errorMessage,
                        KEY_TIMESTAMP to System.currentTimeMillis(),
                        KEY_SUCCESS to false,
                        KEY_EXECUTION_TIME_MS to executionTime
                    )

                    // Retry if we haven't exceeded attempt limit
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Timber.w("StorageCleanupWorker: Retrying (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                        Result.retry()
                    } else {
                        Timber.e("StorageCleanupWorker: Max retries exceeded, marking as failed")
                        Result.failure(errorData)
                    }
                }
            )

        } catch (e: Exception) {
            // Unexpected error - log and retry
            Timber.e(e, "StorageCleanupWorker: Unexpected error during cleanup")

            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Timber.w("StorageCleanupWorker: Retrying after exception (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                val errorData = workDataOf(
                    KEY_ERROR_MESSAGE to (e.message ?: "Unexpected error"),
                    KEY_TIMESTAMP to System.currentTimeMillis(),
                    KEY_SUCCESS to false
                )
                Timber.e("StorageCleanupWorker: Max retries exceeded after exception")
                Result.failure(errorData)
            }
        }
    }

    companion object {
        // Input data keys
        const val KEY_FORCE_CLEANUP = "force_cleanup" // Force cleanup even if storage OK

        // Output data keys
        const val KEY_DELETED_COUNT = "deleted_count"
        const val KEY_FREED_MB = "freed_mb"
        const val KEY_CLEANUP_REASON = "cleanup_reason"
        const val KEY_BEFORE_AVAILABLE_MB = "before_available_mb"
        const val KEY_AFTER_AVAILABLE_MB = "after_available_mb"
        const val KEY_EXECUTION_TIME_MS = "execution_time_ms"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_SUCCESS = "success"
        const val KEY_ERROR_MESSAGE = "error_message"

        // Worker configuration
        const val WORKER_NAME = StorageConfig.WORKER_NAME_STORAGE_CLEANUP
        const val MAX_RETRY_ATTEMPTS = 2 // Lower retry limit (cleanup is best-effort)

        // Work tags
        const val TAG_CLEANUP = StorageConfig.WORKER_TAG_STORAGE_CLEANUP
        const val TAG_MAINTENANCE = StorageConfig.WORKER_TAG_MAINTENANCE
        const val TAG_PERIODIC = "periodic_cleanup"
    }
}
