package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for uploading a survey to the server.
 * Handles the business logic of uploading survey data, then media attachments separately,
 * updating sync status, and cleaning up photo attachments after successful upload.
 */
class UploadSurveyUseCase @Inject constructor(
    private val repository: SurveyRepository,
    private val uploadMediaAttachmentsUseCase: UploadMediaAttachmentsUseCase
) {
    /**
     * Result of a complete survey upload including media attachments.
     */
    data class UploadSurveyResult(
        val surveyUploadResult: UploadResult,
        val mediaUploadSuccessCount: Int,
        val mediaUploadFailureCount: Int,
        val mediaSkippedCount: Int = 0,
        val totalMediaCount: Int
    )

    /**
     * Upload a survey and its media attachments to the server.
     * Process:
     * 1. Upload survey data (text answers)
     * 2. If successful, upload media attachments separately (unless skipped)
     * 3. Update sync status based on results
     * 4. Clean up local photo files after successful upload
     * 5. On failure: increment retry count; surveys are auto-retried until maxRetries is reached
     *
     * @param survey The survey to upload
     * @param mediaAttachments List of media attachments to upload
     * @param cleanupAttachments Whether to delete local photo files after successful upload (default: true)
     * @param skipMedia Skip media uploads to conserve battery/data on weak networks (default: false)
     * @param maxRetries Maximum number of retry attempts before survey is permanently failed (default: 3)
     * @return DomainResult containing the upload result or error
     */
    suspend operator fun invoke(
        survey: Survey,
        mediaAttachments: List<MediaAttachment> = emptyList(),
        cleanupAttachments: Boolean = true,
        skipMedia: Boolean = false,
        maxRetries: Int = 3
    ): DomainResult<DomainError, UploadSurveyResult> {
        // Step 1: Upload survey data (text only)
        // Skip if survey data was already uploaded (PENDING_MEDIA status)
        val surveyUploadResult = if (survey.syncStatus == SyncStatus.PENDING_MEDIA) {
            // Survey data already uploaded, skip to media upload
            DomainResult.success(
                UploadResult(
                    success = true,
                    surveyId = survey.surveyId,
                    message = "Survey data already uploaded",
                    uploadedAt = System.currentTimeMillis()
                )
            )
        } else {
            // Update status to SYNCING before upload
            repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCING)
            repository.uploadSurvey(survey)
        }

        return surveyUploadResult.handle(
            onError = { error ->
                when {
                    error.isRetryable -> {
                        // Retryable error (network, timeout, server error)
                        // Increment retry count and mark as FAILED
                        // Survey will be retried on next sync if retryCount < maxRetries
                        repository.incrementSurveyRetryCount(survey.surveyId)
                        repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
                    }

                    else -> {
                        // Non-retryable error (validation, authentication, business logic)
                        // Mark as permanently FAILED - sets retryCount = maxRetries
                        // This prevents any future retry attempts for errors that won't resolve
                        repository.markSurveyAsPermanentlyFailed(survey.surveyId, maxRetries)
                    }
                }
                DomainResult.error(error)
            },
            onSuccess = { uploadResult ->
                // Step 2: Upload media attachments if survey upload succeeded
                var mediaSuccessCount = 0
                var mediaFailureCount = 0
                var mediaSkippedCount = 0

                if (mediaAttachments.isNotEmpty()) {
                    if (skipMedia) {
                        // Skip media uploads to conserve battery and data on weak networks
                        // Media will remain PENDING and be retried when network improves
                        mediaSkippedCount = mediaAttachments.size
                    } else {
                        val mediaResults = uploadMediaAttachmentsUseCase.invoke(
                            surveyId = survey.surveyId,
                            attachments = mediaAttachments
                        )

                        mediaSuccessCount = mediaResults.values.count { it.isSuccess }
                        mediaFailureCount = mediaResults.values.count { it.isError }
                    }
                }

                // Step 3: Update sync status based on media upload results
                // Mark as PENDING_MEDIA if:
                //   - Media was intentionally skipped (skipMedia = true), OR
                //   - Some media uploads failed (mediaSuccessCount < total)
                // Mark as SYNCED only if all media uploaded successfully or no media exists
                val finalStatus = if (mediaAttachments.isNotEmpty() &&
                    (skipMedia || mediaSuccessCount < mediaAttachments.size)
                ) {
                    SyncStatus.PENDING_MEDIA
                } else {
                    SyncStatus.SYNCED
                }

                // Increment retry count if this was a PENDING_MEDIA retry that's still PENDING_MEDIA
                // This prevents infinite retries when conditions don't improve
                if (survey.syncStatus == SyncStatus.PENDING_MEDIA && finalStatus == SyncStatus.PENDING_MEDIA) {
                    repository.incrementSurveyRetryCount(survey.surveyId)
                }

                repository.updateSyncStatus(survey.surveyId, finalStatus)

                // Step 4: Clean up successfully uploaded attachments
                if (cleanupAttachments && mediaSuccessCount > 0) {
                    repository.cleanupSyncedAttachments(survey.surveyId)
                }

                // Return combined result
                DomainResult.success(
                    UploadSurveyResult(
                        surveyUploadResult = uploadResult,
                        mediaUploadSuccessCount = mediaSuccessCount,
                        mediaUploadFailureCount = mediaFailureCount,
                        mediaSkippedCount = mediaSkippedCount,
                        totalMediaCount = mediaAttachments.size
                    )
                )
            }
        )
    }
}
