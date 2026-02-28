package com.survey.sync.engine.data.entity

/**
 * Enum representing the sync status of an entity in the local database.
 * Used by SurveyEntity, MediaAttachmentEntity, and AnswerEntity.
 */
enum class SyncStatusEntity {
    /**
     * Not yet uploaded to server
     */
    PENDING,

    /**
     * Currently being synced to server (used by SurveyEntity)
     */
    SYNCING,

    /**
     * Successfully uploaded to server
     */
    SYNCED,

    /**
     * Upload failed (used by SurveyEntity)
     */
    FAILED
}
