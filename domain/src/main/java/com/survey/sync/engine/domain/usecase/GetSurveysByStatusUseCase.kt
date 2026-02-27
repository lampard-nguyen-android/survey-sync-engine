package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for retrieving surveys filtered by sync status.
 */
class GetSurveysByStatusUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Get all surveys with a specific sync status.
     *
     * @param status The sync status to filter by (null for all surveys)
     * @return DomainResult containing list of surveys or error
     */
    suspend operator fun invoke(status: SyncStatus?): DomainResult<DomainError, List<Survey>> {
        return if (status == null) {
            // Get all surveys by fetching each status and combining
            try {
                val allSurveys = mutableListOf<Survey>()
                SyncStatus.values().forEach { syncStatus ->
                    repository.getSurveysByStatus(syncStatus).handle(
                        onError = { return DomainResult.error(it) },
                        onSuccess = { allSurveys.addAll(it) }
                    )
                }
                DomainResult.success(allSurveys)
            } catch (e: Exception) {
                DomainResult.error(DomainError.UnexpectedError(e.message ?: "Unknown error"))
            }
        } else {
            repository.getSurveysByStatus(status)
        }
    }
}
