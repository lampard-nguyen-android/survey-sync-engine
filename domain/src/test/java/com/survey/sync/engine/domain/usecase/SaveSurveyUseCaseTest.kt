package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.createDaoError
import com.survey.sync.engine.domain.createSurvey
import com.survey.sync.engine.domain.domainError
import com.survey.sync.engine.domain.domainSuccess
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for SaveSurveyUseCase.
 * Tests saving surveys to local database with success and error scenarios.
 */
class SaveSurveyUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var saveSurveyUseCase: SaveSurveyUseCase

    @Before
    fun setup() {
        repository = mock()
        saveSurveyUseCase = SaveSurveyUseCase(repository)
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    fun `invoke successfully saves survey`() = runTest {
        // Given: A survey to save
        val survey = createSurvey(surveyId = "survey-1")

        whenever(repository.saveSurvey(survey)).thenReturn(domainSuccess(Unit))

        // When: Save survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: Success returned
        assertTrue(result is DomainResult.Success)
        verify(repository).saveSurvey(survey)
    }

    @Test
    fun `invoke saves survey with PENDING status`() = runTest {
        // Given: Survey with PENDING status
        val survey = createSurvey(surveyId = "survey-1", syncStatus = SyncStatus.PENDING)

        whenever(repository.saveSurvey(survey)).thenReturn(domainSuccess(Unit))

        // When: Save survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(repository).saveSurvey(survey)
    }

    @Test
    fun `invoke saves survey with answers`() = runTest {
        // Given: Survey with multiple answers
        val survey = createSurvey(
            surveyId = "survey-1",
            answers = listOf(
                com.survey.sync.engine.domain.createAnswer(answerUuid = "answer-1"),
                com.survey.sync.engine.domain.createAnswer(answerUuid = "answer-2")
            )
        )

        whenever(repository.saveSurvey(survey)).thenReturn(domainSuccess(Unit))

        // When: Save survey with answers
        val result = saveSurveyUseCase.invoke(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(repository).saveSurvey(survey)
    }

    @Test
    fun `invoke can save multiple surveys`() = runTest {
        // Given: Multiple surveys to save
        val survey1 = createSurvey(surveyId = "survey-1")
        val survey2 = createSurvey(surveyId = "survey-2")
        val survey3 = createSurvey(surveyId = "survey-3")

        whenever(repository.saveSurvey(any())).thenReturn(domainSuccess(Unit))

        // When: Save multiple surveys
        val result1 = saveSurveyUseCase.invoke(survey1)
        val result2 = saveSurveyUseCase.invoke(survey2)
        val result3 = saveSurveyUseCase.invoke(survey3)

        // Then: All succeed
        assertTrue(result1 is DomainResult.Success)
        assertTrue(result2 is DomainResult.Success)
        assertTrue(result3 is DomainResult.Success)

        verify(repository).saveSurvey(survey1)
        verify(repository).saveSurvey(survey2)
        verify(repository).saveSurvey(survey3)
    }

    // ========== ERROR SCENARIOS ==========

    @Test
    fun `invoke returns DaoError when save fails`() = runTest {
        // Given: Database error
        val survey = createSurvey(surveyId = "survey-1")
        val daoError = createDaoError(operation = "insert", message = "Database locked")

        whenever(repository.saveSurvey(survey)).thenReturn(domainError(daoError))

        // When: Save survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertEquals("insert", (error as DomainError.DaoError).operation)
        assertEquals("Database error during insert: Database locked", error.errorMessage)
    }

    @Test
    fun `invoke returns DaoError on constraint violation`() = runTest {
        // Given: Unique constraint violation (duplicate survey ID)
        val survey = createSurvey(surveyId = "survey-1")
        val daoError = createDaoError(
            operation = "insert",
            message = "UNIQUE constraint failed: surveys.surveyId"
        )

        whenever(repository.saveSurvey(survey)).thenReturn(domainError(daoError))

        // When: Save duplicate survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertTrue(error.errorMessage.contains("UNIQUE constraint"))
    }

    @Test
    fun `invoke returns DaoError when database is full`() = runTest {
        // Given: Database full error
        val survey = createSurvey(surveyId = "survey-1")
        val daoError = createDaoError(
            operation = "insert",
            message = "Database or disk is full"
        )

        whenever(repository.saveSurvey(survey)).thenReturn(domainError(daoError))

        // When: Save survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertTrue(error.errorMessage.contains("full"))
    }

    // ========== EDGE CASES ==========

    @Test
    fun `invoke saves survey with empty answers list`() = runTest {
        // Given: Survey with no answers
        val survey = createSurvey(surveyId = "survey-1", answers = emptyList())

        whenever(repository.saveSurvey(survey)).thenReturn(domainSuccess(Unit))

        // When: Save survey
        val result = saveSurveyUseCase.invoke(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(repository).saveSurvey(survey)
    }

    @Test
    fun `invoke saves survey with all sync statuses`() = runTest {
        // Test saving surveys with different sync statuses
        val statuses = listOf(
            SyncStatus.PENDING,
            SyncStatus.SYNCING,
            SyncStatus.SYNCED,
            SyncStatus.FAILED
        )

        whenever(repository.saveSurvey(any())).thenReturn(domainSuccess(Unit))

        statuses.forEach { status ->
            val survey = createSurvey(surveyId = "survey-$status", syncStatus = status)
            val result = saveSurveyUseCase.invoke(survey)
            assertTrue(result is DomainResult.Success)
        }

        verify(repository, times(4)).saveSurvey(any())
    }
}
