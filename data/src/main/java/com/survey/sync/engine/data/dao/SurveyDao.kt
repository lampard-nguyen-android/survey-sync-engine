package com.survey.sync.engine.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.survey.sync.engine.data.entity.SurveyEntity
import com.survey.sync.engine.data.entity.SyncStatusEntity
import com.survey.sync.engine.data.pojo.FullSurveyDetail
import kotlinx.coroutines.flow.Flow
import java.util.Date

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
    suspend fun getSurveysByStatus(status: SyncStatusEntity): List<SurveyEntity>

    /**
     * Get surveys by sync status as Flow for reactive updates.
     */
    @Query("SELECT * FROM surveys WHERE syncStatus = :status ORDER BY createdAt DESC")
    fun observeSurveysByStatus(status: SyncStatusEntity): Flow<List<SurveyEntity>>

    /**
     * Get a single survey by ID.
     */
    @Query("SELECT * FROM surveys WHERE surveyId = :surveyId")
    suspend fun getSurveyById(surveyId: String): SurveyEntity?

    /**
     * Get all pending surveys with full details (including answers and definitions).
     * Used by Sync Engine to build upload payloads.
     * Includes PENDING surveys, PENDING_MEDIA surveys (retry media upload with retry limit),
     * and FAILED surveys that haven't exceeded max retry count.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM surveys
        WHERE (syncStatus = 'PENDING'
            OR (syncStatus = 'PENDING_MEDIA' AND retryCount < :maxRetries)
            OR (syncStatus = 'FAILED' AND retryCount < :maxRetries))
        ORDER BY createdAt ASC
    """
    )
    suspend fun getPendingSurveys(maxRetries: Int = 3): List<FullSurveyDetail>

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
    suspend fun updateSyncStatus(surveyId: String, status: SyncStatusEntity)

    /**
     * Get count of surveys by status.
     */
    @Query("SELECT COUNT(*) FROM surveys WHERE syncStatus = :status")
    suspend fun getCountByStatus(status: SyncStatusEntity): Int

    /**
     * Get count of surveys by status as Flow.
     */
    @Query("SELECT COUNT(*) FROM surveys WHERE syncStatus = :status")
    fun observeCountByStatus(status: SyncStatusEntity): Flow<Int>

    /**
     * Increment retry count and update last attempt timestamp for a survey.
     * Used when a sync attempt fails to track retry attempts.
     */
    @Query(
        """
        UPDATE surveys
        SET retryCount = retryCount + 1,
            lastAttemptAt = :attemptTime
        WHERE surveyId = :surveyId
    """
    )
    suspend fun incrementRetryCount(surveyId: String, attemptTime: Date)

    /**
     * Update retry-related fields for a survey.
     * Used to manually set retry count and last attempt time.
     */
    @Query(
        """
        UPDATE surveys
        SET retryCount = :retryCount,
            lastAttemptAt = :lastAttemptAt,
            syncStatus = :status
        WHERE surveyId = :surveyId
    """
    )
    suspend fun updateRetryInfo(
        surveyId: String,
        retryCount: Int,
        lastAttemptAt: Date?,
        status: SyncStatusEntity
    )

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
