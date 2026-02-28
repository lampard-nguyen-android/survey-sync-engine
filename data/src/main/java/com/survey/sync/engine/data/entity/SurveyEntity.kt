package com.survey.sync.engine.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

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
    val syncStatus: SyncStatusEntity, // PENDING, SYNCING, SYNCED, FAILED
    val createdAt: Date // Timestamp when survey was created
)
