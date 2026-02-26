package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for retrieving media attachments for a survey.
 */
class GetMediaAttachmentsUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Get all media attachments for a specific survey.
     *
     * @param surveyId The survey ID
     * @return Result containing list of media attachments or error
     */
    suspend operator fun invoke(surveyId: String): Result<List<MediaAttachment>> {
        // Get the survey with its answers, then extract attachments
        return repository.getSurveyById(surveyId).fold(
            onSuccess = { survey ->
                if (survey == null) {
                    Result.success(emptyList())
                } else {
                    // For now, return empty list as we don't have direct attachment access
                    // This would need repository method enhancement
                    Result.success(emptyList())
                }
            },
            onFailure = { Result.failure(it) }
        )
    }
}
