package com.survey.sync.engine.domain.model

enum class SyncStatus {
    PENDING,
    SYNCING,
    PENDING_MEDIA,  // Survey data uploaded, media attachments pending
    SYNCED,
    FAILED
}
