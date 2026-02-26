package com.survey.sync.engine.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.survey.sync.engine.presentation.screen.SubmitSurveyScreen
import com.survey.sync.engine.presentation.screen.SurveyListScreen
import com.survey.sync.engine.presentation.screen.SyncScreen

/**
 * Main navigation composable for the SurveySync app.
 * Defines the navigation graph and routes between screens.
 */
@Composable
fun SurveySyncNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.SurveyList.route
    ) {
        // Survey List Screen (Home)
        composable(Screen.SurveyList.route) {
            SurveyListScreen(
                onNavigateToSubmit = {
                    navController.navigate(Screen.SubmitSurvey.route)
                },
                onNavigateToSync = {
                    navController.navigate(Screen.Sync.route)
                }
            )
        }

        // Submit Survey Screen
        composable(Screen.SubmitSurvey.route) {
            SubmitSurveyScreen(
                onNavigateToList = {
                    navController.popBackStack()
                }
            )
        }

        // Sync Screen
        composable(Screen.Sync.route) {
            SyncScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
