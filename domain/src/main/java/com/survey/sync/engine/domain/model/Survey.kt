package com.survey.sync.engine.domain.model

/**
 * Domain model for a survey session.
 */
data class Survey(
    val surveyId: String,
    val agentId: String,
    val farmerId: String,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val answers: List<Answer> = emptyList()
)
