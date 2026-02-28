package com.survey.sync.engine.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity for tracking media attachments (photos) stored locally.
 * Links to the answer that contains the photo reference.
 * Supports automatic cleanup after successful upload.
 */
@Entity(
    tableName = "media_attachments",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["surveyId"],
            childColumns = ["parentSurveyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AnswerEntity::class,
            parentColumns = ["answerUuid"],
            childColumns = ["answerUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["parentSurveyId"]),
        Index(value = ["answerUuid"]),
        Index(value = ["attachmentId"], unique = true)
    ]
)
data class MediaAttachmentEntity(
    @PrimaryKey
    val attachmentId: String, // UUID
    val parentSurveyId: String, // FK to SurveyEntity
    val answerUuid: String, // FK to AnswerEntity (the answer with inputType = PHOTO)
    val localFilePath: String, // Absolute path to photo on device storage
    val fileSize: Long, // Size in bytes for storage management
    val uploadedAt: Date?, // Timestamp when successfully uploaded (null if pending)
    val syncStatus: SyncStatusEntity // PENDING, SYNCED
)
