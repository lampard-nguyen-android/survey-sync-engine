package com.survey.sync.engine.presentation.navigation

/**
 * Sealed class representing all navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    /**
     * Home screen showing list of surveys with filters.
     */
    data object SurveyList : Screen("survey_list")

    /**
     * Screen for submitting a new survey with test data.
     */
    data object SubmitSurvey : Screen("submit_survey")

    /**
     * Screen for manually triggering sync and viewing sync results.
     */
    data object Sync : Screen("sync")
}
