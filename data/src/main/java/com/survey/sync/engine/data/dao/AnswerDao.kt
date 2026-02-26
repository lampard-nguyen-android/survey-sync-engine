package com.survey.sync.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.survey.sync.engine.data.entity.AnswerEntity
import com.survey.sync.engine.data.pojo.AnswerWithDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Answer operations.
 * Handles answer storage with idempotency and sync tracking.
 */
@Dao
interface AnswerDao {

    /**
     * Insert an answer. Uses IGNORE strategy for idempotency via answerUuid.
     * If the same UUID is inserted again (e.g., after network timeout), it will be ignored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnswer(answer: AnswerEntity): Long

    /**
     * Insert multiple answers with idempotency.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllAnswers(answers: List<AnswerEntity>)

    /**
     * Get all answers for a specific survey.
     */
    @Query("SELECT * FROM answers WHERE parentSurveyId = :surveyId ORDER BY instanceIndex, questionKey")
    suspend fun getAnswersBySurvey(surveyId: String): List<AnswerEntity>

    /**
     * Get all answers for a specific survey as Flow.
     */
    @Query("SELECT * FROM answers WHERE parentSurveyId = :surveyId ORDER BY instanceIndex, questionKey")
    fun observeAnswersBySurvey(surveyId: String): Flow<List<AnswerEntity>>

    /**
     * Get answers with their question definitions for a specific survey.
     */
    @Transaction
    @Query("SELECT * FROM answers WHERE parentSurveyId = :surveyId ORDER BY instanceIndex, questionKey")
    suspend fun getAnswersWithDefinitions(surveyId: String): List<AnswerWithDefinition>

    /**
     * Get answers with definitions as Flow.
     */
    @Transaction
    @Query("SELECT * FROM answers WHERE parentSurveyId = :surveyId ORDER BY instanceIndex, questionKey")
    fun observeAnswersWithDefinitions(surveyId: String): Flow<List<AnswerWithDefinition>>

    /**
     * Get answers for a specific instance of a repeating section.
     * For example, get all answers for "Farm 2" (instanceIndex = 2).
     */
    @Query("SELECT * FROM answers WHERE parentSurveyId = :surveyId AND instanceIndex = :instanceIndex")
    suspend fun getAnswersByInstance(surveyId: String, instanceIndex: Int): List<AnswerEntity>

    /**
     * Update sync status of an answer after successful upload.
     */
    @Query("UPDATE answers SET syncStatus = :status, uploadedAt = :uploadedAt WHERE answerUuid = :answerUuid")
    suspend fun updateAnswerSyncStatus(answerUuid: String, status: String, uploadedAt: Long?)

    /**
     * Update sync status for all answers of a survey.
     */
    @Query("UPDATE answers SET syncStatus = :status, uploadedAt = :uploadedAt WHERE parentSurveyId = :surveyId")
    suspend fun updateAllAnswersSyncStatus(surveyId: String, status: String, uploadedAt: Long?)

    /**
     * Get count of pending answers for a survey.
     */
    @Query("SELECT COUNT(*) FROM answers WHERE parentSurveyId = :surveyId AND syncStatus = 'PENDING'")
    suspend fun getPendingAnswerCount(surveyId: String): Int

    /**
     * Get a specific answer by UUID (for idempotency checking).
     */
    @Query("SELECT * FROM answers WHERE answerUuid = :answerUuid")
    suspend fun getAnswerByUuid(answerUuid: String): AnswerEntity?

    /**
     * Delete all answers for a survey (usually not needed due to CASCADE delete).
     */
    @Query("DELETE FROM answers WHERE parentSurveyId = :surveyId")
    suspend fun deleteAnswersBySurvey(surveyId: String)

    /**
     * Delete all answers.
     */
    @Query("DELETE FROM answers")
    suspend fun deleteAllAnswers()
}
