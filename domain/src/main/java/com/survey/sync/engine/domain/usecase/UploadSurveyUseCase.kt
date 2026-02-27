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
        val totalMediaCount: Int
    )

    /**
     * Upload a survey and its media attachments to the server.
     * Process:
     * 1. Upload survey data (text answers)
     * 2. If successful, upload media attachments separately
     * 3. Update sync status based on results
     * 4. Clean up local photo files after successful upload
     *
     * @param survey The survey to upload
     * @param mediaAttachments List of media attachments to upload
     * @param cleanupAttachments Whether to delete local photo files after successful upload (default: true)
     * @return DomainResult containing the upload result or error
     */
    suspend operator fun invoke(
        survey: Survey,
        mediaAttachments: List<MediaAttachment> = emptyList(),
        cleanupAttachments: Boolean = true
    ): DomainResult<DomainError, UploadSurveyResult> {
        // Update status to SYNCING before upload
        repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCING)

        // Step 1: Upload survey data (text only)
        val surveyUploadResult = repository.uploadSurvey(survey)

        return surveyUploadResult.handle(
            onError = { error ->
                // Survey upload failed, mark as failed and return
                repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
                DomainResult.error(error)
            },
            onSuccess = { uploadResult ->
                // Step 2: Upload media attachments if survey upload succeeded
                var mediaSuccessCount = 0
                var mediaFailureCount = 0

                if (mediaAttachments.isNotEmpty()) {
                    val mediaResults = uploadMediaAttachmentsUseCase.invoke(
                        surveyId = survey.surveyId,
                        attachments = mediaAttachments
                    )

                    mediaSuccessCount = mediaResults.values.count { it.isSuccess() }
                    mediaFailureCount = mediaResults.values.count { it.isError() }
                }

                // Step 3: Update sync status
                // Mark as SYNCED if survey uploaded successfully
                // (Media failures don't prevent survey from being marked as synced)
                repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCED)

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
                        totalMediaCount = mediaAttachments.size
                    )
                )
            }
        )
    }
}
