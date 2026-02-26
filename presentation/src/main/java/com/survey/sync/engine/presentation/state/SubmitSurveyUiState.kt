package com.survey.sync.engine.presentation.state

/**
 * UI state for the Submit Survey screen.
 */
data class SubmitSurveyUiState(
    val isSubmitting: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val agentId: String = "agent_001",
    val farmerId: String = "farmer_001",
    val photoCount: Int = 2 // Number of test photos to generate
)
