package com.survey.sync.engine.data.network

import com.squareup.moshi.Moshi
import com.survey.sync.engine.data.util.toSyncError
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Retrofit Call wrapper that converts API responses to DomainResult<DomainError, T>.
 *
 * This eliminates the need for manual error handling in every API call.
 * All survey upload API calls automatically return DomainResult with proper
 * error mapping for network failures, HTTP errors, and unexpected exceptions.
 *
 * Benefits for SurveySyncEngine:
 * - Consistent error handling across all API calls
 * - Automatic retry strategy based on DomainError.isRetryable
 * - No need to wrap each API call with try-catch
 * - Type-safe error handling with sealed class
 *
 * @param delegate The original Retrofit Call to wrap
 * @param moshi Moshi instance for parsing server error responses
 */
internal class DomainResultCall<T>(
    private val delegate: Call<T>,
    private val moshi: Moshi
) : Call<DomainResult<DomainError, T>> {

    override fun enqueue(callback: Callback<DomainResult<DomainError, T>>) {
        delegate.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                val domainResult = response.toDomainResult(moshi)
                callback.onResponse(this@DomainResultCall, Response.success(domainResult))
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                // Network failure, timeout, or unexpected error
                val error = (t as? Exception)?.toSyncError(moshi)
                    ?: DomainError.InternalError(Exception(t))
                val domainResult = DomainResult.error<DomainError>(error)
                callback.onResponse(this@DomainResultCall, Response.success(domainResult))
            }
        })
    }

    override fun execute(): Response<DomainResult<DomainError, T>> {
        return try {
            val response = delegate.execute()
            Response.success(response.toDomainResult(moshi))
        } catch (e: Exception) {
            val error = e.toSyncError(moshi)
            Response.success(DomainResult.error<DomainError>(error))
        }
    }

    override fun isExecuted(): Boolean = delegate.isExecuted

    override fun cancel() = delegate.cancel()

    override fun isCanceled(): Boolean = delegate.isCanceled

    override fun clone(): Call<DomainResult<DomainError, T>> =
        DomainResultCall(delegate.clone(), moshi)

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()
}

/**
 * Convert Retrofit Response to DomainResult.
 * Handles both successful and error HTTP responses.
 */
private fun <T> Response<T>.toDomainResult(moshi: Moshi): DomainResult<DomainError, T> {
    return if (isSuccessful) {
        val body = body()
        if (body != null) {
            // Successful response with body
            DomainResult.success(body)
        } else {
            // Successful response but null body (e.g., 204 No Content)
            // For survey sync, this might indicate empty response which could be an error
            DomainResult.error(
                DomainError.ValidationError(
                    errorCode = code().toString(),
                    errorMessage = "Server returned empty response"
                )
            )
        }
    } else {
        // HTTP error response (4xx, 5xx)
        val httpCode = code()
        val httpMessage = message()
        val errorBody = errorBody()?.string()

        // Parse server error response if available
        val serverError = if (errorBody != null) {
            try {
                val adapter =
                    moshi.adapter(com.survey.sync.engine.domain.error.SurveyServerError::class.java)
                adapter.fromJson(errorBody)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        DomainResult.error(
            DomainError.ApiError(
                httpCode = httpCode,
                httpMessage = httpMessage,
                responseBody = serverError
            )
        )
    }
}
