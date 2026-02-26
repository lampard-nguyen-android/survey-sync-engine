package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for uploading a survey to the server.
 * Handles the business logic of uploading and updating sync status.
 */
class UploadSurveyUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Upload a survey and update its sync status based on the result.
     *
     * @param survey The survey to upload
     * @return Result containing the upload result or error
     */
    suspend operator fun invoke(survey: Survey): Result<UploadResult> {
        // Update status to SYNCING before upload
        repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCING)

        // Attempt upload
        val uploadResult = repository.uploadSurvey(survey)

        // Update status based on result
        if (uploadResult.isSuccess) {
            repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCED)
        } else {
            repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
        }

        return uploadResult
    }
}
