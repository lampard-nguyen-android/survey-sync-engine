package com.survey.sync.engine.domain

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.error.SurveyServerError
import com.survey.sync.engine.domain.model.Answer
import com.survey.sync.engine.domain.model.BatteryStatus
import com.survey.sync.engine.domain.model.DeviceResources
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.NetworkType
import com.survey.sync.engine.domain.model.StorageStatus
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult

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

// DeviceResources builders
fun createDeviceResources(
    batteryLevel: Int = 80,
    isCharging: Boolean = false,
    availableBytes: Long = 500_000_000L, // 500 MB
    totalBytes: Long = 10_000_000_000L, // 10 GB
    networkType: NetworkType = NetworkType.WIFI
) = DeviceResources(
    battery = BatteryStatus(
        level = batteryLevel,
        isCharging = isCharging
    ),
    storage = StorageStatus(
        availableBytes = availableBytes,
        totalBytes = totalBytes
    ),
    network = networkType
)

// Upload result builders
fun createUploadResult(
    surveyId: String = "survey-1",
    success: Boolean = true,
    message: String? = "Survey uploaded successfully",
    uploadedAt: Long = System.currentTimeMillis()
) = UploadResult(
    success = success,
    surveyId = surveyId,
    message = message,
    uploadedAt = uploadedAt
)

fun createMediaUploadResult(
    attachmentId: String = "attachment-1",
    success: Boolean = true,
    uploadedAt: Long = System.currentTimeMillis(),
    url: String? = "https://server.com/media/attachment-1",
    message: String? = "Media uploaded successfully"
) = MediaUploadResult(
    success = success,
    attachmentId = attachmentId,
    uploadedAt = uploadedAt,
    url = url,
    message = message
)

// DomainError builders
fun createNetworkFailure(
    message: String = "Network unavailable",
    throwable: Throwable? = null
) = DomainError.NetworkFailure(throwable = throwable ?: Throwable(message))

fun createApiError(
    httpCode: Int = 500,
    httpMessage: String = "Internal Server Error",
    responseBody: SurveyServerError? = null
) = DomainError.ApiError(
    httpCode = httpCode,
    httpMessage = httpMessage,
    responseBody = responseBody
)

fun createDaoError(
    operation: String = "insert",
    message: String = "Database error",
    throwable: Throwable? = null
) = DomainError.DaoError(
    throwable = throwable ?: Throwable(message),
    operation = operation
)

fun createInternalError(
    message: String = "Internal error",
    throwable: Throwable? = null
) = DomainError.InternalError(throwable = throwable ?: Throwable(message))

fun createValidationError(
    errorCode: String = "VALIDATION_ERROR",
    errorMessage: String = "Invalid survey ID",
    isRetryable: Boolean = false
) = DomainError.ValidationError(
    errorCode = errorCode,
    errorMessage = errorMessage,
    isRetryable = isRetryable
)

// DomainResult builders
fun <T> domainSuccess(value: T): DomainResult<DomainError, T> = DomainResult.Success(value)

fun <T> domainError(error: DomainError): DomainResult<DomainError, T> = DomainResult.Error(error)
