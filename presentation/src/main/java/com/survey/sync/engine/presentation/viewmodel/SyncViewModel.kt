package com.survey.sync.engine.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survey.sync.engine.domain.sync.SyncScheduler
import com.survey.sync.engine.domain.usecase.GetPendingSurveysUseCase
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
 * Handles manual sync trigger via SyncScheduler and displays sync results.
 *
 * Addresses Scenario 4: Concurrent Sync Prevention
 * - Uses SyncScheduler interface backed by WorkManager
 * - WorkManager uses ExistingWorkPolicy.KEEP to prevent duplicate syncs
 * - If a sync is already running (background or UI-triggered), new requests are ignored
 * - Observes sync status to show real-time progress to user
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val getPendingSurveysUseCase: GetPendingSurveysUseCase,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SyncUiState())
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    init {
        loadPendingSurveyCount()
        observeSyncStatus()
    }

    /**
     * Load count of pending surveys.
     */
    fun loadPendingSurveyCount() {
        viewModelScope.launch {
            getPendingSurveysUseCase().handle(
                onError = { /* Ignore error for count */ },
                onSuccess = { surveys ->
                    _uiState.value = _uiState.value.copy(
                        pendingSurveyCount = surveys.size
                    )
                }
            )
        }
    }

    /**
     * Trigger manual sync via WorkManager.
     *
     * Scenario 4 Protection:
     * - If a sync is already running, WorkManager will KEEP the existing work
     * - The new request will be ignored, preventing corruption or duplication
     * - UI will show that sync is already in progress
     */
    fun syncPendingSurveys() {
        viewModelScope.launch {
            // Check if sync is already running
            val isRunning = syncScheduler.isSyncRunning()

            if (isRunning) {
                // Sync already in progress - show message to user
                _uiState.value = _uiState.value.copy(
                    isSyncing = true,
                    errorMessage = "Sync already in progress. Please wait..."
                )
                return@launch
            }

            // Trigger sync via SyncScheduler
            // WorkManager will handle concurrent prevention automatically
            syncScheduler.triggerImmediateSync()

            // UI state will be updated by observeSyncStatus()
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                syncResults = emptyList(),
                errorMessage = null
            )
        }
    }

    /**
     * Observe sync status and update UI accordingly.
     * This monitors both background periodic syncs and manual UI syncs.
     */
    private fun observeSyncStatus() {
        viewModelScope.launch {
            // Poll sync status periodically
            // In production, this could use WorkManager LiveData/Flow observers
            while (true) {
                val isRunning = syncScheduler.isSyncRunning()

                if (_uiState.value.isSyncing && !isRunning) {
                    // Sync just completed - load results
                    loadSyncResults()
                }

                // Update syncing state
                if (_uiState.value.isSyncing != isRunning) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = isRunning
                    )
                }

                kotlinx.coroutines.delay(1000) // Poll every second
            }
        }
    }

    /**
     * Load sync results from SyncScheduler output.
     */
    private suspend fun loadSyncResults() {
        val lastResult = syncScheduler.getLastSyncResult()

        if (lastResult != null) {
            // Convert WorkManager result to UI state
            // Note: WorkManager doesn't provide per-survey details
            // so we show a summary result
            val summary = SyncResultItem(
                surveyId = "SUMMARY",
                isSuccess = lastResult.failureCount == 0,
                mediaUploadSuccessCount = 0,
                mediaUploadFailureCount = 0,
                totalMediaCount = 0,
                errorMessage = lastResult.errorMessage
            )

            _uiState.value = _uiState.value.copy(
                syncResults = listOf(summary),
                errorMessage = lastResult.errorMessage
            )

            // Reload pending count
            loadPendingSurveyCount()
        }
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

    /**
     * Cancel any running sync work.
     * Use with caution - only for user-initiated cancellation.
     */
    fun cancelSync() {
        viewModelScope.launch {
            syncScheduler.cancelAllSync()
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                errorMessage = "Sync cancelled by user"
            )
        }
    }
}
