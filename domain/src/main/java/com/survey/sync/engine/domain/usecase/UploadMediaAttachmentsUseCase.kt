package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for uploading media attachments (photos) to the server.
 * Uploads each attachment separately and tracks individual results.
 */
class UploadMediaAttachmentsUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Upload a single media attachment.
     *
     * @param surveyId The survey ID
     * @param attachment The attachment to upload
     * @return Result containing upload result or error
     */
    suspend fun uploadSingle(
        surveyId: String,
        attachment: MediaAttachment
    ): Result<MediaUploadResult> {
        return repository.uploadMediaAttachment(surveyId, attachment)
    }

    /**
     * Upload multiple media attachments for a survey.
     * Continues uploading even if some fail, tracking all results.
     *
     * @param surveyId The survey ID
     * @param attachments List of attachments to upload
     * @return Map of attachment ID to upload result
     */
    suspend operator fun invoke(
        surveyId: String,
        attachments: List<MediaAttachment>
    ): Map<String, Result<MediaUploadResult>> {
        val results = mutableMapOf<String, Result<MediaUploadResult>>()

        attachments.forEach { attachment ->
            val result = uploadSingle(surveyId, attachment)
            results[attachment.attachmentId] = result
        }

        return results
    }
}
