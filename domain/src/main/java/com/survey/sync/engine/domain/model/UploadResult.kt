package com.survey.sync.engine.domain.model

/**
 * Result of a survey upload operation.
 */
data class UploadResult(
    val success: Boolean,
    val surveyId: String,
    val message: String?,
    val uploadedAt: Long
)
