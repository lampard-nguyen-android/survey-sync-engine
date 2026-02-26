package com.survey.sync.engine.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for uploading complete survey with answers.
 */
@JsonClass(generateAdapter = true)
data class SurveyUploadDto(
    @Json(name = "survey_id")
    val surveyId: String,

    @Json(name = "agent_id")
    val agentId: String,

    @Json(name = "farmer_id")
    val farmerId: String,

    @Json(name = "created_at")
    val createdAt: Long,

    @Json(name = "answers")
    val answers: List<AnswerDto>
)
