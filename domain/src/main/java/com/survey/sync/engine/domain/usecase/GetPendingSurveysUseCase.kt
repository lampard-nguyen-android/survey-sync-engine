package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for retrieving all pending surveys that need to be uploaded.
 */
class GetPendingSurveysUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Get all surveys with PENDING status.
     *
     * @return Result containing list of pending surveys or error
     */
    suspend operator fun invoke(): Result<List<Survey>> {
        return repository.getPendingSurveys()
    }
}
