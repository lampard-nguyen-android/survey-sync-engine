package com.survey.sync.engine.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.survey.sync.engine.data.converters.Converters
import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.QuestionDefinitionDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.entity.QuestionDefinitionEntity
import com.survey.sync.engine.data.entity.SurveyEntity

/**
 * Room Database for the Survey Sync Engine.
 *
 * Manages three core tables:
 * - surveys: Survey sessions with sync status
 * - question_definitions: Question metadata for UI rendering
 * - answers: Survey responses with foreign key relationships
 *
 * Key Features:
 * - Cascading deletes from surveys to answers
 * - Idempotent answer insertion via UUID
 * - Supports dynamic repeating sections via instanceIndex
 * - Offline-first with sync status tracking
 */
@Database(
    entities = [
        SurveyEntity::class,
        QuestionDefinitionEntity::class,
        AnswerEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * Provides access to Survey operations.
     */
    abstract fun surveyDao(): SurveyDao

    /**
     * Provides access to QuestionDefinition operations.
     */
    abstract fun questionDefinitionDao(): QuestionDefinitionDao

    /**
     * Provides access to Answer operations.
     */
    abstract fun answerDao(): AnswerDao

    companion object {
        const val DATABASE_NAME = "survey_sync_database"
    }
}
