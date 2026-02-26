package com.survey.sync.engine.domain.model

/**
 * Result of a media attachment upload operation.
 */
data class MediaUploadResult(
    val success: Boolean,
    val attachmentId: String,
    val uploadedAt: Long,
    val url: String?,
    val message: String?
)
