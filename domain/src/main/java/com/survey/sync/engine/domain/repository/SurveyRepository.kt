package com.survey.sync.engine.domain.repository

import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for survey operations.
 * Defines the contract for data operations - implemented in the data layer.
 */
interface SurveyRepository {

    /**
     * Upload a survey to the server (text data only, no photos).
     *
     * @param survey The survey to upload
     * @return Result containing success/failure information
     */
    suspend fun uploadSurvey(survey: Survey): Result<UploadResult>

    /**
     * Upload a media attachment (photo) to the server.
     * Should be called separately after survey upload.
     *
     * @param surveyId The survey ID
     * @param attachment The media attachment to upload
     * @return Result containing upload result
     */
    suspend fun uploadMediaAttachment(
        surveyId: String,
        attachment: MediaAttachment
    ): Result<MediaUploadResult>

    /**
     * Get all surveys with a specific sync status.
     *
     * @param status The sync status to filter by
     * @return List of surveys
     */
    suspend fun getSurveysByStatus(status: SyncStatus): Result<List<Survey>>

    /**
     * Get all pending surveys (ready to be uploaded).
     *
     * @return List of pending surveys with their answers
     */
    suspend fun getPendingSurveys(): Result<List<Survey>>

    /**
     * Save a survey locally (offline storage).
     *
     * @param survey The survey to save
     * @return Result indicating success/failure
     */
    suspend fun saveSurvey(survey: Survey): Result<Unit>

    /**
     * Update the sync status of a survey.
     *
     * @param surveyId The survey ID
     * @param status The new sync status
     * @return Result indicating success/failure
     */
    suspend fun updateSyncStatus(surveyId: String, status: SyncStatus): Result<Unit>

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
     * @return The survey if found, null otherwise
     */
    suspend fun getSurveyById(surveyId: String): Result<Survey?>

    /**
     * Delete a survey by ID.
     *
     * @param surveyId The survey ID
     * @return Result indicating success/failure
     */
    suspend fun deleteSurvey(surveyId: String): Result<Unit>

    /**
     * Clean up synced attachments (delete local files after successful upload).
     * This should be called after a successful survey upload.
     *
     * @param surveyId The survey ID
     * @return Result containing number of files deleted
     */
    suspend fun cleanupSyncedAttachments(surveyId: String): Result<Int>

    /**
     * Clean up all synced attachments older than specified timestamp.
     * Useful for periodic cleanup to free up storage.
     *
     * @param olderThan Timestamp threshold
     * @return Result containing number of files deleted
     */
    suspend fun cleanupOldSyncedAttachments(olderThan: Long): Result<Int>
}
