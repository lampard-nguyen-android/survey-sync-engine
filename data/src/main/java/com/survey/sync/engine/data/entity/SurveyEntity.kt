package com.survey.sync.engine.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a survey session with sync tracking.
 * Parent entity for all survey answers.
 */
@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey
    val surveyId: String, // UUID
    val agentId: String,
    val farmerId: String,
    val syncStatus: String, // PENDING, SYNCING, SYNCED, FAILED
    val createdAt: Long // Timestamp in milliseconds
)
