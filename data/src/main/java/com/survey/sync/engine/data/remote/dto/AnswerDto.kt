package com.survey.sync.engine.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for survey answers in API requests.
 */
@JsonClass(generateAdapter = true)
data class AnswerDto(
    @Json(name = "answer_uuid")
    val answerUuid: String,

    @Json(name = "question_key")
    val questionKey: String,

    @Json(name = "instance_index")
    val instanceIndex: Int,

    @Json(name = "answer_value")
    val answerValue: String?,

    @Json(name = "answered_at")
    val answeredAt: Long
)
