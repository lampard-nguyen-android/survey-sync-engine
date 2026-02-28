package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.DeviceResources
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.network.HealthStatus
import com.survey.sync.engine.domain.network.NetworkHealthTracker
import com.survey.sync.engine.domain.network.NetworkStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for batch syncing multiple surveys.
 * Handles Scenario 2: Partial Failure
 * - Successfully uploaded surveys are not re-uploaded
 * - Caller knows exactly which succeeded and which failed
 * - Only failed/unattempted surveys are retried on next sync
 */
class BatchSyncUseCase @Inject constructor(
    private val repository: SurveyRepository,
    private val uploadSurveyUseCase: UploadSurveyUseCase,
    private val getMediaAttachmentsUseCase: GetMediaAttachmentsUseCase
) {
    /**
     * Reason why sync operation stopped
     */
    enum class StopReason {
        COMPLETED,              // All surveys processed successfully
        NETWORK_DOWN,           // Network likely down (circuit breaker opened)
        NETWORK_UNAVAILABLE,    // Network became unavailable during sync
        BATTERY_LOW,            // Battery too low to continue sync
        STORAGE_CRITICAL,       // Storage too low to continue
        CANCELLED               // Sync was cancelled
    }

    /**
     * Result for batch sync operation.
     */
    data class BatchSyncResult(
        val totalSurveys: Int,
        val successCount: Int,
        val failureCount: Int,
        val skippedCount: Int = 0,
        val surveyResults: Map<String, SurveyResult>,
        val succeededSurveyIds: List<String> = emptyList(),
        val failedSurveyIds: List<String> = emptyList(),
        val skippedSurveyIds: List<String> = emptyList(),
        val stopReason: StopReason = StopReason.COMPLETED,
        val networkHealthStatus: HealthStatus = HealthStatus.HEALTHY
    )

    /**
     * Individual survey sync result.
     */
    data class SurveyResult(
        val surveyId: String,
        val isSuccess: Boolean,
        val isSkipped: Boolean = false,
        val mediaUploadSuccessCount: Int = 0,
        val mediaUploadFailureCount: Int = 0,
        val totalMediaCount: Int = 0,
        val errorMessage: String? = null
    )

    /**
     * Sync all pending surveys with network health monitoring and device-aware optimizations.
     * Implements circuit breaker pattern to detect network-down scenarios and stop early.
     *
     * Device-aware optimizations:
     * - Skips media uploads if battery < 20% and not charging
     * - Skips media uploads on weak networks to conserve battery/data
     * - Skips media uploads on cellular networks (metered) to conserve data
     * - Stops sync if storage is critical (< 200 MB)
     *
     * @param networkHealthTracker Optional tracker for monitoring network health during sync.
     *                              If not provided, creates a new one (default threshold: 3 failures).
     * @param networkStatus Current network status (used to optimize media uploads on weak networks).
     * @param deviceResources Current device resources (battery, storage, network type).
     *                        Used for device-aware sync decisions.
     * @return Detailed results including succeeded, failed, and skipped surveys
     */
    suspend operator fun invoke(
        networkHealthTracker: NetworkHealthTracker = NetworkHealthTracker(),
        networkStatus: NetworkStatus? = null,
        deviceResources: DeviceResources? = null
    ): DomainResult<DomainError, BatchSyncResult> {
        return try {
            // Get all pending surveys
            val pendingSurveys = repository.getPendingSurveys().getOrElse {
                return DomainResult.error(it)
            }

            if (pendingSurveys.isEmpty()) {
                return DomainResult.success(
                    BatchSyncResult(
                        totalSurveys = 0,
                        successCount = 0,
                        failureCount = 0,
                        skippedCount = 0,
                        surveyResults = emptyMap(),
                        succeededSurveyIds = emptyList(),
                        failedSurveyIds = emptyList(),
                        skippedSurveyIds = emptyList(),
                        stopReason = StopReason.COMPLETED,
                        networkHealthStatus = networkHealthTracker.healthStatus
                    )
                )
            }

            // Sync each survey individually with network health monitoring
            val results = mutableMapOf<String, SurveyResult>()
            val succeededIds = mutableListOf<String>()
            val failedIds = mutableListOf<String>()
            val skippedIds = mutableListOf<String>()
            var successCount = 0
            var failureCount = 0
            var skippedCount = 0
            var stopReason = StopReason.COMPLETED

            for (survey in pendingSurveys) {
                // Check storage before each upload - stop if critical
                if (deviceResources != null && deviceResources.storage.isCritical) {
                    // Storage critical - stop sync to prevent device issues
                    val skippedResult = SurveyResult(
                        surveyId = survey.surveyId,
                        isSuccess = false,
                        isSkipped = true,
                        errorMessage = "Skipped: Storage critical (${deviceResources.storage.availableMB} MB free)"
                    )
                    results[survey.surveyId] = skippedResult
                    skippedIds.add(survey.surveyId)
                    skippedCount++
                    stopReason = StopReason.STORAGE_CRITICAL
                    continue
                }

                // Check network health before each upload (circuit breaker pattern)
                if (!networkHealthTracker.shouldContinueSync) {
                    // Network likely down - stop early to conserve battery and data
                    val skippedResult = SurveyResult(
                        surveyId = survey.surveyId,
                        isSuccess = false,
                        isSkipped = true,
                        errorMessage = "Skipped: Network likely down (${networkHealthTracker.healthStatus})"
                    )
                    results[survey.surveyId] = skippedResult
                    skippedIds.add(survey.surveyId)
                    skippedCount++
                    stopReason = StopReason.NETWORK_DOWN
                    continue
                }

                // Attempt to sync survey with device-aware optimizations
                val result =
                    syncSingleSurvey(survey, networkHealthTracker, networkStatus, deviceResources)
                results[survey.surveyId] = result

                when {
                    result.isSuccess -> {
                        successCount++
                        succeededIds.add(survey.surveyId)
                    }

                    result.isSkipped -> {
                        skippedCount++
                        skippedIds.add(survey.surveyId)
                    }

                    else -> {
                        failureCount++
                        failedIds.add(survey.surveyId)
                    }
                }
            }

            DomainResult.success(
                BatchSyncResult(
                    totalSurveys = pendingSurveys.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    skippedCount = skippedCount,
                    surveyResults = results,
                    succeededSurveyIds = succeededIds,
                    failedSurveyIds = failedIds,
                    skippedSurveyIds = skippedIds,
                    stopReason = stopReason,
                    networkHealthStatus = networkHealthTracker.healthStatus
                )
            )
        } catch (e: Exception) {
            DomainResult.error(DomainError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }

    /**
     * Sync a single survey with its media attachments.
     * Handles partial media upload failures gracefully.
     * Reports failures to network health tracker for circuit breaker logic.
     * Implements device-aware media upload optimizations:
     * - Skips media on weak networks
     * - Skips media on cellular (metered) networks
     * - Skips media if battery < 20% and not charging
     */
    private suspend fun syncSingleSurvey(
        survey: Survey,
        networkHealthTracker: NetworkHealthTracker,
        networkStatus: NetworkStatus?,
        deviceResources: DeviceResources?
    ): SurveyResult {
        return try {
            // Get media attachments for this survey
            val attachments = getMediaAttachmentsUseCase(survey.surveyId).getOrNull() ?: emptyList()

            // Determine if media should be skipped based on device resources
            val skipMedia = shouldSkipMedia(networkStatus, deviceResources)

            // Upload survey with attachments (or skip media based on device conditions)
            val uploadResult = uploadSurveyUseCase(
                survey = survey,
                mediaAttachments = attachments,
                cleanupAttachments = true,
                skipMedia = skipMedia
            )

            uploadResult.handle(
                onError = { error ->
                    // Report failure to network health tracker
                    val isNetworkFailure = networkHealthTracker.recordFailure(error)

                    SurveyResult(
                        surveyId = survey.surveyId,
                        isSuccess = false,
                        errorMessage = if (isNetworkFailure) {
                            "Network error: $error"
                        } else {
                            error.toString()
                        }
                    )
                },
                onSuccess = { result ->
                    // Report success to network health tracker (resets consecutive failures)
                    networkHealthTracker.recordSuccess()

                    SurveyResult(
                        surveyId = survey.surveyId,
                        isSuccess = true,
                        mediaUploadSuccessCount = result.mediaUploadSuccessCount,
                        mediaUploadFailureCount = result.mediaUploadFailureCount,
                        totalMediaCount = result.totalMediaCount
                    )
                }
            )
        } catch (e: Exception) {
            // Report unexpected errors
            networkHealthTracker.recordFailure(
                DomainError.UnexpectedError(e.message ?: "Unexpected error")
            )

            SurveyResult(
                surveyId = survey.surveyId,
                isSuccess = false,
                errorMessage = e.message ?: "Unexpected error"
            )
        }
    }

    /**
     * Determine if media uploads should be skipped based on device resources.
     * Conserves battery and data by skipping media in suboptimal conditions.
     *
     * Skips media if:
     * - Network is weak (low bandwidth)
     * - Network is cellular (metered/expensive data)
     * - Battery < 20% and not charging (conserve power)
     *
     * @param networkStatus Current network status
     * @param deviceResources Current device resources
     * @return true if media should be skipped, false otherwise
     */
    private fun shouldSkipMedia(
        networkStatus: NetworkStatus?,
        deviceResources: DeviceResources?
    ): Boolean {
        // Skip media on weak networks to conserve battery and data
        if (networkStatus == NetworkStatus.Weak) {
            return true
        }

        // If no device resources provided, use legacy behavior (only check network status)
        if (deviceResources == null) {
            return false
        }

        // Skip media on cellular networks to conserve metered data
        if (!deviceResources.network.isGoodForMediaUpload) {
            return true
        }

        // Skip media if battery is low and not charging
        if (!deviceResources.battery.isGoodForSync) {
            return true
        }

        return false
    }
}
