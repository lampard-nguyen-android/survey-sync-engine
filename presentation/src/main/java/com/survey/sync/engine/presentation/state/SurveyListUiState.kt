package com.survey.sync.engine.presentation.state

import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus

/**
 * UI state for the Survey List screen.
 */
data class SurveyListUiState(
    val isLoading: Boolean = false,
    val surveys: List<Survey> = emptyList(),
    val selectedFilter: SyncStatus? = null, // null means show all
    val errorMessage: String? = null
)
