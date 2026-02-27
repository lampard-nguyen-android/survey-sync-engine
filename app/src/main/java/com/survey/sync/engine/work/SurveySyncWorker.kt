package com.survey.sync.engine.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.survey.sync.engine.domain.usecase.BatchSyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker for background sync of pending surveys.
 *
 * Addresses Scenario 1 & 2:
 * - Syncs pending surveys when connectivity is available
 * - Handles partial failures (successful surveys not re-uploaded)
 * - Returns detailed results about successes/failures
 * - Uses exponential backoff for retries
 */
@HiltWorker
class SurveySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val batchSyncUseCase: BatchSyncUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Execute batch sync
            val syncResult = batchSyncUseCase()

            syncResult.fold(
                onSuccess = { batchResult ->
                    // Create output data with sync results
                    val outputData = workDataOf(
                        KEY_TOTAL_SURVEYS to batchResult.totalSurveys,
                        KEY_SUCCESS_COUNT to batchResult.successCount,
                        KEY_FAILURE_COUNT to batchResult.failureCount,
                        KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
                    )

                    // If all surveys synced successfully, return success
                    // If some failed, return success but with output data showing failures
                    // The surveys that failed will remain PENDING and be retried later
                    if (batchResult.failureCount > 0) {
                        // Partial failure - some succeeded, some failed
                        // Return success because successful surveys were processed
                        // Failed surveys remain PENDING for next sync
                        Result.success(outputData)
                    } else {
                        // Complete success
                        Result.success(outputData)
                    }
                },
                onFailure = { error ->
                    // Complete failure - retry with backoff
                    val errorData = workDataOf(
                        KEY_ERROR_MESSAGE to (error.message ?: "Unknown error"),
                        KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
                    )

                    // Retry if we haven't exceeded attempt limit
                    if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure(errorData)
                    }
                }
            )
        } catch (e: Exception) {
            // Unexpected error - retry with backoff
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to (e.message ?: "Unexpected error"),
                        KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
                    )
                )
            }
        }
    }

    companion object {
        // Output data keys
        const val KEY_TOTAL_SURVEYS = "total_surveys"
        const val KEY_SUCCESS_COUNT = "success_count"
        const val KEY_FAILURE_COUNT = "failure_count"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_SYNC_TIMESTAMP = "sync_timestamp"

        // Worker configuration
        const val WORKER_NAME = "survey_sync_worker"
        const val MAX_RETRY_ATTEMPTS = 3

        // Work tags
        const val TAG_SYNC = "sync"
        const val TAG_PERIODIC = "periodic_sync"
        const val TAG_MANUAL = "manual_sync"
    }
}
