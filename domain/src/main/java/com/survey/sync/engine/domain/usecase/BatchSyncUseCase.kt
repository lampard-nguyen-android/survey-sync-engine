package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
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
        BATTERY_LOW,            // Battery constraints no longer met
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
     * Sync all pending surveys with network health monitoring.
     * Implements circuit breaker pattern to detect network-down scenarios and stop early.
     *
     * @param networkHealthTracker Optional tracker for monitoring network health during sync.
     *                              If not provided, creates a new one (default threshold: 3 failures).
     * @param networkStatus Current network status (used to optimize media uploads on weak networks).
     * @return Detailed results including succeeded, failed, and skipped surveys
     */
    suspend operator fun invoke(
        networkHealthTracker: NetworkHealthTracker = NetworkHealthTracker(),
        networkStatus: NetworkStatus? = null
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

                // Attempt to sync survey
                val result = syncSingleSurvey(survey, networkHealthTracker, networkStatus)
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
     * Skips media uploads on weak networks to conserve battery and data.
     */
    private suspend fun syncSingleSurvey(
        survey: Survey,
        networkHealthTracker: NetworkHealthTracker,
        networkStatus: NetworkStatus?
    ): SurveyResult {
        return try {
            // Get media attachments for this survey
            val attachments = getMediaAttachmentsUseCase(survey.surveyId).getOrNull() ?: emptyList()

            // Determine if media should be skipped based on network quality
            // Skip media on weak networks to conserve battery and data
            val skipMedia = networkStatus == NetworkStatus.Weak

            // Upload survey with attachments (or skip media if network is weak)
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
                            "Network error: ${error}"
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
}
