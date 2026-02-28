package com.survey.sync.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.survey.sync.engine.data.entity.MediaAttachmentEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MediaAttachment operations.
 * Handles photo attachment storage tracking and cleanup.
 */
@Dao
interface MediaAttachmentDao {

    /**
     * Insert a media attachment. Uses REPLACE strategy for updates.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: MediaAttachmentEntity): Long

    /**
     * Insert multiple attachments.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAttachments(attachments: List<MediaAttachmentEntity>)

    /**
     * Get all attachments for a specific survey.
     */
    @Query("SELECT * FROM media_attachments WHERE parentSurveyId = :surveyId ORDER BY attachmentId")
    suspend fun getAttachmentsBySurvey(surveyId: String): List<MediaAttachmentEntity>

    /**
     * Get all attachments for a specific survey as Flow.
     */
    @Query("SELECT * FROM media_attachments WHERE parentSurveyId = :surveyId ORDER BY attachmentId")
    fun observeAttachmentsBySurvey(surveyId: String): Flow<List<MediaAttachmentEntity>>

    /**
     * Get attachment for a specific answer (photo answers).
     */
    @Query("SELECT * FROM media_attachments WHERE answerUuid = :answerUuid")
    suspend fun getAttachmentByAnswer(answerUuid: String): MediaAttachmentEntity?

    /**
     * Get a specific attachment by its ID.
     * Used for deleting attachments along with their local files.
     */
    @Query("SELECT * FROM media_attachments WHERE attachmentId = :attachmentId")
    suspend fun getAttachmentById(attachmentId: String): MediaAttachmentEntity?

    /**
     * Get all pending attachments (not yet uploaded) for a survey.
     */
    @Query("SELECT * FROM media_attachments WHERE parentSurveyId = :surveyId AND syncStatus = 'PENDING'")
    suspend fun getPendingAttachmentsBySurvey(surveyId: String): List<MediaAttachmentEntity>

    /**
     * Get all synced attachments (successfully uploaded) for cleanup.
     * Used to delete local files after successful upload.
     */
    @Query("SELECT * FROM media_attachments WHERE syncStatus = 'SYNCED' AND uploadedAt IS NOT NULL")
    suspend fun getSyncedAttachmentsForCleanup(): List<MediaAttachmentEntity>

    /**
     * Get synced attachments older than a specific timestamp for cleanup.
     */
    @Query("SELECT * FROM media_attachments WHERE syncStatus = 'SYNCED' AND uploadedAt IS NOT NULL AND uploadedAt < :olderThan")
    suspend fun getSyncedAttachmentsOlderThan(olderThan: Long): List<MediaAttachmentEntity>

    /**
     * Update sync status of an attachment after upload.
     */
    @Query("UPDATE media_attachments SET syncStatus = :status, uploadedAt = :uploadedAt WHERE attachmentId = :attachmentId")
    suspend fun updateAttachmentSyncStatus(attachmentId: String, status: String, uploadedAt: Long?)

    /**
     * Update sync status for all attachments of a survey.
     */
    @Query("UPDATE media_attachments SET syncStatus = :status, uploadedAt = :uploadedAt WHERE parentSurveyId = :surveyId")
    suspend fun updateAllAttachmentsSyncStatus(surveyId: String, status: String, uploadedAt: Long?)

    /**
     * Get total size of attachments for a survey.
     */
    @Query("SELECT SUM(fileSize) FROM media_attachments WHERE parentSurveyId = :surveyId")
    suspend fun getTotalAttachmentSize(surveyId: String): Long?

    /**
     * Get total size of all attachments.
     */
    @Query("SELECT SUM(fileSize) FROM media_attachments")
    suspend fun getTotalStorageUsed(): Long?

    /**
     * Get count of pending attachments.
     */
    @Query("SELECT COUNT(*) FROM media_attachments WHERE syncStatus = 'PENDING'")
    suspend fun getPendingAttachmentCount(): Int

    /**
     * Delete an attachment by ID (after local file has been deleted).
     */
    @Query("DELETE FROM media_attachments WHERE attachmentId = :attachmentId")
    suspend fun deleteAttachmentById(attachmentId: String)

    /**
     * Delete all attachments for a survey (cascade should handle this, but explicit method provided).
     */
    @Query("DELETE FROM media_attachments WHERE parentSurveyId = :surveyId")
    suspend fun deleteAttachmentsBySurvey(surveyId: String)

    /**
     * Delete synced attachments (typically after local files are deleted).
     */
    @Query("DELETE FROM media_attachments WHERE syncStatus = 'SYNCED' AND uploadedAt IS NOT NULL")
    suspend fun deleteSyncedAttachments()

    /**
     * Delete all attachments.
     */
    @Query("DELETE FROM media_attachments")
    suspend fun deleteAllAttachments()
}
