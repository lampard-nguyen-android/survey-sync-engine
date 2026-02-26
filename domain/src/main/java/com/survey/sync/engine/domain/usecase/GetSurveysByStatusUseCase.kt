package com.survey.sync.engine.domain.usecase

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
     * @return Result containing list of surveys or error
     */
    suspend operator fun invoke(status: SyncStatus?): Result<List<Survey>> {
        return if (status == null) {
            // Get all surveys by fetching each status and combining
            try {
                val allSurveys = mutableListOf<Survey>()
                SyncStatus.values().forEach { syncStatus ->
                    repository.getSurveysByStatus(syncStatus).fold(
                        onSuccess = { allSurveys.addAll(it) },
                        onFailure = { return Result.failure(it) }
                    )
                }
                Result.success(allSurveys)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            repository.getSurveysByStatus(status)
        }
    }
}
