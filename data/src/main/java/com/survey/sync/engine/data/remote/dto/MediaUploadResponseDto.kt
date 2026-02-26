package com.survey.sync.engine.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data Transfer Object for API response after media upload.
 */
@JsonClass(generateAdapter = true)
data class MediaUploadResponseDto(
    @Json(name = "success")
    val success: Boolean,

    @Json(name = "attachment_id")
    val attachmentId: String,

    @Json(name = "uploaded_at")
    val uploadedAt: Long,

    @Json(name = "url")
    val url: String?, // Server URL where photo is stored

    @Json(name = "message")
    val message: String?
)
