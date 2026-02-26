package com.survey.sync.engine.domain.model

/**
 * Domain model for a survey answer.
 */
data class Answer(
    val answerUuid: String,
    val questionKey: String,
    val instanceIndex: Int,
    val answerValue: String?,
    val answeredAt: Long,
    val uploadedAt: Long?,
    val syncStatus: SyncStatus
)
