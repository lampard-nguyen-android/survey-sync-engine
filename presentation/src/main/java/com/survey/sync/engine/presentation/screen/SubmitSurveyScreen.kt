package com.survey.sync.engine.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survey.sync.engine.presentation.state.SubmitSurveyUiState
import com.survey.sync.engine.presentation.viewmodel.SubmitSurveyViewModel

/**
 * Screen for submitting test survey data.
 * Allows user to configure test data parameters and create surveys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitSurveyScreen(
    viewModel: SubmitSurveyViewModel = hiltViewModel(),
    onNavigateToList: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SubmitSurveyScreenContent(
        uiState = uiState,
        onAgentIdChange = { viewModel.updateAgentId(it) },
        onFarmerIdChange = { viewModel.updateFarmerId(it) },
        onPhotoCountChange = { viewModel.updatePhotoCount(it) },
        onSubmit = { viewModel.submitTestSurvey() },
        onClearMessages = { viewModel.clearMessages() },
        onNavigateToList = onNavigateToList
    )
}

/**
 * Stateless content composable for preview and testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmitSurveyScreenContent(
    uiState: SubmitSurveyUiState,
    onAgentIdChange: (String) -> Unit = {},
    onFarmerIdChange: (String) -> Unit = {},
    onPhotoCountChange: (Int) -> Unit = {},
    onSubmit: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onNavigateToList: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submit Test Survey") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Agent ID input
            OutlinedTextField(
                value = uiState.agentId,
                onValueChange = onAgentIdChange,
                label = { Text("Agent ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting,
                singleLine = true
            )

            // Farmer ID input
            OutlinedTextField(
                value = uiState.farmerId,
                onValueChange = onFarmerIdChange,
                label = { Text("Farmer ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting,
                singleLine = true
            )

            // Photo count input
            OutlinedTextField(
                value = uiState.photoCount.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { count -> onPhotoCountChange(count) }
                },
                label = { Text("Number of Photos (0-10)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Test Survey Details:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Creates a survey with 2 general questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Creates 2 farm sections (repeating)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Adds specified number of photo attachments",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• Sets status to PENDING",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Submit button
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting &&
                        uiState.agentId.isNotBlank() &&
                        uiState.farmerId.isNotBlank()
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isSubmitting) "Submitting..." else "Create Test Survey")
            }

            // View surveys button
            OutlinedButton(
                onClick = onNavigateToList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View All Surveys")
            }

            // Success message
            uiState.successMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearMessages) {
                            Text("Dismiss")
                        }
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearMessages) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

// Previews

@Preview(showBackground = true, name = "Default State")
@Composable
private fun SubmitSurveyScreenPreview() {
    MaterialTheme {
        SubmitSurveyScreenContent(
            uiState = SubmitSurveyUiState(
                agentId = "agent_001",
                farmerId = "farmer_001",
                photoCount = 2
            )
        )
    }
}

@Preview(showBackground = true, name = "Submitting State")
@Composable
private fun SubmitSurveyScreenSubmittingPreview() {
    MaterialTheme {
        SubmitSurveyScreenContent(
            uiState = SubmitSurveyUiState(
                agentId = "agent_001",
                farmerId = "farmer_001",
                photoCount = 2,
                isSubmitting = true
            )
        )
    }
}

@Preview(showBackground = true, name = "Success State")
@Composable
private fun SubmitSurveyScreenSuccessPreview() {
    MaterialTheme {
        SubmitSurveyScreenContent(
            uiState = SubmitSurveyUiState(
                agentId = "agent_001",
                farmerId = "farmer_001",
                photoCount = 2,
                successMessage = "Survey saved successfully! ID: 1a2b3c4d..."
            )
        )
    }
}

@Preview(showBackground = true, name = "Error State")
@Composable
private fun SubmitSurveyScreenErrorPreview() {
    MaterialTheme {
        SubmitSurveyScreenContent(
            uiState = SubmitSurveyUiState(
                agentId = "agent_001",
                farmerId = "farmer_001",
                photoCount = 2,
                errorMessage = "Failed to save survey: Database error"
            )
        )
    }
}
