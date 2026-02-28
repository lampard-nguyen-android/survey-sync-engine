package com.survey.sync.engine.data.repository

import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.MediaAttachmentDao
import com.survey.sync.engine.data.dao.QuestionDefinitionDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.mapper.toDomain
import com.survey.sync.engine.data.mapper.toEntity
import com.survey.sync.engine.data.mapper.toUploadDto
import com.survey.sync.engine.data.remote.api.SurveyApiService
import com.survey.sync.engine.data.util.safeDaoCall
import com.survey.sync.engine.domain.config.SyncConfig
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.InputType
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Date
import java.util.UUID
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
    private val questionDefinitionDao: QuestionDefinitionDao,
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
        val requestFile = photoFile
            .asRequestBody("image/jpeg".toMediaTypeOrNull())
        val filePart = okhttp3.MultipartBody.Part.createFormData(
            "file",
            photoFile.name,
            requestFile
        )

        // Create text parts
        val attachmentIdPart = attachment.attachmentId
            .toRequestBody("text/plain".toMediaTypeOrNull())
        val surveyIdPart = surveyId
            .toRequestBody("text/plain".toMediaTypeOrNull())
        val answerUuidPart = attachment.answerUuid
            .toRequestBody("text/plain".toMediaTypeOrNull())

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
                    status = SyncStatus.SYNCED.toEntity(),
                    uploadedAt = uploadResponse.uploadedAt?.let { Date(it) }
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
            surveyDao.getSurveysByStatus(status.toEntity()).map { it.toDomain() }
        }
    }

    /**
     * Get all pending surveys with full details (including answers).
     * Includes FAILED surveys that haven't exceeded max retry count.
     */
    override suspend fun getPendingSurveys(): DomainResult<DomainError, List<Survey>> {
        return safeDaoCall(operation = "getPendingSurveys") {
            surveyDao.getPendingSurveys(maxRetries = SyncConfig.MAX_RETRY_COUNT)
                .map { it.toDomain() }
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

            // Create or update media attachments for PHOTO answers.
            // Each PHOTO answer's value is treated as the local file path.
            val photoAttachments = mutableListOf<MediaAttachment>()

            for (answer in survey.answers) {
                val filePath = answer.answerValue ?: continue

                // Look up question definition to determine input type.
                val definition = questionDefinitionDao.getQuestionByKey(answer.questionKey)
                if (definition?.inputType != InputType.PHOTO.name) continue

                // Ensure we only track non-blank file paths.
                if (filePath.isBlank()) continue

                // Reuse existing attachment if present to keep idempotency.
                val existing = mediaAttachmentDao.getAttachmentByAnswer(answer.answerUuid)

                val file = File(filePath)
                val fileSize = if (file.exists()) file.length() else 0L

                if (fileSize > 0L) {
                    val attachment = MediaAttachment(
                        attachmentId = existing?.attachmentId ?: UUID.randomUUID().toString(),
                        answerUuid = answer.answerUuid,
                        localFilePath = filePath,
                        fileSize = fileSize,
                        uploadedAt = null,
                        syncStatus = SyncStatus.PENDING
                    )

                    photoAttachments.add(attachment)
                }

            }

            if (photoAttachments.isNotEmpty()) {
                val entities =
                    photoAttachments.map { it.toEntity(parentSurveyId = survey.surveyId) }
                mediaAttachmentDao.insertAllAttachments(entities)
            }
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
            surveyDao.updateSyncStatus(surveyId, status.toEntity())
        }
    }

    /**
     * Increment retry count for a survey.
     * Used when a sync attempt fails to track retry attempts.
     */
    override suspend fun incrementSurveyRetryCount(surveyId: String): DomainResult<DomainError, Unit> {
        return safeDaoCall(operation = "incrementSurveyRetryCount") {
            surveyDao.incrementRetryCount(surveyId, Date())
        }
    }

    /**
     * Mark a survey as permanently failed (non-retryable).
     * Sets retry count to maxRetries to exclude from future sync attempts.
     */
    override suspend fun markSurveyAsPermanentlyFailed(
        surveyId: String,
        maxRetries: Int
    ): DomainResult<DomainError, Unit> {
        return safeDaoCall(operation = "markSurveyAsPermanentlyFailed") {
            surveyDao.updateRetryInfo(
                surveyId = surveyId,
                retryCount = maxRetries, // Set to maxRetries to exclude from getPendingSurveys()
                lastAttemptAt = Date(),
                status = SyncStatus.FAILED.toEntity()
            )
        }
    }

    /**
     * Observe surveys by status as a reactive Flow.
     * Flow does not use DomainResult wrapper - errors are handled via Flow error channels.
     */
    override fun observeSurveysByStatus(status: SyncStatus): Flow<List<Survey>> {
        return surveyDao.observeSurveysByStatus(status.toEntity())
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
     * Get all media attachments for a specific survey.
     */
    override suspend fun getMediaAttachments(surveyId: String): DomainResult<DomainError, List<MediaAttachment>> {
        return safeDaoCall(operation = "getMediaAttachments") {
            val attachmentEntities = mediaAttachmentDao.getAttachmentsBySurvey(surveyId)
            attachmentEntities.map { it.toDomain() }
        }
    }

    /**
     * Get oldest synced attachments for cleanup purposes.
     * Used by StorageManagementUseCase to progressively free up space.
     */
    override suspend fun getOldestSyncedAttachments(
        limit: Int,
        daysOld: Int
    ): DomainResult<DomainError, List<MediaAttachment>> {
        return safeDaoCall(operation = "getOldestSyncedAttachments") {
            val thresholdDate = Date(System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L))
            val attachmentEntities =
                mediaAttachmentDao.getSyncedAttachmentsOlderThan(thresholdDate)
                    .take(limit)
            attachmentEntities.map { it.toDomain() }
        }
    }

    /**
     * Delete multiple attachments by their IDs.
     * Deletes both files and database records.
     */
    override suspend fun deleteAttachmentsByIds(attachmentIds: List<String>): DomainResult<DomainError, Int> {
        return safeDaoCall(operation = "deleteAttachmentsByIds") {
            var deletedCount = 0

            attachmentIds.forEach { attachmentId ->
                // Get attachment to find file path
                val attachment = mediaAttachmentDao.getAttachmentById(attachmentId)

                if (attachment != null) {
                    // Delete file from local storage
                    val file = File(attachment.localFilePath)
                    val fileDeleted = if (file.exists()) file.delete() else true

                    if (fileDeleted) {
                        // Remove attachment record from database
                        mediaAttachmentDao.deleteAttachmentById(attachmentId)
                        deletedCount++
                    }
                }
            }

            deletedCount
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
                .filter { it.syncStatus == SyncStatus.SYNCED.toEntity() && it.uploadedAt != null }

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
            val oldAttachments = mediaAttachmentDao.getSyncedAttachmentsOlderThan(Date(olderThan))

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
