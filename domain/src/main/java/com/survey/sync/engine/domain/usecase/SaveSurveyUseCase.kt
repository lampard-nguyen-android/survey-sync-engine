package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for saving a survey locally (offline storage).
 */
class SaveSurveyUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Save a survey to local database.
     *
     * @param survey The survey to save
     * @return DomainResult indicating success or failure
     */
    suspend operator fun invoke(survey: Survey): DomainResult<DomainError, Unit> {
        return repository.saveSurvey(survey)
    }
}
