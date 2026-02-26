package com.survey.sync.engine.data.repository

import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.MediaAttachmentDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.mapper.toDomain
import com.survey.sync.engine.data.mapper.toEntity
import com.survey.sync.engine.data.mapper.toUploadDto
import com.survey.sync.engine.data.remote.api.SurveyApiService
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
 * Handles photo attachment cleanup after successful upload.
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
     */
    override suspend fun uploadSurvey(survey: Survey): Result<UploadResult> {
        return try {
            val uploadDto = survey.toUploadDto()
            val response = apiService.uploadSurvey(uploadDto)

            if (response.isSuccessful && response.body() != null) {
                val uploadResult = response.body()!!.toDomain()
                Result.success(uploadResult)
            } else {
                Result.failure(
                    Exception("Upload failed: HTTP ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload a media attachment (photo) to the server as multipart/form-data.
     */
    override suspend fun uploadMediaAttachment(
        surveyId: String,
        attachment: MediaAttachment
    ): Result<MediaUploadResult> {
        return try {
            val photoFile = File(attachment.localFilePath)

            if (!photoFile.exists()) {
                return Result.failure(Exception("Photo file not found: ${attachment.localFilePath}"))
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

            // Upload to server
            val response = apiService.uploadMedia(
                attachmentIdPart,
                surveyIdPart,
                answerUuidPart,
                filePart
            )

            if (response.isSuccessful && response.body() != null) {
                val uploadResult = response.body()!!.toDomain()

                // Update attachment sync status in database
                mediaAttachmentDao.updateAttachmentSyncStatus(
                    attachmentId = attachment.attachmentId,
                    status = SyncStatus.SYNCED.name,
                    uploadedAt = uploadResult.uploadedAt
                )

                Result.success(uploadResult)
            } else {
                Result.failure(
                    Exception("Media upload failed: HTTP ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get surveys by sync status from local database.
     */
    override suspend fun getSurveysByStatus(status: SyncStatus): Result<List<Survey>> {
        return try {
            val surveys = surveyDao.getSurveysByStatus(status.name)
                .map { it.toDomain() }
            Result.success(surveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all pending surveys with full details (including answers).
     */
    override suspend fun getPendingSurveys(): Result<List<Survey>> {
        return try {
            val pendingSurveys = surveyDao.getPendingSurveys()
                .map { it.toDomain() }
            Result.success(pendingSurveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save a survey locally (offline storage).
     */
    override suspend fun saveSurvey(survey: Survey): Result<Unit> {
        return try {
            // Save survey entity
            surveyDao.insertSurvey(survey.toEntity())

            // Save all answers
            val answerEntities = survey.answers.map { answer ->
                answer.toEntity(parentSurveyId = survey.surveyId)
            }
            answerDao.insertAllAnswers(answerEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update sync status of a survey.
     */
    override suspend fun updateSyncStatus(surveyId: String, status: SyncStatus): Result<Unit> {
        return try {
            surveyDao.updateSyncStatus(surveyId, status.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe surveys by status as a reactive Flow.
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
    override suspend fun getSurveyById(surveyId: String): Result<Survey?> {
        return try {
            val fullSurveyDetail = surveyDao.getFullSurveyDetail(surveyId)
            val survey = fullSurveyDetail?.toDomain()
            Result.success(survey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a survey (will cascade delete answers).
     */
    override suspend fun deleteSurvey(surveyId: String): Result<Unit> {
        return try {
            surveyDao.deleteSurveyById(surveyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clean up synced attachments for a specific survey.
     * Deletes local photo files after successful upload to free up storage.
     */
    override suspend fun cleanupSyncedAttachments(surveyId: String): Result<Int> {
        return try {
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

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clean up all synced attachments older than specified timestamp.
     * Useful for periodic cleanup to free up storage on low-end devices.
     */
    override suspend fun cleanupOldSyncedAttachments(olderThan: Long): Result<Int> {
        return try {
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

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
