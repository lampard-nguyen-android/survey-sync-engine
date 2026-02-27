package com.survey.sync.engine.data.util

import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult

/**
 * Utility functions for wrapping Room DAO operations with DomainResult.
 *
 * Provides consistent error handling for all database operations in the SurveySyncEngine.
 * Automatically maps SQLite exceptions to DomainError.DaoError with proper retry logic.
 *
 * Usage in Repository:
 * ```kotlin
 * suspend fun saveSurvey(survey: SurveyEntity): DomainResult<DomainError, Unit> =
 *     safeDaoCall(operation = "saveSurvey") {
 *         surveyDao.insertSurvey(survey)
 *     }
 * ```
 *
 * Handles these database scenarios:
 * - SQLiteDiskIOException: Retryable (temporary I/O issue)
 * - SQLiteDatabaseLockedException: Retryable (database locked by another thread)
 * - SQLiteFullException: Not retryable (disk full - user action needed)
 * - SQLiteConstraintException: Not retryable (constraint violation - data issue)
 * - SQLiteDatabaseCorruptException: Not retryable (database corrupt - needs clear)
 * - Other exceptions: Not retryable by default
 */

/**
 * Wraps a DAO operation in DomainResult with automatic exception mapping.
 *
 * @param operation Description of the operation (e.g., "insertSurvey", "getSurveyById")
 * @param block The DAO operation to execute
 * @return DomainResult.Success with the operation result, or DomainResult.Error with DaoError
 *
 * Example:
 * ```kotlin
 * suspend fun getAllPendingSurveys(): DomainResult<DomainError, List<SurveyEntity>> =
 *     safeDaoCall(operation = "getAllPendingSurveys") {
 *         surveyDao.getSurveysByStatus(SyncStatus.PENDING)
 *     }
 * ```
 */
suspend fun <T> safeDaoCall(
    operation: String,
    block: suspend () -> T
): DomainResult<DomainError, T> {
    return try {
        val result = block()
        DomainResult.success(result)
    } catch (e: Exception) {
        val error = DomainError.DaoError(
            throwable = e,
            operation = operation
        )
        DomainResult.error(error)
    }
}

/**
 * Wraps a non-suspend DAO operation in DomainResult with automatic exception mapping.
 *
 * Use this for blocking DAO operations (rare, most should be suspend).
 *
 * @param operation Description of the operation
 * @param block The DAO operation to execute
 * @return DomainResult.Success with the operation result, or DomainResult.Error with DaoError
 *
 * Example:
 * ```kotlin
 * fun getSurveyCountSync(): DomainResult<DomainError, Int> =
 *     safeDaoCallBlocking(operation = "getSurveyCount") {
 *         surveyDao.getSurveyCountSync()
 *     }
 * ```
 */
fun <T> safeDaoCallBlocking(
    operation: String,
    block: () -> T
): DomainResult<DomainError, T> {
    return try {
        val result = block()
        DomainResult.success(result)
    } catch (e: Exception) {
        val error = DomainError.DaoError(
            throwable = e,
            operation = operation
        )
        DomainResult.error(error)
    }
}

/**
 * Gets a user-friendly message for database errors.
 *
 * @return User-friendly error message appropriate for displaying in the UI
 */
fun DomainError.DaoError.getUserMessage(): String {
    return when (throwable::class.simpleName) {
        "SQLiteDiskIOException" -> "Temporary storage error. Please try again."
        "SQLiteDatabaseLockedException" -> "Database is busy. Please try again."
        "SQLiteFullException" -> "Storage full. Please free up space on your device."
        "SQLiteConstraintException" -> "Invalid data. Please check your input."
        "SQLiteDatabaseCorruptException" -> "Database error. Please contact support."
        else -> "Database error during $operation. Please try again."
    }
}

/**
 * Gets technical details for database errors (for logging).
 *
 * @return Technical error details including exception type and message
 */
fun DomainError.DaoError.getTechnicalDetails(): String {
    return buildString {
        append("DaoError during operation: $operation\n")
        append("Exception type: ${throwable::class.simpleName}\n")
        append("Exception message: ${throwable.message}\n")
        append("Is retryable: $isRetryable\n")
        append("Stack trace:\n${throwable.stackTraceToString()}")
    }
}
