package com.survey.sync.engine.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.usecase.GetSurveysByStatusUseCase
import com.survey.sync.engine.presentation.state.SurveyListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Survey List screen.
 * Handles loading and filtering surveys by sync status.
 */
@HiltViewModel
class SurveyListViewModel @Inject constructor(
    private val getSurveysByStatusUseCase: GetSurveysByStatusUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyListUiState())
    val uiState: StateFlow<SurveyListUiState> = _uiState.asStateFlow()

    init {
        loadSurveys()
    }

    /**
     * Load surveys with optional status filter.
     */
    fun loadSurveys(status: SyncStatus? = _uiState.value.selectedFilter) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                selectedFilter = status
            )

            getSurveysByStatusUseCase(status).handle(
                onSuccess = { surveys ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        surveys = surveys
                    )
                },
                onError = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.errorMessage
                    )
                }
            )
        }
    }

    /**
     * Update the filter and reload surveys.
     */
    fun setFilter(status: SyncStatus?) {
        loadSurveys(status)
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
