package com.survey.sync.engine.data.network

import com.squareup.moshi.Moshi
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Retrofit CallAdapter.Factory for DomainResult<DomainError, T>.
 *
 * Automatically wraps all Retrofit API calls that return DomainResult in error handling logic.
 * This eliminates the need for manual try-catch blocks in repository implementations.
 *
 * Configuration in NetworkModule:
 * ```kotlin
 * Retrofit.Builder()
 *     .addCallAdapterFactory(DomainResultCallAdapterFactory(moshi))
 *     .build()
 * ```
 *
 * Usage in API service:
 * ```kotlin
 * interface SurveyApiService {
 *     @POST("api/v1/surveys/upload")
 *     suspend fun uploadSurvey(
 *         @Body surveyData: SurveyUploadDto
 *     ): DomainResult<DomainError, SurveyUploadResponseDto>
 * }
 * ```
 *
 * The factory inspects the return type and only provides adapters for
 * Call<DomainResult<DomainError, T>> types. Other return types are handled
 * by Retrofit's default adapters.
 *
 * @param moshi Moshi instance for parsing server error responses
 */
class DomainResultCallAdapterFactory(
    private val moshi: Moshi
) : CallAdapter.Factory() {

    /**
     * Returns a CallAdapter for DomainResult return types, or null for other types.
     *
     * This method is called by Retrofit for each API method to determine which
     * adapter should handle the response.
     */
    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // Check if the return type is Call
        if (getRawType(returnType) != Call::class.java) {
            return null
        }

        // Extract the response type from Call<T>
        val callType = getParameterUpperBound(0, returnType as ParameterizedType)

        // Check if the response type is DomainResult
        if (getRawType(callType) != DomainResult::class.java) {
            return null
        }

        // Ensure DomainResult has two type parameters: DomainResult<E, T>
        if (callType !is ParameterizedType) {
            throw IllegalArgumentException(
                "DomainResult return type must be parameterized as DomainResult<DomainError, T>"
            )
        }

        // Extract the error type (should be DomainError)
        val errorType = getParameterUpperBound(0, callType)
        if (getRawType(errorType) != DomainError::class.java) {
            throw IllegalArgumentException(
                "First type parameter of DomainResult must be DomainError, but was: $errorType"
            )
        }

        // Extract the success response type T from DomainResult<DomainError, T>
        val responseType = getParameterUpperBound(1, callType)

        // Return the adapter for Call<DomainResult<DomainError, T>>
        return DomainResultCallAdapter<Any>(responseType, moshi)
    }
}
