package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.Survey
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
     * Result for batch sync operation.
     */
    data class BatchSyncResult(
        val totalSurveys: Int,
        val successCount: Int,
        val failureCount: Int,
        val surveyResults: Map<String, SurveyResult>
    )

    /**
     * Individual survey sync result.
     */
    data class SurveyResult(
        val surveyId: String,
        val isSuccess: Boolean,
        val mediaUploadSuccessCount: Int = 0,
        val mediaUploadFailureCount: Int = 0,
        val totalMediaCount: Int = 0,
        val errorMessage: String? = null
    )

    /**
     * Sync all pending surveys.
     * Returns detailed results for each survey.
     */
    suspend operator fun invoke(): DomainResult<DomainError, BatchSyncResult> {
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
                        surveyResults = emptyMap()
                    )
                )
            }

            // Sync each survey individually
            val results = mutableMapOf<String, SurveyResult>()
            var successCount = 0
            var failureCount = 0

            pendingSurveys.forEach { survey ->
                val result = syncSingleSurvey(survey)
                results[survey.surveyId] = result

                if (result.isSuccess) {
                    successCount++
                } else {
                    failureCount++
                }
            }

            DomainResult.success(
                BatchSyncResult(
                    totalSurveys = pendingSurveys.size,
                    successCount = successCount,
                    failureCount = failureCount,
                    surveyResults = results
                )
            )
        } catch (e: Exception) {
            DomainResult.error(DomainError.UnexpectedError(e.message ?: "Unknown error"))
        }
    }

    /**
     * Sync a single survey with its media attachments.
     * Handles partial media upload failures gracefully.
     */
    private suspend fun syncSingleSurvey(survey: Survey): SurveyResult {
        return try {
            // Get media attachments for this survey
            val attachments = getMediaAttachmentsUseCase(survey.surveyId).getOrNull() ?: emptyList()

            // Upload survey with attachments
            val uploadResult = uploadSurveyUseCase(
                survey = survey,
                mediaAttachments = attachments,
                cleanupAttachments = true
            )

            uploadResult.handle(
                onError = { error ->
                    SurveyResult(
                        surveyId = survey.surveyId,
                        isSuccess = false,
                        errorMessage = error.toString()
                    )
                },
                onSuccess = { result ->
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
            SurveyResult(
                surveyId = survey.surveyId,
                isSuccess = false,
                errorMessage = e.message ?: "Unexpected error"
            )
        }
    }
}
