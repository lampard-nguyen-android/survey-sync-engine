package com.survey.sync.engine.data.remote.api

import com.survey.sync.engine.data.remote.dto.MediaUploadResponseDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadResponseDto
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit API service for survey operations.
 * All methods return DomainResult for automatic error handling via DomainResultCallAdapter.
 *
 * The CallAdapter automatically:
 * - Wraps successful responses in DomainResult.Success
 * - Maps HTTP errors (4xx, 5xx) to DomainError.ApiError
 * - Maps network failures to DomainError.NetworkFailure
 * - Maps unexpected exceptions to DomainError.InternalError
 *
 * No manual try-catch needed in repository implementations.
 */
interface SurveyApiService {

    /**
     * Upload a completed survey with all its answers (text data only, no photos).
     *
     * @param surveyData The survey data including all answers
     * @return DomainResult with upload confirmation or error details
     */
    @POST("api/v1/surveys/upload")
    suspend fun uploadSurvey(
        @Body surveyData: SurveyUploadDto
    ): DomainResult<DomainError, SurveyUploadResponseDto>

    /**
     * Upload a media attachment (photo) as multipart/form-data.
     * This is called separately after survey upload for each photo attachment.
     *
     * @param attachmentId Attachment UUID
     * @param surveyId Survey UUID
     * @param answerUuid Answer UUID
     * @param file The photo file as multipart
     * @return DomainResult with upload confirmation or error details
     */
    @Multipart
    @POST("api/v1/media/upload")
    suspend fun uploadMedia(
        @Part("attachment_id") attachmentId: RequestBody,
        @Part("survey_id") surveyId: RequestBody,
        @Part("answer_uuid") answerUuid: RequestBody,
        @Part file: MultipartBody.Part
    ): DomainResult<DomainError, MediaUploadResponseDto>

    /**
     * Batch upload multiple surveys at once.
     *
     * @param surveys List of surveys to upload
     * @return DomainResult with batch upload results or error details
     */
    @POST("api/v1/surveys/batch-upload")
    suspend fun batchUploadSurveys(
        @Body surveys: List<SurveyUploadDto>
    ): DomainResult<DomainError, List<SurveyUploadResponseDto>>

    companion object {
        const val BASE_URL = "https://api.survey-sync.example.com/"
    }
}
