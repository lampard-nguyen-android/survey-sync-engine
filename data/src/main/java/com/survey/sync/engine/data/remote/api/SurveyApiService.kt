package com.survey.sync.engine.data.remote.api

import com.survey.sync.engine.data.remote.dto.MediaUploadResponseDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit API service for survey operations.
 * This is a dummy service - endpoints don't need to be functional for this exercise.
 */
interface SurveyApiService {

    /**
     * Upload a completed survey with all its answers (text data only, no photos).
     *
     * @param surveyData The survey data including all answers
     * @return Response containing upload confirmation
     */
    @POST("api/v1/surveys/upload")
    suspend fun uploadSurvey(
        @Body surveyData: SurveyUploadDto
    ): Response<SurveyUploadResponseDto>

    /**
     * Upload a media attachment (photo) as multipart/form-data.
     * This is called separately after survey upload for each photo attachment.
     *
     * @param attachmentId Attachment UUID
     * @param surveyId Survey UUID
     * @param answerUuid Answer UUID
     * @param file The photo file as multipart
     * @return Response containing upload confirmation
     */
    @Multipart
    @POST("api/v1/media/upload")
    suspend fun uploadMedia(
        @Part("attachment_id") attachmentId: RequestBody,
        @Part("survey_id") surveyId: RequestBody,
        @Part("answer_uuid") answerUuid: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<MediaUploadResponseDto>

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
