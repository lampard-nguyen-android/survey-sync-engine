package com.survey.sync.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.survey.sync.engine.data.entity.SurveyEntity
import com.survey.sync.engine.data.pojo.FullSurveyDetail
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Survey operations.
 * Handles CRUD operations and relationship queries for surveys.
 */
@Dao
interface SurveyDao {

    /**
     * Insert a new survey or replace if it already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: SurveyEntity): Long

    /**
     * Insert multiple surveys.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSurveys(surveys: List<SurveyEntity>)

    /**
     * Get surveys by sync status (e.g., PENDING, SYNCED, FAILED).
     */
    @Query("SELECT * FROM surveys WHERE syncStatus = :status ORDER BY createdAt DESC")
    suspend fun getSurveysByStatus(status: String): List<SurveyEntity>

    /**
     * Get surveys by sync status as Flow for reactive updates.
     */
    @Query("SELECT * FROM surveys WHERE syncStatus = :status ORDER BY createdAt DESC")
    fun observeSurveysByStatus(status: String): Flow<List<SurveyEntity>>

    /**
     * Get a single survey by ID.
     */
    @Query("SELECT * FROM surveys WHERE surveyId = :surveyId")
    suspend fun getSurveyById(surveyId: String): SurveyEntity?

    /**
     * Get all pending surveys with full details (including answers and definitions).
     * Used by Sync Engine to build upload payloads.
     */
    @Transaction
    @Query("SELECT * FROM surveys WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSurveys(): List<FullSurveyDetail>

    /**
     * Get full survey detail for a specific survey ID.
     */
    @Transaction
    @Query("SELECT * FROM surveys WHERE surveyId = :surveyId")
    suspend fun getFullSurveyDetail(surveyId: String): FullSurveyDetail?

    /**
     * Update sync status of a survey.
     */
    @Query("UPDATE surveys SET syncStatus = :status WHERE surveyId = :surveyId")
    suspend fun updateSyncStatus(surveyId: String, status: String)

    /**
     * Get count of surveys by status.
     */
    @Query("SELECT COUNT(*) FROM surveys WHERE syncStatus = :status")
    suspend fun getCountByStatus(status: String): Int

    /**
     * Get count of surveys by status as Flow.
     */
    @Query("SELECT COUNT(*) FROM surveys WHERE syncStatus = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    /**
     * Delete a survey by ID (will cascade delete answers due to FK constraint).
     */
    @Query("DELETE FROM surveys WHERE surveyId = :surveyId")
    suspend fun deleteSurveyById(surveyId: String)

    /**
     * Delete all surveys.
     */
    @Query("DELETE FROM surveys")
    suspend fun deleteAllSurveys()
}
