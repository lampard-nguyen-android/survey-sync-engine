package com.survey.sync.engine.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.survey.sync.engine.domain.model.Answer
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.usecase.SaveSurveyUseCase
import com.survey.sync.engine.presentation.state.SubmitSurveyUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Submit Survey screen.
 * Handles creating and saving test survey data.
 */
@HiltViewModel
class SubmitSurveyViewModel @Inject constructor(
    private val saveSurveyUseCase: SaveSurveyUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitSurveyUiState())
    val uiState: StateFlow<SubmitSurveyUiState> = _uiState.asStateFlow()

    /**
     * Update agent ID.
     */
    fun updateAgentId(agentId: String) {
        _uiState.value = _uiState.value.copy(agentId = agentId)
    }

    /**
     * Update farmer ID.
     */
    fun updateFarmerId(farmerId: String) {
        _uiState.value = _uiState.value.copy(farmerId = farmerId)
    }

    /**
     * Update photo count.
     */
    fun updatePhotoCount(count: Int) {
        _uiState.value = _uiState.value.copy(photoCount = count.coerceIn(0, 10))
    }

    /**
     * Submit a test survey with generated data.
     */
    fun submitTestSurvey() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmitting = true,
                successMessage = null,
                errorMessage = null
            )

            try {
                val survey = createTestSurvey(
                    agentId = _uiState.value.agentId,
                    farmerId = _uiState.value.farmerId,
                    photoCount = _uiState.value.photoCount
                )

                saveSurveyUseCase(survey).handle(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            successMessage = "Survey saved successfully! ID: ${
                                survey.surveyId.take(
                                    8
                                )
                            }..."
                        )
                    },
                    onError = { error ->
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            errorMessage = error.errorMessage
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = e.message ?: "Unexpected error occurred"
                )
            }
        }
    }

    /**
     * Create a test survey with sample data.
     */
    private fun createTestSurvey(
        agentId: String,
        farmerId: String,
        photoCount: Int
    ): Survey {
        val surveyId = UUID.randomUUID().toString()
        val currentTime = System.currentTimeMillis()

        // Create test answers
        val answers = mutableListOf<Answer>()

        // General section answers (instanceIndex = 0)
        answers.add(
            Answer(
                answerUuid = UUID.randomUUID().toString(),
                questionKey = "farmer_name",
                instanceIndex = 0,
                answerValue = "John Doe",
                answeredAt = currentTime,
                uploadedAt = null,
                syncStatus = SyncStatus.PENDING
            )
        )
        answers.add(
            Answer(
                answerUuid = UUID.randomUUID().toString(),
                questionKey = "farmer_phone",
                instanceIndex = 0,
                answerValue = "+1234567890",
                answeredAt = currentTime,
                uploadedAt = null,
                syncStatus = SyncStatus.PENDING
            )
        )

        // Farm section answers (repeating, instanceIndex = 1, 2, ...)
        for (i in 1..2) {
            answers.add(
                Answer(
                    answerUuid = UUID.randomUUID().toString(),
                    questionKey = "farm_size_acres",
                    instanceIndex = i,
                    answerValue = "${(10..100).random()}",
                    answeredAt = currentTime,
                    uploadedAt = null,
                    syncStatus = SyncStatus.PENDING
                )
            )
            answers.add(
                Answer(
                    answerUuid = UUID.randomUUID().toString(),
                    questionKey = "crop_type",
                    instanceIndex = i,
                    answerValue = listOf("Wheat", "Corn", "Rice", "Soybeans").random(),
                    answeredAt = currentTime,
                    uploadedAt = null,
                    syncStatus = SyncStatus.PENDING
                )
            )

            // Add photo answers
            if (photoCount > 0 && i <= photoCount) {
                answers.add(
                    Answer(
                        answerUuid = UUID.randomUUID().toString(),
                        questionKey = "farm_photo",
                        instanceIndex = i,
                        answerValue = "/data/local/photos/farm_${i}_${currentTime}.jpg",
                        answeredAt = currentTime,
                        uploadedAt = null,
                        syncStatus = SyncStatus.PENDING
                    )
                )
            }
        }

        return Survey(
            surveyId = surveyId,
            agentId = agentId,
            farmerId = farmerId,
            syncStatus = SyncStatus.PENDING,
            createdAt = currentTime,
            answers = answers
        )
    }

    /**
     * Clear messages.
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }
}
