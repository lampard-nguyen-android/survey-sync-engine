package com.survey.sync.engine.presentation.state

/**
 * UI state for the Sync screen.
 */
data class SyncUiState(
    val isSyncing: Boolean = false,
    val pendingSurveyCount: Int = 0,
    val syncResults: List<SyncResultItem> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Represents the result of a single survey sync operation.
 */
data class SyncResultItem(
    val surveyId: String,
    val isSuccess: Boolean,
    val mediaUploadSuccessCount: Int = 0,
    val mediaUploadFailureCount: Int = 0,
    val totalMediaCount: Int = 0,
    val errorMessage: String? = null
)
