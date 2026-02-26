package com.survey.sync.engine.data.mapper

import com.survey.sync.engine.data.entity.MediaAttachmentEntity
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.model.SyncStatus

/**
 * Extension function to convert MediaAttachmentEntity to Domain MediaAttachment model.
 */
fun MediaAttachmentEntity.toDomain(): MediaAttachment {
    return MediaAttachment(
        attachmentId = attachmentId,
        answerUuid = answerUuid,
        localFilePath = localFilePath,
        fileSize = fileSize,
        uploadedAt = uploadedAt,
        syncStatus = SyncStatus.valueOf(syncStatus)
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
        uploadedAt = uploadedAt,
        syncStatus = syncStatus.name
    )
}
