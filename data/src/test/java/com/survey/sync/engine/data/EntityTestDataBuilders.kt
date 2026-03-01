package com.survey.sync.engine.data

import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.entity.MediaAttachmentEntity
import com.survey.sync.engine.data.entity.QuestionDefinitionEntity
import com.survey.sync.engine.data.entity.SurveyEntity
import com.survey.sync.engine.data.entity.SyncStatusEntity
import com.survey.sync.engine.data.remote.dto.SurveyUploadResponseDto
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.Answer
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import java.util.Date

/**
 * Test data builders for creating domain model instances in tests.
 */

// Survey builders
fun createSurvey(
    surveyId: String = "survey-1",
    agentId: String = "agent-1",
    farmerId: String = "farmer-1",
    syncStatus: SyncStatus = SyncStatus.PENDING,
    createdAt: Long = System.currentTimeMillis(),
    answers: List<Answer> = emptyList()
) = Survey(
    surveyId = surveyId,
    agentId = agentId,
    farmerId = farmerId,
    syncStatus = syncStatus,
    createdAt = createdAt,
    answers = answers
)

fun createAnswer(
    answerUuid: String = "answer-1",
    questionKey: String = "question-1",
    instanceIndex: Int = 0,
    answerValue: String? = "test-value",
    answeredAt: Long = System.currentTimeMillis(),
    uploadedAt: Long? = null,
    syncStatus: SyncStatus = SyncStatus.PENDING
) = Answer(
    answerUuid = answerUuid,
    questionKey = questionKey,
    instanceIndex = instanceIndex,
    answerValue = answerValue,
    answeredAt = answeredAt,
    uploadedAt = uploadedAt,
    syncStatus = syncStatus
)

// MediaAttachment builders
fun createMediaAttachment(
    attachmentId: String = "attachment-1",
    answerUuid: String = "answer-1",
    localFilePath: String = "/path/to/file.jpg",
    fileSize: Long = 1024L,
    uploadedAt: Long? = null,
    syncStatus: SyncStatus = SyncStatus.PENDING
) = MediaAttachment(
    attachmentId = attachmentId,
    answerUuid = answerUuid,
    localFilePath = localFilePath,
    fileSize = fileSize,
    uploadedAt = uploadedAt,
    syncStatus = syncStatus
)


/**
 * Test data builders for creating data layer entity instances in tests.
 */

// SurveyEntity builders
fun createSurveyEntity(
    surveyId: String = "survey-1",
    agentId: String = "agent-1",
    farmerId: String = "farmer-1",
    syncStatus: SyncStatusEntity = SyncStatusEntity.PENDING,
    createdAt: Date = Date(),
    retryCount: Int = 0,
    lastAttemptAt: Date? = null
) = SurveyEntity(
    surveyId = surveyId,
    agentId = agentId,
    farmerId = farmerId,
    syncStatus = syncStatus,
    createdAt = createdAt,
    retryCount = retryCount,
    lastAttemptAt = lastAttemptAt
)

// AnswerEntity builders
fun createAnswerEntity(
    answerUuid: String = "answer-1",
    parentSurveyId: String = "survey-1",
    questionKey: String = "farmer_name",
    instanceIndex: Int = 0,
    answerValue: String? = "John Doe",
    answeredAt: Date = Date(),
    uploadedAt: Date? = null,
    syncStatus: SyncStatusEntity = SyncStatusEntity.PENDING,
    retryCount: Int = 0
) = AnswerEntity(
    answerUuid = answerUuid,
    parentSurveyId = parentSurveyId,
    questionKey = questionKey,
    instanceIndex = instanceIndex,
    answerValue = answerValue,
    answeredAt = answeredAt,
    uploadedAt = uploadedAt,
    syncStatus = syncStatus,
    retryCount = retryCount
)

// MediaAttachmentEntity builders
fun createMediaAttachmentEntity(
    attachmentId: String = "attachment-1",
    parentSurveyId: String = "survey-1",
    answerUuid: String = "answer-1",
    localFilePath: String = "/storage/emulated/0/Pictures/photo.jpg",
    fileSize: Long = 1024L,
    uploadedAt: Date? = null,
    syncStatus: SyncStatusEntity = SyncStatusEntity.PENDING,
    retryCount: Int = 0
) = MediaAttachmentEntity(
    attachmentId = attachmentId,
    parentSurveyId = parentSurveyId,
    answerUuid = answerUuid,
    localFilePath = localFilePath,
    fileSize = fileSize,
    uploadedAt = uploadedAt,
    syncStatus = syncStatus,
    retryCount = retryCount
)

// QuestionDefinitionEntity builders
fun createQuestionDefinitionEntity(
    questionKey: String = "farmer_name",
    sectionType: String = "GENERAL",
    isRepeating: Boolean = false,
    inputType: String = "TEXT",
    labelText: String = "Farmer Name",
    sortOrder: Int = 0
) = QuestionDefinitionEntity(
    questionKey = questionKey,
    sectionType = sectionType,
    isRepeating = isRepeating,
    inputType = inputType,
    labelText = labelText,
    sortOrder = sortOrder
)

// DTO builders
fun createUploadResponseDto(
    success: Boolean = true,
    surveyId: String = "survey-1",
    message: String? = "Survey uploaded successfully",
    uploadedAt: Long = System.currentTimeMillis()
) = SurveyUploadResponseDto(
    success = success,
    surveyId = surveyId,
    message = message,
    uploadedAt = uploadedAt
)

// DomainResult builders
fun <T> domainSuccess(value: T): DomainResult<DomainError, T> = DomainResult.Success(value)

fun <T> domainError(error: DomainError): DomainResult<DomainError, T> = DomainResult.Error(error)