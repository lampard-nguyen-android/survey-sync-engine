package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
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
     * @return DomainResult containing list of media attachments or error
     */
    suspend operator fun invoke(surveyId: String): DomainResult<DomainError, List<MediaAttachment>> {
        return repository.getMediaAttachments(surveyId)
    }
}
