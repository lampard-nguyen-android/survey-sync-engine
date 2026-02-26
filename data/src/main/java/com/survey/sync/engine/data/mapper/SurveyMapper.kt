package com.survey.sync.engine.data.mapper

import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.entity.SurveyEntity
import com.survey.sync.engine.data.pojo.FullSurveyDetail
import com.survey.sync.engine.data.remote.dto.AnswerDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadDto
import com.survey.sync.engine.data.remote.dto.SurveyUploadResponseDto
import com.survey.sync.engine.domain.model.Answer
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult

/**
 * Extension function to convert SurveyEntity to Domain Survey model.
 */
fun SurveyEntity.toDomain(): Survey {
    return Survey(
        surveyId = surveyId,
        agentId = agentId,
        farmerId = farmerId,
        syncStatus = SyncStatus.valueOf(syncStatus),
        createdAt = createdAt,
        answers = emptyList() // Answers loaded separately via FullSurveyDetail
    )
}

/**
 * Extension function to convert AnswerEntity to Domain Answer model.
 */
fun AnswerEntity.toDomain(): Answer {
    return Answer(
        answerUuid = answerUuid,
        questionKey = questionKey,
        instanceIndex = instanceIndex,
        answerValue = answerValue,
        answeredAt = answeredAt,
        uploadedAt = uploadedAt,
        syncStatus = SyncStatus.valueOf(syncStatus)
    )
}

/**
 * Extension function to convert FullSurveyDetail to Domain Survey model with answers.
 */
fun FullSurveyDetail.toDomain(): Survey {
    return Survey(
        surveyId = survey.surveyId,
        agentId = survey.agentId,
        farmerId = survey.farmerId,
        syncStatus = SyncStatus.valueOf(survey.syncStatus),
        createdAt = survey.createdAt,
        answers = answersWithDefinitions.map { it.answer.toDomain() }
    )
}

/**
 * Extension function to convert Domain Survey to SurveyEntity.
 */
fun Survey.toEntity(): SurveyEntity {
    return SurveyEntity(
        surveyId = surveyId,
        agentId = agentId,
        farmerId = farmerId,
        syncStatus = syncStatus.name,
        createdAt = createdAt
    )
}

/**
 * Extension function to convert Domain Answer to AnswerEntity.
 */
fun Answer.toEntity(parentSurveyId: String): AnswerEntity {
    return AnswerEntity(
        answerUuid = answerUuid,
        parentSurveyId = parentSurveyId,
        questionKey = questionKey,
        instanceIndex = instanceIndex,
        answerValue = answerValue,
        answeredAt = answeredAt,
        uploadedAt = uploadedAt,
        syncStatus = syncStatus.name
    )
}

/**
 * Extension function to convert Domain Survey to DTO for API upload.
 */
fun Survey.toUploadDto(): SurveyUploadDto {
    return SurveyUploadDto(
        surveyId = surveyId,
        agentId = agentId,
        farmerId = farmerId,
        createdAt = createdAt,
        answers = answers.map { it.toDto() }
    )
}

/**
 * Extension function to convert Domain Answer to DTO.
 */
fun Answer.toDto(): AnswerDto {
    return AnswerDto(
        answerUuid = answerUuid,
        questionKey = questionKey,
        instanceIndex = instanceIndex,
        answerValue = answerValue,
        answeredAt = answeredAt
    )
}

/**
 * Extension function to convert AnswerEntity to DTO.
 */
fun AnswerEntity.toDto(): AnswerDto {
    return AnswerDto(
        answerUuid = answerUuid,
        questionKey = questionKey,
        instanceIndex = instanceIndex,
        answerValue = answerValue,
        answeredAt = answeredAt
    )
}

/**
 * Extension function to convert FullSurveyDetail to DTO for API upload.
 */
fun FullSurveyDetail.toUploadDto(): SurveyUploadDto {
    return SurveyUploadDto(
        surveyId = survey.surveyId,
        agentId = survey.agentId,
        farmerId = survey.farmerId,
        createdAt = survey.createdAt,
        answers = answersWithDefinitions.map { it.answer.toDto() }
    )
}

/**
 * Extension function to convert API response DTO to Domain UploadResult.
 */
fun SurveyUploadResponseDto.toDomain(): UploadResult {
    return UploadResult(
        success = success,
        surveyId = surveyId,
        message = message,
        uploadedAt = uploadedAt
    )
}
