package com.survey.sync.engine.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for uploading media attachments.
 * Used for multipart/form-data uploads with photo files.
 */
@JsonClass(generateAdapter = true)
data class MediaUploadDto(
    @Json(name = "attachment_id")
    val attachmentId: String,

    @Json(name = "survey_id")
    val surveyId: String,

    @Json(name = "answer_uuid")
    val answerUuid: String,

    @Json(name = "file_name")
    val fileName: String,

    @Json(name = "file_size")
    val fileSize: Long,

    @Json(name = "mime_type")
    val mimeType: String = "image/jpeg"
)
