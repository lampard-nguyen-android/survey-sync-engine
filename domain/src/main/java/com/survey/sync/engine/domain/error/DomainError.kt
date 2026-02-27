package com.survey.sync.engine.domain.error

/**
 * Error codes for SurveySyncEngine failure scenarios.
 */
const val SYNC_ERROR_SYSTEM = "-100"
const val SYNC_ERROR_NO_NETWORK = "-107"
const val SYNC_ERROR_DNS = "-101"
const val SYNC_ERROR_CONNECTION_TIMEOUT = "-102"
const val SYNC_ERROR_SOCKET_TIMEOUT = "-103"
const val SYNC_ERROR_SOCKET = "-104"
const val SYNC_ERROR_SSL_HANDSHAKE = "-105"
const val SYNC_ERROR_DATABASE = "-108"
const val SYNC_ERROR_UNKNOWN = "-109"

/**
 * Error representation for SurveySyncEngine operations.
 *
 * Handles various field survey sync scenarios in rural areas:
 * - No internet connection at all
 * - Connection timeout (slow network in rural areas)
 * - Server returns an error (400, 500 status codes)
 * - Unknown/unexpected exceptions
 *
 * Provides clear guidance for WorkManager retry strategy:
 * - Retryable errors: Temporary issues that may resolve (network problems, server errors)
 * - Non-retryable errors: Permanent issues with the request (bad data, validation failures)
 */
sealed class DomainError {

    /**
     * Unique error code for tracking and debugging survey sync failures.
     */
    abstract val errorCode: String

    /**
     * Human-readable error message describing what went wrong.
     */
    abstract val errorMessage: String

    /**
     * Determines WorkManager retry behavior for survey uploads.
     * - true: Temporary failure, retry with exponential backoff
     * - false: Permanent failure, mark survey as FAILED, don't retry
     */
    abstract val isRetryable: Boolean

    /**
     * HTTP/API error from survey upload server.
     * Represents failures from SurveyApiService calls.
     *
     * @param httpCode HTTP status code from server response
     * @param httpMessage HTTP status message
     * @param responseBody Parsed error response from server API
     */
    data class ApiError(
        val httpCode: Int,
        val httpMessage: String,
        val responseBody: SurveyServerError? = null
    ) : DomainError() {

        override val errorCode: String
            get() = responseBody?.errorCode ?: httpCode.toString()

        override val errorMessage: String
            get() = responseBody?.message ?: httpMessage

        /**
         * Survey upload retry strategy based on HTTP status:
         * - 5xx (500-599): Server-side issue, retry later when server recovers
         * - 408: Request timeout, retry (poor rural network)
         * - 429: Rate limited, retry with backoff
         * - 4xx (400-499): Client-side issue (bad survey data), don't retry
         */
        override val isRetryable: Boolean
            get() = when (httpCode) {
                in 500..599 -> true // Server errors - retry when server recovers
                408 -> true // Request timeout - retry with better connection
                429 -> true // Rate limited - retry after delay
                in 400..499 -> false // Client errors - survey data issue, don't retry
                else -> false
            }

        // Helper properties for specific HTTP status codes
        val isServerError: Boolean get() = httpCode in 500..599
        val isClientError: Boolean get() = httpCode in 400..499
        val isUnauthorized: Boolean get() = httpCode == 401
        val isForbidden: Boolean get() = httpCode == 403
        val isNotFound: Boolean get() = httpCode == 404
        val isRateLimited: Boolean get() = httpCode == 429
    }

    /**
     * Network connectivity failures in field conditions.
     * Always retryable - field agents may regain connectivity later.
     *
     * Common in rural areas:
     * - No signal/WiFi
     * - DNS resolution fails
     * - Connection timeouts due to weak signal
     * - Request timeouts on slow networks
     *
     * @param throwable The underlying network exception from OkHttp/Retrofit
     */
    data class NetworkFailure(val throwable: Throwable) : DomainError() {

        override val errorCode: String
            get() = when (throwable) {
                is OfflineException -> SYNC_ERROR_NO_NETWORK
                is java.net.UnknownHostException -> SYNC_ERROR_DNS
                is java.net.ConnectException -> SYNC_ERROR_CONNECTION_TIMEOUT
                is java.net.SocketTimeoutException -> SYNC_ERROR_SOCKET_TIMEOUT
                is java.net.SocketException -> SYNC_ERROR_SOCKET
                is javax.net.ssl.SSLHandshakeException -> SYNC_ERROR_SSL_HANDSHAKE
                is javax.net.ssl.SSLPeerUnverifiedException -> SYNC_ERROR_SSL_HANDSHAKE
                else -> SYNC_ERROR_UNKNOWN
            }

        override val errorMessage: String
            get() = when (throwable) {
                is OfflineException -> "No internet connection - field agent is offline"
                is java.net.UnknownHostException -> "Cannot reach server - DNS lookup failed"
                is java.net.ConnectException -> "Connection timeout - weak signal or server unreachable"
                is java.net.SocketTimeoutException -> "Upload timed out - slow network or large survey data"
                is java.net.SocketException -> "Network connection interrupted during upload"
                is javax.net.ssl.SSLHandshakeException -> "Secure connection failed - check network settings"
                is javax.net.ssl.SSLPeerUnverifiedException -> "Secure connection failed - SSL certificate issue"
                else -> throwable.message ?: "Unexpected network error during sync"
            }

        /**
         * All network failures are retryable for field survey scenario.
         * Agent may move to area with better connectivity.
         */
        override val isRetryable: Boolean = true

        // Helper properties to identify specific network failure types
        val isOffline: Boolean get() = throwable is OfflineException || throwable is java.net.UnknownHostException
        val isTimeout: Boolean get() = throwable is java.net.SocketTimeoutException || throwable is java.net.ConnectException
        val isSSLError: Boolean get() = throwable is javax.net.ssl.SSLHandshakeException || throwable is javax.net.ssl.SSLPeerUnverifiedException
    }

    /**
     * Unexpected application errors during survey sync.
     * Non-retryable - indicates bugs in sync logic, not network issues.
     *
     * Examples:
     * - NullPointerException in mapper code
     * - IllegalStateException in business logic
     * - JSON serialization errors
     *
     * @param throwable The unexpected exception
     */
    data class InternalError(val throwable: Throwable) : DomainError() {
        override val errorCode: String = SYNC_ERROR_SYSTEM

        override val errorMessage: String
            get() = throwable.message ?: "Internal error in survey sync engine"

        /**
         * Internal errors are NOT retryable.
         * These are bugs that need code fixes, not network improvements.
         */
        override val isRetryable: Boolean = false
    }

    /**
     * Room database operation failures.
     * Can be retryable depending on the cause (disk full vs constraint violation).
     *
     * Examples:
     * - SQLiteConstraintException (unique constraint violation)
     * - SQLiteDiskIOException (disk I/O error)
     * - SQLiteFullException (database or disk is full)
     * - SQLiteDatabaseCorruptException (database file is corrupt)
     *
     * @param throwable The database exception
     * @param operation Description of the database operation that failed
     */
    data class DaoError(
        val throwable: Throwable,
        val operation: String = "database operation"
    ) : DomainError() {
        override val errorCode: String = SYNC_ERROR_DATABASE

        override val errorMessage: String
            get() = "Database error during $operation: ${throwable.message}"

        /**
         * Retry logic for database errors:
         * - Disk full: Not retryable (need to free space)
         * - Constraint violation: Not retryable (data issue)
         * - Disk I/O: Retryable (temporary issue)
         * - Database locked: Retryable (concurrent access)
         * - Corrupt database: Not retryable (need to clear data)
         */
        override val isRetryable: Boolean
            get() = when (throwable::class.simpleName) {
                "SQLiteDiskIOException" -> true // Disk I/O - temporary issue
                "SQLiteDatabaseLockedException" -> true // Database locked - retry
                "SQLiteFullException" -> false // Disk full - need user action
                "SQLiteConstraintException" -> false // Constraint violation - data issue
                "SQLiteDatabaseCorruptException" -> false // Corrupt DB - need to clear
                else -> false // Unknown database error - don't retry
            }

        val isDiskFull: Boolean get() = throwable::class.simpleName == "SQLiteFullException"
        val isConstraintViolation: Boolean get() = throwable::class.simpleName == "SQLiteConstraintException"
        val isCorrupt: Boolean get() = throwable::class.simpleName == "SQLiteDatabaseCorruptException"
    }

    /**
     * Simple error with message for specific sync scenarios.
     * Used when you need custom error handling beyond network/API/internal/database.
     */
    data class ValidationError(
        override val errorCode: String,
        override val errorMessage: String,
        override val isRetryable: Boolean = false
    ) : DomainError()

    /**
     * Fallback for unexpected error states in sync engine.
     * Use this for errors that don't fit other categories.
     *
     * @param message Custom error message describing the unexpected error
     */
    data class UnexpectedError(
        val message: String = "An unexpected error occurred during survey sync"
    ) : DomainError() {
        override val errorCode: String = SYNC_ERROR_UNKNOWN
        override val errorMessage: String = message
        override val isRetryable: Boolean = false
    }
}

/**
 * Thrown when device has no network connectivity.
 * Used to detect offline state before attempting sync.
 */
class OfflineException(message: String = "Device is offline - no network connection") :
    java.io.IOException(message)

/**
 * Server error response from survey upload API.
 * Matches the expected format from the backend.
 */
data class SurveyServerError(
    val statusCode: Int? = null,
    val errorCode: String? = null,
    val message: String? = null,
    val error: String? = null,
    val details: Map<String, Any>? = null
)
