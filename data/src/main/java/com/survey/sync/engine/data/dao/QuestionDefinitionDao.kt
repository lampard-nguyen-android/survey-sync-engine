package com.survey.sync.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.survey.sync.engine.data.entity.QuestionDefinitionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QuestionDefinition operations.
 * Handles question metadata for UI rendering and validation.
 */
@Dao
interface QuestionDefinitionDao {

    /**
     * Insert a question definition or replace if it already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestionDefinition(question: QuestionDefinitionEntity)

    /**
     * Insert multiple question definitions (typically during app initialization).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllQuestionDefinitions(questions: List<QuestionDefinitionEntity>)

    /**
     * Get all questions for a specific section, ordered by sortOrder.
     */
    @Query("SELECT * FROM question_definitions WHERE sectionType = :sectionType ORDER BY sortOrder ASC")
    suspend fun getQuestionsBySection(sectionType: String): List<QuestionDefinitionEntity>

    /**
     * Get all questions for a specific section as Flow.
     */
    @Query("SELECT * FROM question_definitions WHERE sectionType = :sectionType ORDER BY sortOrder ASC")
    fun observeQuestionsBySection(sectionType: String): Flow<List<QuestionDefinitionEntity>>

    /**
     * Get a specific question by its key.
     */
    @Query("SELECT * FROM question_definitions WHERE questionKey = :key")
    suspend fun getQuestionByKey(key: String): QuestionDefinitionEntity?

    /**
     * Get all repeating questions (for dynamic section handling).
     */
    @Query("SELECT * FROM question_definitions WHERE isRepeating = 1 ORDER BY sectionType, sortOrder")
    suspend fun getRepeatingQuestions(): List<QuestionDefinitionEntity>

    /**
     * Get all non-repeating questions.
     */
    @Query("SELECT * FROM question_definitions WHERE isRepeating = 0 ORDER BY sectionType, sortOrder")
    suspend fun getNonRepeatingQuestions(): List<QuestionDefinitionEntity>

    /**
     * Get all question definitions.
     */
    @Query("SELECT * FROM question_definitions ORDER BY sectionType, sortOrder")
    suspend fun getAllQuestions(): List<QuestionDefinitionEntity>

    /**
     * Get all question definitions as Flow.
     */
    @Query("SELECT * FROM question_definitions ORDER BY sectionType, sortOrder")
    fun observeAllQuestions(): Flow<List<QuestionDefinitionEntity>>

    /**
     * Delete all question definitions.
     */
    @Query("DELETE FROM question_definitions")
    suspend fun deleteAllQuestions()
}
