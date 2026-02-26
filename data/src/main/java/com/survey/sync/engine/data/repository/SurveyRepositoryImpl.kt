package com.survey.sync.engine.data.repository

import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.mapper.toDomain
import com.survey.sync.engine.data.mapper.toEntity
import com.survey.sync.engine.data.mapper.toUploadDto
import com.survey.sync.engine.data.remote.api.SurveyApiService
import com.survey.sync.engine.domain.model.Survey
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.model.UploadResult
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SurveyRepository.
 * Coordinates between local database (Room) and remote API (Retrofit).
 */
@Singleton
class SurveyRepositoryImpl @Inject constructor(
    private val surveyDao: SurveyDao,
    private val answerDao: AnswerDao,
    private val apiService: SurveyApiService
) : SurveyRepository {

    /**
     * Upload a survey to the server via API.
     */
    override suspend fun uploadSurvey(survey: Survey): Result<UploadResult> {
        return try {
            val uploadDto = survey.toUploadDto()
            val response = apiService.uploadSurvey(uploadDto)

            if (response.isSuccessful && response.body() != null) {
                val uploadResult = response.body()!!.toDomain()
                Result.success(uploadResult)
            } else {
                Result.failure(
                    Exception("Upload failed: HTTP ${response.code()} - ${response.message()}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get surveys by sync status from local database.
     */
    override suspend fun getSurveysByStatus(status: SyncStatus): Result<List<Survey>> {
        return try {
            val surveys = surveyDao.getSurveysByStatus(status.name)
                .map { it.toDomain() }
            Result.success(surveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all pending surveys with full details (including answers).
     */
    override suspend fun getPendingSurveys(): Result<List<Survey>> {
        return try {
            val pendingSurveys = surveyDao.getPendingSurveys()
                .map { it.toDomain() }
            Result.success(pendingSurveys)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save a survey locally (offline storage).
     */
    override suspend fun saveSurvey(survey: Survey): Result<Unit> {
        return try {
            // Save survey entity
            surveyDao.insertSurvey(survey.toEntity())

            // Save all answers
            val answerEntities = survey.answers.map { answer ->
                answer.toEntity(parentSurveyId = survey.surveyId)
            }
            answerDao.insertAllAnswers(answerEntities)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update sync status of a survey.
     */
    override suspend fun updateSyncStatus(surveyId: String, status: SyncStatus): Result<Unit> {
        return try {
            surveyDao.updateSyncStatus(surveyId, status.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Observe surveys by status as a reactive Flow.
     */
    override fun observeSurveysByStatus(status: SyncStatus): Flow<List<Survey>> {
        return surveyDao.observeSurveysByStatus(status.name)
            .map { surveys ->
                surveys.map { it.toDomain() }
            }
    }

    /**
     * Get a specific survey by ID with its answers.
     */
    override suspend fun getSurveyById(surveyId: String): Result<Survey?> {
        return try {
            val fullSurveyDetail = surveyDao.getFullSurveyDetail(surveyId)
            val survey = fullSurveyDetail?.toDomain()
            Result.success(survey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a survey (will cascade delete answers).
     */
    override suspend fun deleteSurvey(surveyId: String): Result<Unit> {
        return try {
            surveyDao.deleteSurveyById(surveyId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
