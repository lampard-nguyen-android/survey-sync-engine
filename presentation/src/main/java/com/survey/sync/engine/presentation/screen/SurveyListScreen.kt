package com.survey.sync.engine.presentation.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.survey.sync.engine.domain.model.Answer
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.presentation.viewmodel.SurveyListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen displaying list of surveys with filter options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyListScreen(
    viewModel: SurveyListViewModel = hiltViewModel(),
    onNavigateToSubmit: () -> Unit = {},
    onNavigateToSync: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Responses")
                        Text(
                            text = "${uiState.surveys.size} surveys",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    // Filter button
                    Box {
                        TextButton(onClick = { showFilterMenu = true }) {
                            Text(
                                text = uiState.selectedFilter?.name ?: "All",
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All") },
                                onClick = {
                                    viewModel.setFilter(null)
                                    showFilterMenu = false
                                }
                            )
                            SyncStatus.values().forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.name) },
                                    onClick = {
                                        viewModel.setFilter(status)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Sync button
                    IconButton(onClick = onNavigateToSync) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToSubmit) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Survey"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { viewModel.loadSurveys() }) {
                            Text("Retry")
                        }
                    }
                }

                uiState.surveys.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No surveys found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Tap + to create a test survey",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.surveys) { survey ->
                            SurveyListItem(survey = survey)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual survey item in the list.
 */
@Composable
private fun SurveyListItem(survey: Survey) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Could navigate to detail screen */ },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Survey ${survey.surveyId.take(8)}...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                SyncStatusChip(status = survey.syncStatus)
            }

            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Agent: ${survey.agentId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Farmer: ${survey.farmerId}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDate(survey.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${survey.answers.size} answers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Chip showing sync status with appropriate colors.
 */
@Composable
private fun SyncStatusChip(status: SyncStatus) {
    val (containerColor, contentColor) = when (status) {
        SyncStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SyncStatus.SYNCING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SyncStatus.SYNCED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        SyncStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Format timestamp to readable date string.
 */
private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

// Previews

@Preview(showBackground = true, name = "With Surveys")
@Composable
private fun SurveyListItemPreview() {
    MaterialTheme {
        val mockSurvey = Survey(
            surveyId = "1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p",
            agentId = "agent_001",
            farmerId = "farmer_001",
            syncStatus = SyncStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            answers = listOf(
                Answer(
                    answerUuid = "answer_1",
                    questionKey = "farmer_name",
                    instanceIndex = 0,
                    answerValue = "John Doe",
                    answeredAt = System.currentTimeMillis(),
                    uploadedAt = null,
                    syncStatus = SyncStatus.PENDING
                )
            )
        )
        SurveyListItem(survey = mockSurvey)
    }
}

@Preview(showBackground = true, name = "Synced Survey")
@Composable
private fun SurveyListItemSyncedPreview() {
    MaterialTheme {
        val mockSurvey = Survey(
            surveyId = "1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p",
            agentId = "agent_002",
            farmerId = "farmer_002",
            syncStatus = SyncStatus.SYNCED,
            createdAt = System.currentTimeMillis() - 86400000, // 1 day ago
            answers = listOf()
        )
        SurveyListItem(survey = mockSurvey)
    }
}

@Preview(showBackground = true, name = "Failed Survey")
@Composable
private fun SurveyListItemFailedPreview() {
    MaterialTheme {
        val mockSurvey = Survey(
            surveyId = "1a2b3c4d-5e6f-7g8h-9i0j-1k2l3m4n5o6p",
            agentId = "agent_003",
            farmerId = "farmer_003",
            syncStatus = SyncStatus.FAILED,
            createdAt = System.currentTimeMillis() - 3600000, // 1 hour ago
            answers = listOf()
        )
        SurveyListItem(survey = mockSurvey)
    }
}
