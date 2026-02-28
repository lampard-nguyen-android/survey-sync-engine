package com.survey.sync.engine.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Individual answer data with relationships to survey and question definition.
 * Supports repeating sections via instanceIndex.
 */
@Entity(
    tableName = "answers",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["surveyId"],
            childColumns = ["parentSurveyId"],
            onDelete = ForeignKey.CASCADE // Delete answers when survey is deleted
        ),
        ForeignKey(
            entity = QuestionDefinitionEntity::class,
            parentColumns = ["questionKey"],
            childColumns = ["questionKey"],
            onDelete = ForeignKey.RESTRICT // Prevent deletion of question if answers exist
        )
    ],
    indices = [
        Index(value = ["parentSurveyId"]),
        Index(value = ["questionKey"]),
        Index(value = ["answerUuid"], unique = true)
    ]
)
data class AnswerEntity(
    @PrimaryKey
    val answerUuid: String, // UUID for idempotency (prevents duplicate POST on retry)
    val parentSurveyId: String, // FK to SurveyEntity
    val questionKey: String, // FK to QuestionDefinitionEntity
    val instanceIndex: Int, // 0 for general questions, 1+ for repeating sections (e.g., Farm 1, Farm 2)
    val answerValue: String?, // Actual answer value (nullable for unanswered questions)
    val answeredAt: Date, // Timestamp when answer was recorded
    val uploadedAt: Date?, // Timestamp when successfully uploaded (null if not yet uploaded)
    val syncStatus: SyncStatusEntity, // PENDING, SYNCED
    val retryCount: Int = 0 // Number of retry attempts for this answer
)
