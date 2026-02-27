package com.survey.sync.engine.data.network

import com.squareup.moshi.Moshi
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

/**
 * Retrofit CallAdapter for DomainResult<DomainError, T>.
 *
 * Converts Retrofit's Call<T> to Call<DomainResult<DomainError, T>>,
 * enabling automatic error handling for all survey sync API calls.
 *
 * Usage in Retrofit interface:
 * ```kotlin
 * interface SurveyApiService {
 *     @POST("api/v1/surveys/upload")
 *     suspend fun uploadSurvey(
 *         @Body surveyData: SurveyUploadDto
 *     ): DomainResult<DomainError, SurveyUploadResponseDto>
 * }
 * ```
 *
 * The CallAdapter automatically:
 * - Wraps successful responses in DomainResult.Success
 * - Maps HTTP errors (4xx, 5xx) to DomainError.ApiError
 * - Maps network failures to DomainError.NetworkFailure
 * - Maps unexpected exceptions to DomainError.InternalError
 *
 * @param responseType The actual response type T (extracted from DomainResult<DomainError, T>)
 * @param moshi Moshi instance for parsing server error responses
 */
internal class DomainResultCallAdapter<T>(
    private val responseType: Type,
    private val moshi: Moshi
) : CallAdapter<T, Call<DomainResult<DomainError, T>>> {

    /**
     * Returns the type that this adapter uses when converting the HTTP response body
     * to a Java object. This is the T in DomainResult<DomainError, T>.
     */
    override fun responseType(): Type = responseType

    /**
     * Adapts a Call<T> to Call<DomainResult<DomainError, T>>.
     * Wraps the original call with DomainResultCall which handles error mapping.
     */
    override fun adapt(call: Call<T>): Call<DomainResult<DomainError, T>> {
        return DomainResultCall(call, moshi)
    }
}
