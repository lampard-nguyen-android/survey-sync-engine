package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for cleaning up synced photo attachments after successful upload.
 * Deletes local files to free up storage on low-end devices.
 */
class CleanupSyncedAttachmentsUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Clean up synced attachments for a specific survey.
     *
     * @param surveyId The survey ID
     * @return Result containing number of files deleted
     */
    suspend operator fun invoke(surveyId: String): Result<Int> {
        return repository.cleanupSyncedAttachments(surveyId)
    }
}
