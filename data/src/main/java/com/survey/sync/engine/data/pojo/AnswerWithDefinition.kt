package com.survey.sync.engine.data.pojo

import androidx.room.Embedded
import androidx.room.Relation
import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.entity.QuestionDefinitionEntity

/**
 * Combines an answer with its corresponding question definition.
 * Used to reconstruct the survey structure with metadata for UI rendering.
 */
data class AnswerWithDefinition(
    @Embedded
    val answer: AnswerEntity,

    @Relation(
        parentColumn = "questionKey",
        entityColumn = "questionKey"
    )
    val definition: QuestionDefinitionEntity
)
