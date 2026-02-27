package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for periodic cleanup of old synced attachments.
 * Helps manage storage on devices with limited capacity (16-32GB).
 */
class CleanupOldAttachmentsUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    /**
     * Clean up synced attachments older than specified timestamp.
     * Typical usage: clean up attachments older than 30 days.
     *
     * @param daysOld Number of days (will calculate timestamp)
     * @return DomainResult containing number of files deleted
     */
    suspend operator fun invoke(daysOld: Int = 30): DomainResult<DomainError, Int> {
        val now = System.currentTimeMillis()
        val daysInMillis = daysOld * 24 * 60 * 60 * 1000L
        val threshold = now - daysInMillis

        return repository.cleanupOldSyncedAttachments(threshold)
    }
}
