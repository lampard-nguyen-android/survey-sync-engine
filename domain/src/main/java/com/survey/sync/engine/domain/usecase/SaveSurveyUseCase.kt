package com.survey.sync.engine.domain.usecase

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
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(survey: Survey): Result<Unit> {
        return repository.saveSurvey(survey)
    }
}
