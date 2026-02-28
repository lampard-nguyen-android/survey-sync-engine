package com.survey.sync.engine.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.survey.sync.engine.data.manager.ConnectivityManager
import com.survey.sync.engine.domain.network.NetworkHealthTracker
import com.survey.sync.engine.domain.network.NetworkStatus
import com.survey.sync.engine.domain.usecase.BatchSyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * WorkManager worker for background sync of pending surveys.
 *
 * Addresses Scenario 1 & 2 + Network Quality Monitoring:
 * - Syncs pending surveys when connectivity is available
 * - Monitors network quality in real-time during sync
 * - Implements circuit breaker to detect network-down scenarios
 * - Stops early when network likely down (conserves battery & data)
 * - Handles partial failures (successful surveys not re-uploaded)
 * - Returns detailed results about successes/failures/skipped
 * - Uses exponential backoff for retries
 */
@HiltWorker
class SurveySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val batchSyncUseCase: BatchSyncUseCase,
    private val connectivityManager: ConnectivityManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Check network status before starting
            val networkStatus = connectivityManager.networkStatusFlow.first()

            if (networkStatus == NetworkStatus.Unavailable) {
                Timber.w("SurveySyncWorker: Network unavailable, skipping sync")
                return Result.retry() // Retry when network becomes available
            }

            Timber.d("SurveySyncWorker: Starting sync with network status: $networkStatus")

            // Create network health tracker for this sync session
            val networkHealthTracker = NetworkHealthTracker(
                consecutiveFailureThreshold = CIRCUIT_BREAKER_THRESHOLD
            )

            // Execute batch sync with network health monitoring
            val syncResult = batchSyncUseCase(networkHealthTracker, networkStatus)

            syncResult.fold(
                onSuccess = { batchResult ->
                    Timber.i(
                        "SurveySyncWorker: Sync completed. " +
                                "Success: ${batchResult.successCount}, " +
                                "Failed: ${batchResult.failureCount}, " +
                                "Skipped: ${batchResult.skippedCount}, " +
                                "Stop reason: ${batchResult.stopReason}, " +
                                "Network health: ${batchResult.networkHealthStatus}"
                    )

                    // Create enhanced output data with detailed sync results
                    val outputData = workDataOf(
                        KEY_TOTAL_SURVEYS to batchResult.totalSurveys,
                        KEY_SUCCESS_COUNT to batchResult.successCount,
                        KEY_FAILURE_COUNT to batchResult.failureCount,
                        KEY_SKIPPED_COUNT to batchResult.skippedCount,
                        KEY_STOP_REASON to batchResult.stopReason.name,
                        KEY_NETWORK_HEALTH to batchResult.networkHealthStatus.name,
                        KEY_SUCCEEDED_IDS to batchResult.succeededSurveyIds.joinToString(","),
                        KEY_FAILED_IDS to batchResult.failedSurveyIds.joinToString(","),
                        KEY_SKIPPED_IDS to batchResult.skippedSurveyIds.joinToString(","),
                        KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
                    )

                    // Determine result based on stop reason
                    when (batchResult.stopReason) {
                        BatchSyncUseCase.StopReason.NETWORK_DOWN -> {
                            // Network likely down - retry later to upload remaining surveys
                            Timber.w("SurveySyncWorker: Network likely down, will retry later")
                            Result.retry()
                        }

                        BatchSyncUseCase.StopReason.NETWORK_UNAVAILABLE -> {
                            // Network became unavailable - retry when available
                            Timber.w("SurveySyncWorker: Network unavailable, will retry later")
                            Result.retry()
                        }

                        else -> {
                            // Completed or partial success
                            // Return success even with partial failures
                            // Failed surveys remain PENDING for next sync
                            Result.success(outputData)
                        }
                    }
                },
                onFailure = { error ->
                    // Complete failure - retry with backoff
                    Timber.e("SurveySyncWorker: Sync failed with error: $error")

                    val errorData = workDataOf(
                        KEY_ERROR_MESSAGE to (error.errorMessage),
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
            Timber.e(e, "SurveySyncWorker: Unexpected error during sync")

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
        const val KEY_SKIPPED_COUNT = "skipped_count"
        const val KEY_STOP_REASON = "stop_reason"
        const val KEY_NETWORK_HEALTH = "network_health"
        const val KEY_SUCCEEDED_IDS = "succeeded_ids"
        const val KEY_FAILED_IDS = "failed_ids"
        const val KEY_SKIPPED_IDS = "skipped_ids"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_SYNC_TIMESTAMP = "sync_timestamp"

        // Worker configuration
        const val WORKER_NAME = "survey_sync_worker"
        const val MAX_RETRY_ATTEMPTS = 3
        const val CIRCUIT_BREAKER_THRESHOLD = 3 // Stop after 3 consecutive network failures

        // Work tags
        const val TAG_SYNC = "sync"
        const val TAG_PERIODIC = "periodic_sync"
        const val TAG_MANUAL = "manual_sync"
    }
}
