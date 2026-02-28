package com.survey.sync.engine.data.mapper

import com.survey.sync.engine.data.entity.MediaAttachmentEntity
import com.survey.sync.engine.data.remote.dto.MediaUploadResponseDto
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.MediaUploadResult
import java.util.Date

/**
 * Extension function to convert MediaAttachmentEntity to Domain MediaAttachment model.
 */
fun MediaAttachmentEntity.toDomain(): MediaAttachment {
    return MediaAttachment(
        attachmentId = attachmentId,
        answerUuid = answerUuid,
        localFilePath = localFilePath,
        fileSize = fileSize,
        uploadedAt = uploadedAt?.time, // Convert Date? to Long? (milliseconds)
        syncStatus = syncStatus.toDomain()
    )
}

/**
 * Extension function to convert Domain MediaAttachment to MediaAttachmentEntity.
 */
fun MediaAttachment.toEntity(parentSurveyId: String): MediaAttachmentEntity {
    return MediaAttachmentEntity(
        attachmentId = attachmentId,
        parentSurveyId = parentSurveyId,
        answerUuid = answerUuid,
        localFilePath = localFilePath,
        fileSize = fileSize,
        uploadedAt = uploadedAt?.let { Date(it) }, // Convert Long? to Date?
        syncStatus = syncStatus.toEntity(),
        retryCount = 0 // Default value for new entities
    )
}

/**
 * Extension function to convert MediaUploadResponseDto to Domain MediaUploadResult.
 */
fun MediaUploadResponseDto.toDomain(): MediaUploadResult {
    return MediaUploadResult(
        success = success,
        attachmentId = attachmentId,
        uploadedAt = uploadedAt,
        url = url,
        message = message
    )
}
