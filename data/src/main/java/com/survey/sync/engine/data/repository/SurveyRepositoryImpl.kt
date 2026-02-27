package com.survey.sync.engine.data.repository

import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.MediaAttachmentDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.mapper.toDomain
import com.survey.sync.engine.data.mapper.toEntity
import com.survey.sync.engine.data.mapper.toUploadDto
import com.survey.sync.engine.data.remote.api.SurveyApiService
import com.survey.sync.engine.data.util.safeDaoCall
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SurveyRepository.
 * Coordinates between local database (Room) and remote API (Retrofit).
 *
 * Error handling:
 * - API calls: Automatically wrapped by DomainResultCallAdapter
 * - DAO operations: Wrapped by safeDaoCall helper
 * - File operations: Manual error handling with DomainError.ValidationError
 */
@Singleton
class SurveyRepositoryImpl @Inject constructor(
    private val surveyDao: SurveyDao,
    private val answerDao: AnswerDao,
    private val mediaAttachmentDao: MediaAttachmentDao,
    private val apiService: SurveyApiService
) : SurveyRepository {

    /**
     * Upload a survey to the server via API (text data only, no photos).
     * Error handling is automatic via DomainResultCallAdapter.
     */
    override suspend fun uploadSurvey(survey: Survey): DomainResult<DomainError, UploadResult> {
        val uploadDto = survey.toUploadDto()
        return apiService.uploadSurvey(uploadDto).map { it.toDomain() }
    }

    /**
     * Upload a media attachment (photo) to the server as multipart/form-data.
     * API call error handling is automatic. File validation and DB updates use manual handling.
     */
    override suspend fun uploadMediaAttachment(
        surveyId: String,
        attachment: MediaAttachment
    ): DomainResult<DomainError, MediaUploadResult> {
        val photoFile = File(attachment.localFilePath)

        // Validate file exists
        if (!photoFile.exists()) {
            return DomainResult.error(
                DomainError.ValidationError(
                    errorCode = "FILE_NOT_FOUND",
                    errorMessage = "Photo file not found: ${attachment.localFilePath}",
                    isRetryable = false
                )
            )
        }

        // Create multipart request body
        val requestFile = okhttp3.RequestBody.create(
            "image/jpeg".toMediaTypeOrNull(),
            photoFile
        )
        val filePart = okhttp3.MultipartBody.Part.createFormData(
            "file",
            photoFile.name,
            requestFile
        )

        // Create text parts
        val attachmentIdPart = okhttp3.RequestBody.create(
            "text/plain".toMediaTypeOrNull(),
            attachment.attachmentId
        )
        val surveyIdPart = okhttp3.RequestBody.create(
            "text/plain".toMediaTypeOrNull(),
            surveyId
        )
        val answerUuidPart = okhttp3.RequestBody.create(
            "text/plain".toMediaTypeOrNull(),
            attachment.answerUuid
        )

        // Upload to server (automatic error handling)
        return apiService.uploadMedia(
            attachmentIdPart,
            surveyIdPart,
            answerUuidPart,
            filePart
        ).flatMap { uploadResponse ->
            // Update attachment sync status in database
            safeDaoCall(operation = "updateAttachmentSyncStatus") {
                mediaAttachmentDao.updateAttachmentSyncStatus(
                    attachmentId = attachment.attachmentId,
                    status = SyncStatus.SYNCED.name,
                    uploadedAt = uploadResponse.toDomain().uploadedAt
                )
                uploadResponse.toDomain()
            }
        }
    }

    /**
     * Get surveys by sync status from local database.
     */
    override suspend fun getSurveysByStatus(status: SyncStatus): DomainResult<DomainError, List<Survey>> {
        return safeDaoCall(operation = "getSurveysByStatus") {
            surveyDao.getSurveysByStatus(status.name).map { it.toDomain() }
        }
    }

    /**
     * Get all pending surveys with full details (including answers).
     */
    override suspend fun getPendingSurveys(): DomainResult<DomainError, List<Survey>> {
        return safeDaoCall(operation = "getPendingSurveys") {
            surveyDao.getPendingSurveys().map { it.toDomain() }
        }
    }

    /**
     * Save a survey locally (offline storage).
     */
    override suspend fun saveSurvey(survey: Survey): DomainResult<DomainError, Unit> {
        return safeDaoCall(operation = "saveSurvey") {
            // Save survey entity
            surveyDao.insertSurvey(survey.toEntity())

            // Save all answers
            val answerEntities = survey.answers.map { answer ->
                answer.toEntity(parentSurveyId = survey.surveyId)
            }
            answerDao.insertAllAnswers(answerEntities)
        }
    }

    /**
     * Update sync status of a survey.
     */
    override suspend fun updateSyncStatus(
        surveyId: String,
        status: SyncStatus
    ): DomainResult<DomainError, Unit> {
        return safeDaoCall(operation = "updateSyncStatus") {
            surveyDao.updateSyncStatus(surveyId, status.name)
        }
    }

    /**
     * Observe surveys by status as a reactive Flow.
     * Flow does not use DomainResult wrapper - errors are handled via Flow error channels.
     */
    override fun observeSurveysByStatus(status: SyncStatus): Flow<List<Survey>> {
        return surveyDao.observeSurveysByStatus(status.name)
            .map { surveys ->
                surveys.map { it.toDomain() }
            }
    }

    /**
     * Get a specific survey by ID with its answers.
     */
    override suspend fun getSurveyById(surveyId: String): DomainResult<DomainError, Survey?> {
        return safeDaoCall(operation = "getSurveyById") {
            surveyDao.getFullSurveyDetail(surveyId)?.toDomain()
        }
    }

    /**
     * Delete a survey (will cascade delete answers).
     */
    override suspend fun deleteSurvey(surveyId: String): DomainResult<DomainError, Unit> {
        return safeDaoCall(operation = "deleteSurvey") {
            surveyDao.deleteSurveyById(surveyId)
        }
    }

    /**
     * Clean up synced attachments for a specific survey.
     * Deletes local photo files after successful upload to free up storage.
     * Combines DAO and file system operations with proper error handling.
     */
    override suspend fun cleanupSyncedAttachments(surveyId: String): DomainResult<DomainError, Int> {
        return safeDaoCall(operation = "cleanupSyncedAttachments") {
            // Get all synced attachments for this survey
            val attachments = mediaAttachmentDao.getAttachmentsBySurvey(surveyId)
                .filter { it.syncStatus == SyncStatus.SYNCED.name && it.uploadedAt != null }

            var deletedCount = 0

            // Delete each file from local storage
            attachments.forEach { attachment ->
                val file = File(attachment.localFilePath)
                if (file.exists() && file.delete()) {
                    // Remove attachment record from database after file deletion
                    mediaAttachmentDao.deleteAttachmentById(attachment.attachmentId)
                    deletedCount++
                }
            }

            deletedCount
        }
    }

    /**
     * Clean up all synced attachments older than specified timestamp.
     * Useful for periodic cleanup to free up storage on low-end devices.
     * Combines DAO and file system operations with proper error handling.
     */
    override suspend fun cleanupOldSyncedAttachments(olderThan: Long): DomainResult<DomainError, Int> {
        return safeDaoCall(operation = "cleanupOldSyncedAttachments") {
            // Get all synced attachments older than threshold
            val oldAttachments = mediaAttachmentDao.getSyncedAttachmentsOlderThan(olderThan)

            var deletedCount = 0

            // Delete each file from local storage
            oldAttachments.forEach { attachment ->
                val file = File(attachment.localFilePath)
                if (file.exists() && file.delete()) {
                    // Remove attachment record from database after file deletion
                    mediaAttachmentDao.deleteAttachmentById(attachment.attachmentId)
                    deletedCount++
                }
            }

            deletedCount
        }
    }
}
