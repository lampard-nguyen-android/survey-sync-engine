package com.survey.sync.engine.data.pojo

import androidx.room.Embedded
import androidx.room.Relation
import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.entity.SurveyEntity

/**
 * Complete survey detail with all answers and their question definitions.
 * Used by the Sync Engine to build nested JSON payloads for upload.
 */
data class FullSurveyDetail(
    @Embedded
    val survey: SurveyEntity,

    @Relation(
        entity = AnswerEntity::class,
        parentColumn = "surveyId",
        entityColumn = "parentSurveyId"
    )
    val answersWithDefinitions: List<AnswerWithDefinition>
)
