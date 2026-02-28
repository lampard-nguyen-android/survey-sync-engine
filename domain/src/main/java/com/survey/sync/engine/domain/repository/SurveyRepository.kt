package com.survey.sync.engine.domain.repository

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for survey operations.
 * Defines the contract for data operations - implemented in the data layer.
 *
 * All operations return DomainResult for consistent error handling:
 * - API calls automatically wrapped by DomainResultCallAdapter
 * - Database operations wrapped by safeDaoCall helper
 * - All errors mapped to appropriate DomainError subtypes
 */
interface SurveyRepository {

    /**
     * Upload a survey to the server (text data only, no photos).
     *
     * @param survey The survey to upload
     * @return DomainResult containing upload result or error details
     */
    suspend fun uploadSurvey(survey: Survey): DomainResult<DomainError, UploadResult>

    /**
     * Upload a media attachment (photo) to the server.
     * Should be called separately after survey upload.
     *
     * @param surveyId The survey ID
     * @param attachment The media attachment to upload
     * @return DomainResult containing upload result or error details
     */
    suspend fun uploadMediaAttachment(
        surveyId: String,
        attachment: MediaAttachment
    ): DomainResult<DomainError, MediaUploadResult>

    /**
     * Get all surveys with a specific sync status.
     *
     * @param status The sync status to filter by
     * @return DomainResult containing list of surveys or error details
     */
    suspend fun getSurveysByStatus(status: SyncStatus): DomainResult<DomainError, List<Survey>>

    /**
     * Get all pending surveys (ready to be uploaded).
     *
     * @return DomainResult containing list of pending surveys or error details
     */
    suspend fun getPendingSurveys(): DomainResult<DomainError, List<Survey>>

    /**
     * Save a survey locally (offline storage).
     *
     * @param survey The survey to save
     * @return DomainResult indicating success or error details
     */
    suspend fun saveSurvey(survey: Survey): DomainResult<DomainError, Unit>

    /**
     * Update the sync status of a survey.
     *
     * @param surveyId The survey ID
     * @param status The new sync status
     * @return DomainResult indicating success or error details
     */
    suspend fun updateSyncStatus(
        surveyId: String,
        status: SyncStatus
    ): DomainResult<DomainError, Unit>

    /**
     * Increment retry count for a survey.
     * Used when a sync attempt fails to track retry attempts.
     *
     * @param surveyId The survey ID
     * @return DomainResult indicating success or error details
     */
    suspend fun incrementSurveyRetryCount(surveyId: String): DomainResult<DomainError, Unit>

    /**
     * Mark a survey as permanently failed (non-retryable).
     * Sets retry count to a high value to exclude it from future sync attempts.
     * Used for validation errors, authentication failures, or other non-retryable errors.
     *
     * @param surveyId The survey ID
     * @param maxRetries Maximum retry count value to set (ensures survey is excluded from retries)
     * @return DomainResult indicating success or error details
     */
    suspend fun markSurveyAsPermanentlyFailed(
        surveyId: String,
        maxRetries: Int = 3
    ): DomainResult<DomainError, Unit>

    /**
     * Observe surveys by status as a Flow for reactive updates.
     *
     * @param status The sync status to filter by
     * @return Flow of survey lists
     */
    fun observeSurveysByStatus(status: SyncStatus): Flow<List<Survey>>

    /**
     * Get a specific survey by ID.
     *
     * @param surveyId The survey ID
     * @return DomainResult containing the survey if found, or error details
     */
    suspend fun getSurveyById(surveyId: String): DomainResult<DomainError, Survey?>

    /**
     * Delete a survey by ID.
     *
     * @param surveyId The survey ID
     * @return DomainResult indicating success or error details
     */
    suspend fun deleteSurvey(surveyId: String): DomainResult<DomainError, Unit>

    /**
     * Get all media attachments for a specific survey.
     *
     * @param surveyId The survey ID
     * @return DomainResult containing list of media attachments or error details
     */
    suspend fun getMediaAttachments(surveyId: String): DomainResult<DomainError, List<MediaAttachment>>

    /**
     * Get oldest synced attachments for cleanup.
     *
     * @param limit Maximum number of attachments to return
     * @param daysOld Minimum age in days
     * @return DomainResult containing list of old attachments or error details
     */
    suspend fun getOldestSyncedAttachments(
        limit: Int = 100,
        daysOld: Int = 7
    ): DomainResult<DomainError, List<MediaAttachment>>

    /**
     * Delete multiple attachments by their IDs.
     *
     * @param attachmentIds List of attachment IDs to delete
     * @return DomainResult containing number of files deleted or error details
     */
    suspend fun deleteAttachmentsByIds(attachmentIds: List<String>): DomainResult<DomainError, Int>

    /**
     * Clean up synced attachments (delete local files after successful upload).
     * This should be called after a successful survey upload.
     *
     * @param surveyId The survey ID
     * @return DomainResult containing number of files deleted or error details
     */
    suspend fun cleanupSyncedAttachments(surveyId: String): DomainResult<DomainError, Int>

    /**
     * Clean up all synced attachments older than specified timestamp.
     * Useful for periodic cleanup to free up storage.
     *
     * @param olderThan Timestamp threshold
     * @return DomainResult containing number of files deleted or error details
     */
    suspend fun cleanupOldSyncedAttachments(olderThan: Long): DomainResult<DomainError, Int>
}
