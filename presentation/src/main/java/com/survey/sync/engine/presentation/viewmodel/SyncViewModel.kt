package com.survey.sync.engine.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survey.sync.engine.domain.model.MediaAttachment
import com.survey.sync.engine.domain.usecase.GetMediaAttachmentsUseCase
import com.survey.sync.engine.domain.usecase.GetPendingSurveysUseCase
import com.survey.sync.engine.domain.usecase.UploadSurveyUseCase
import com.survey.sync.engine.presentation.state.SyncResultItem
import com.survey.sync.engine.presentation.state.SyncUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Sync screen.
 * Handles manual sync trigger and displays sync results.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val getPendingSurveysUseCase: GetPendingSurveysUseCase,
    private val uploadSurveyUseCase: UploadSurveyUseCase,
    private val getMediaAttachmentsUseCase: GetMediaAttachmentsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        loadPendingSurveyCount()
    }

    /**
     * Load count of pending surveys.
     */
    fun loadPendingSurveyCount() {
        viewModelScope.launch {
            getPendingSurveysUseCase().fold(
                onSuccess = { surveys ->
                    _uiState.value = _uiState.value.copy(
                        pendingSurveyCount = surveys.size
                    )
                },
                onFailure = { /* Ignore error for count */ }
            )
        }
    }

    /**
     * Trigger manual sync of all pending surveys.
     */
    fun syncPendingSurveys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncResults = emptyList(),
                errorMessage = null
            )

            // Get all pending surveys
            getPendingSurveysUseCase().fold(
                onSuccess = { surveys ->
                    val results = mutableListOf<SyncResultItem>()

                    // Upload each survey sequentially
                    surveys.forEach { survey ->
                        // Get media attachments for this survey
                        val attachments = getMediaAttachmentsForSurvey(survey.surveyId)

                        // Upload survey with attachments
                        uploadSurveyUseCase(
                            survey = survey,
                            mediaAttachments = attachments,
                            cleanupAttachments = true
                        ).fold(
                            onSuccess = { result ->
                                results.add(
                                    SyncResultItem(
                                        surveyId = survey.surveyId,
                                        isSuccess = true,
                                        mediaUploadSuccessCount = result.mediaUploadSuccessCount,
                                        mediaUploadFailureCount = result.mediaUploadFailureCount,
                                        totalMediaCount = result.totalMediaCount
                                    )
                                )
                            },
                            onFailure = { error ->
                                results.add(
                                    SyncResultItem(
                                        surveyId = survey.surveyId,
                                        isSuccess = false,
                                        errorMessage = error.message ?: "Upload failed"
                                    )
                                )
                            }
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncResults = results,
                        pendingSurveyCount = 0 // Will be updated by next load
                    )

                    // Reload pending count
                    loadPendingSurveyCount()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        errorMessage = error.message ?: "Failed to load pending surveys"
                    )
                }
            )
        }
    }

    /**
     * Get media attachments for a survey.
     * This is a simplified version - in production, this would query the database.
     */
    private suspend fun getMediaAttachmentsForSurvey(surveyId: String): List<MediaAttachment> {
        return getMediaAttachmentsUseCase(surveyId).getOrNull() ?: emptyList()
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Clear sync results.
     */
    fun clearResults() {
        _uiState.value = _uiState.value.copy(syncResults = emptyList())
    }
}
