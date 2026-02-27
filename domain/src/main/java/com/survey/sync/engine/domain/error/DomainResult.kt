package com.survey.sync.engine.domain.error

/**
 * Result wrapper for SurveySyncEngine API operations.
 * Represents either a successful result or a sync error.
 *
 * This functional approach to error handling forces explicit handling of failures,
 * preventing silent errors in survey upload operations.
 *
 * Key benefits for field survey sync:
 * - Type-safe: Compiler ensures errors are handled
 * - Explicit: No hidden exceptions or null values
 * - Composable: Chain operations with map/flatMap
 * - Testable: Easy to mock success and failure scenarios
 *
 * Example - Repository layer:
 * ```kotlin
 * suspend fun uploadSurvey(survey: Survey): ApiResult<UploadResult> {
 *     return safeSuspendNetworkCall(moshi) {
 *         val response = apiService.uploadSurvey(survey.toDto())
 *         response.toDomain()
 *     }
 * }
 * ```
 *
 * Example - Use case layer:
 * ```kotlin
 * val result: ApiResult<UploadResult> = repository.uploadSurvey(survey)
 * result.handle(
 *     onError = { error ->
 *         if (error.isRetryable) scheduleRetry() else markAsFailed()
 *     },
 *     onSuccess = { result ->
 *         markSurveyAsSynced()
 *         cleanupLocalPhotos()
 *     }
 * )
 * ```
 */
sealed class DomainResult<out E : DomainError, out V> {

    /**
     * Represents a failed operation with error details.
     * Survey upload failed - agent needs to know why and whether to retry.
     */
    data class Error<out E : DomainError>(val error: E) : DomainResult<E, Nothing>()

    /**
     * Represents a successful operation with result value.
     * Survey uploaded successfully - can proceed with cleanup.
     */
    data class Success<out V>(val value: V) : DomainResult<Nothing, V>()

    /**
     * Check if this result represents a successful survey sync.
     */
    val isSuccess: Boolean
        get() = this is Success

    /**
     * Check if this result represents a failed survey sync.
     */
    val isError: Boolean
        get() = this is Error

    /**
     * Handle both success and error cases for survey operations.
     * Forces explicit handling of all outcomes.
     *
     * @param onError Handler for sync errors (network, API, internal)
     * @param onSuccess Handler for successful sync
     * @return Result of the handler function
     */
    inline fun <R> handle(
        onError: (E) -> R,
        onSuccess: (V) -> R
    ): R = when (this) {
        is Error -> onError(error)
        is Success -> onSuccess(value)
    }

    /**
     * Transform the success value while preserving errors.
     * Useful for converting DTOs to domain models after successful upload.
     *
     * Example:
     * ```kotlin
     * val uploadResult: ApiResult<SyncError, UploadResponseDto> = repository.upload(survey)
     * val domainResult: ApiResult<SyncError, UploadResult> = uploadResult.map { dto ->
     *     dto.toDomain()
     * }
     * ```
     *
     * @param transform Function to transform success value
     * @return ApiResult with transformed success or same error
     */
    inline fun <R> map(transform: (V) -> R): DomainResult<E, R> = when (this) {
        is Error -> Error(error)
        is Success -> Success(transform(value))
    }

    /**
     * Transform the error while preserving success.
     * Useful for adding context to errors.
     *
     * @param transform Function to transform error
     * @return ApiResult with transformed error or same success
     */
    inline fun <R : DomainError> mapError(transform: (E) -> R): DomainResult<R, V> = when (this) {
        is Error -> Error(transform(error))
        is Success -> Success(value)
    }

    /**
     * Chain operations that return ApiResult.
     * Stops on first error, continues on success.
     *
     * Example - sequential survey upload operations:
     * ```kotlin
     * repository.uploadSurvey(survey)
     *     .flatMap { result -> repository.uploadMediaAttachments(survey.id) }
     *     .flatMap { mediaResult -> repository.cleanupLocalFiles(survey.id) }
     * ```
     *
     * @param transform Function that returns another ApiResult
     * @return ApiResult from transformation or original error
     */
    inline fun <R> flatMap(transform: (V) -> DomainResult<@UnsafeVariance E, R>): DomainResult<E, R> =
        when (this) {
            is Error -> Error(error)
            is Success -> transform(value)
        }

    /**
     * Get success value or null if error.
     * Use when you need optional value from survey operation.
     */
    fun getOrNull(): V? = when (this) {
        is Error -> null
        is Success -> value
    }

    /**
     * Get error or null if success.
     * Useful for logging failed survey uploads.
     */
    fun errorOrNull(): E? = when (this) {
        is Error -> error
        is Success -> null
    }

    /**
     * Get success value or provide default.
     * Use for non-critical survey data that can have fallbacks.
     */
    fun getOrElse(default: @UnsafeVariance V): V = when (this) {
        is Error -> default
        is Success -> value
    }

    /**
     * Get success value or compute default from error.
     * Useful when default depends on error type.
     */
    inline fun getOrElse(default: (E) -> @UnsafeVariance V): V = when (this) {
        is Error -> default(error)
        is Success -> value
    }

    /**
     * Execute side effect on successful survey sync.
     * Returns same ApiResult for further chaining.
     */
    inline fun onSuccess(action: (V) -> Unit): DomainResult<E, V> {
        if (this is Success) action(value)
        return this
    }

    /**
     * Execute side effect on failed survey sync.
     * Useful for logging or analytics.
     * Returns same ApiResult for further chaining.
     */
    inline fun onError(action: (E) -> Unit): DomainResult<E, V> {
        if (this is Error) action(error)
        return this
    }

    companion object {
        /**
         * Create successful ApiResult for survey operation.
         */
        fun <V> success(value: V): DomainResult<Nothing, V> = Success(value)

        /**
         * Create failed ApiResult for survey operation.
         */
        fun <E : DomainError> error(error: E): DomainResult<E, Nothing> = Error(error)

        /**
         * Create ApiResult from nullable value.
         * Null becomes an error with provided SyncError.
         */
        fun <V> fromNullable(value: V?, error: DomainError): DomainResult<DomainError, V> =
            if (value != null) Success(value) else Error(error)

        /**
         * Wrap risky code block in ApiResult.
         * Catches exceptions and converts to InternalError.
         *
         * @param block Code that might throw exceptions
         * @return Success if no exception, Error with InternalError if throws
         */
        inline fun <V> attempt(block: () -> V): DomainResult<DomainError, V> =
            try {
                Success(block())
            } catch (e: Exception) {
                Error(DomainError.InternalError(e))
            }
    }
}

/**
 * Create successful ApiResult for survey sync.
 */
fun <V> success(value: V): DomainResult<Nothing, V> = DomainResult.Success(value)

/**
 * Create failed ApiResult for survey sync.
 */
fun <E : DomainError> error(error: E): DomainResult<E, Nothing> = DomainResult.Error(error)

/**
 * Convert Kotlin Result to ApiResult.
 * Bridges standard library Result with SurveySyncEngine ApiResult.
 */
fun <V> Result<V>.toApiResult(): DomainResult<DomainError, V> =
    fold(
        onSuccess = { DomainResult.Success(it) },
        onFailure = {
            DomainResult.Error(
                DomainError.InternalError(
                    it as? Exception ?: Exception(it.message)
                )
            )
        }
    )

/**
 * Convert ApiResult to Kotlin Result.
 * Useful when interfacing with standard library APIs.
 */
fun <V> DomainResult<DomainError, V>.toResult(): Result<V> =
    handle(
        onError = { Result.failure(Exception(it.errorMessage)) },
        onSuccess = { Result.success(it) }
    )
