package com.survey.sync.engine.domain.model

/**
 * Domain model for a media attachment (photo).
 */
data class MediaAttachment(
    val attachmentId: String,
    val answerUuid: String,
    val localFilePath: String,
    val fileSize: Long,
    val uploadedAt: Long?,
    val syncStatus: SyncStatus
)
