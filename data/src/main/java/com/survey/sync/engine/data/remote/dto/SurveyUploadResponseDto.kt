package com.survey.sync.engine.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for API response after survey upload.
 */
@JsonClass(generateAdapter = true)
data class SurveyUploadResponseDto(
    @Json(name = "success")
    val success: Boolean,

    @Json(name = "survey_id")
    val surveyId: String,

    @Json(name = "message")
    val message: String?,

    @Json(name = "uploaded_at")
    val uploadedAt: Long
)
