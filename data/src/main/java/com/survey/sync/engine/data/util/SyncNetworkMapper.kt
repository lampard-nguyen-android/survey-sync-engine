package com.survey.sync.engine.data.util

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.error.OfflineException
import com.survey.sync.engine.domain.error.SurveyServerError
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Network error mapper for SurveySyncEngine.
 *
 * Wraps survey upload API calls and automatically maps exceptions to SyncError.
 * This addresses all network failure scenarios for field survey sync:
 * - Device offline (no internet at all)
 * - Connection timeout (slow network in rural areas)
 * - Request timeout (large survey data on slow connection)
 * - Server errors (400, 500 status codes)
 * - Unexpected exceptions (JSON parsing, null pointer, etc.)
 *
 * Usage in repository:
 * ```kotlin
 * override suspend fun uploadSurvey(data: SurveyUploadDto): ApiResult<SyncError, UploadResponse> =
 *     safeApiCall(moshi) {
 *         apiService.uploadSurvey(data).also { response ->
 *             if (!response.isSuccessful) throw HttpException(response)
 *         }.body()!!
 *     }
 * ```
 */

/**
 * Wrap synchronous survey API calls with error mapping.
 * Converts all exceptions to SyncError for consistent handling.
 *
 * @param moshi Moshi instance for parsing server error responses
 * @param block The API call to execute
 * @return ApiResult.Success with result or ApiResult.Error with mapped SyncError
 */
inline fun <T> safeApiCall(
    moshi: Moshi? = null,
    block: () -> T
): DomainResult<DomainError, T> {
    return try {
        DomainResult.success(block())
    } catch (e: Exception) {
        DomainResult.error(e.toSyncError(moshi))
    }
}

/**
 * Wrap asynchronous (suspend) survey API calls with error mapping.
 * Use this for most Retrofit calls which are suspend functions.
 *
 * @param moshi Moshi instance for parsing server error responses
 * @param block The suspend API call to execute
 * @return ApiResult.Success with result or ApiResult.Error with mapped SyncError
 */
suspend inline fun <T> safeSuspendApiCall(
    moshi: Moshi? = null,
    crossinline block: suspend () -> T
): DomainResult<DomainError, T> {
    return try {
        DomainResult.success(block())
    } catch (e: Exception) {
        DomainResult.error(e.toSyncError(moshi))
    }
}

/**
 * Convert any exception to appropriate SyncError type.
 * Prioritizes network errors (retryable), then HTTP errors, then internal errors.
 *
 * Mapping strategy for field survey sync:
 * 1. Network connectivity exceptions -> NetworkFailure (always retryable)
 * 2. HTTP exceptions -> ApiError (retryable based on status code)
 * 3. JSON parsing errors -> InternalError (not retryable, indicates bug)
 * 4. All other exceptions -> InternalError (not retryable)
 */
fun Exception.toSyncError(moshi: Moshi? = null): DomainError {
    return when (this) {
        // Field connectivity issues - always retry when agent gets signal
        is OfflineException -> DomainError.NetworkFailure(this)
        is UnknownHostException -> DomainError.NetworkFailure(this)
        is ConnectException -> DomainError.NetworkFailure(this)
        is SocketTimeoutException -> DomainError.NetworkFailure(this)
        is SocketException -> DomainError.NetworkFailure(this)
        is SSLHandshakeException -> DomainError.NetworkFailure(this)
        is SSLPeerUnverifiedException -> DomainError.NetworkFailure(this)

        // Server HTTP errors - retry based on status code
        is HttpException -> this.toApiError(moshi)

        // JSON serialization errors - bug in code, don't retry
        is JsonDataException,
        is JsonEncodingException -> DomainError.InternalError(this)

        // Unexpected errors - bug in code, don't retry
        else -> DomainError.InternalError(this)
    }
}

/**
 * Convert Retrofit HttpException to ApiError.
 * Attempts to parse server error response for detailed error info.
 */
private fun HttpException.toApiError(moshi: Moshi?): DomainError.ApiError {
    val httpCode = code()
    val httpMessage = message() ?: "HTTP $httpCode"

    // Try to parse error response body from survey server
    val errorBody = response()?.errorBody()?.string()
    val parsedError = if (errorBody != null && moshi != null) {
        parseServerError(errorBody, moshi)
    } else {
        null
    }

    return DomainError.ApiError(
        httpCode = httpCode,
        httpMessage = httpMessage,
        responseBody = parsedError
    )
}

/**
 * Parse survey server error response JSON.
 * Handles different error formats gracefully - if parsing fails,
 * httpCode and httpMessage are still available.
 */
private fun parseServerError(json: String, moshi: Moshi): SurveyServerError? {
    return try {
        val adapter = moshi.adapter(SurveyServerError::class.java)
        adapter.fromJson(json)
    } catch (e: Exception) {
        // Parsing failed - return null, httpCode/httpMessage still available
        null
    }
}

/**
 * Check if sync error should trigger WorkManager retry.
 * Used by SurveySyncWorker to decide retry strategy.
 *
 * Returns true for:
 * - All network failures (agent may get connectivity later)
 * - 5xx server errors (server may recover)
 * - 408, 429 status codes (timeout, rate limit)
 *
 * Returns false for:
 * - 4xx client errors (bad survey data)
 * - Internal errors (bugs in code)
 */
fun DomainError.shouldRetry(): Boolean = isRetryable

/**
 * Get user-friendly error message for display in UI.
 * Appropriate for showing to field agents.
 */
fun DomainError.getUserMessage(): String {
    return when (this) {
        is DomainError.NetworkFailure -> when {
            isOffline -> "No internet connection. Surveys will sync automatically when you're online."
            isTimeout -> "Connection timed out. Check your signal and try again."
            isSSLError -> "Secure connection failed. Check your network settings or try again later."
            else -> errorMessage
        }

        is DomainError.ApiError -> when {
            isServerError -> "Server error. Your surveys are saved and will sync automatically later."
            isUnauthorized -> "Authentication required. Please log in again."
            isForbidden -> "Access denied. Contact your administrator."
            isNotFound -> "Survey endpoint not found. Update your app."
            isRateLimited -> "Too many requests. Please wait a moment and try again."
            else -> responseBody?.message ?: "Upload failed: $httpMessage"
        }

        is DomainError.DaoError -> when {
            isDiskFull -> "Storage full. Please free up space on your device."
            isConstraintViolation -> "Invalid data. Please check your input."
            isCorrupt -> "Database error. Please contact support."
            else -> "Database is busy. Please try again."
        }

        is DomainError.InternalError -> "An unexpected error occurred. Please report this issue."
        is DomainError.ValidationError -> errorMessage
        is DomainError.UnexpectedError -> "Something went wrong. Please try again."
    }
}

/**
 * Get technical error details for logging and debugging.
 * Include this in crash reports and analytics.
 */
fun DomainError.getTechnicalDetails(): String {
    return when (this) {
        is DomainError.NetworkFailure ->
            "NetworkFailure[$errorCode]: ${throwable.javaClass.simpleName} - ${throwable.message}"

        is DomainError.ApiError ->
            "ApiError[$errorCode]: HTTP $httpCode - $httpMessage - ServerError: ${responseBody?.errorCode}"

        is DomainError.DaoError ->
            "DaoError[$errorCode]: Operation '$operation' - ${throwable.javaClass.simpleName} - ${throwable.message}"

        is DomainError.InternalError ->
            "InternalError[$errorCode]: ${throwable.javaClass.simpleName} - ${throwable.message}\n${throwable.stackTraceToString()}"

        is DomainError.ValidationError ->
            "ValidationError[$errorCode]: $errorMessage"

        is DomainError.UnexpectedError ->
            "UnexpectedError[$errorCode]: $errorMessage"
    }
}

/**
 * Determine if error is network-related (vs application error).
 * Useful for showing appropriate UI feedback.
 */
fun DomainError.isNetworkIssue(): Boolean = this is DomainError.NetworkFailure

/**
 * Determine if error is server-related (vs client/application error).
 * Useful for error categorization in analytics.
 */
fun DomainError.isServerIssue(): Boolean =
    this is DomainError.ApiError && isServerError
