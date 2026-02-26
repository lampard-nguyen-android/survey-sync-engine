package com.survey.sync.engine.data.remote.api

import com.survey.sync.engine.data.remote.dto.SurveyUploadDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API service for survey operations.
 * This is a dummy service - endpoints don't need to be functional for this exercise.
 */
interface SurveyApiService {

    /**
     * Upload a completed survey with all its answers.
     *
     * @param surveyData The survey data including all answers
     * @return Response containing upload confirmation
     */
    @POST("api/v1/surveys/upload")
    suspend fun uploadSurvey(
        @Body surveyData: SurveyUploadDto
    ): Response<SurveyUploadResponseDto>

    /**
     * Batch upload multiple surveys at once.
     *
     * @param surveys List of surveys to upload
     * @return Response containing batch upload results
     */
    @POST("api/v1/surveys/batch-upload")
    suspend fun batchUploadSurveys(
        @Body surveys: List<SurveyUploadDto>
    ): Response<List<SurveyUploadResponseDto>>

    companion object {
        const val BASE_URL = "https://api.survey-sync.example.com/"
    }
}
