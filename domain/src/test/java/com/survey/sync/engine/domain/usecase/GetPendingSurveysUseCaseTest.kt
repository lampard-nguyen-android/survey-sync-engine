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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for GetPendingSurveysUseCase.
 * Tests retrieving pending surveys from local database.
 */
class GetPendingSurveysUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var getPendingSurveysUseCase: GetPendingSurveysUseCase

    @Before
    fun setup() {
        repository = mock()
        getPendingSurveysUseCase = GetPendingSurveysUseCase(repository)
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    fun `invoke returns empty list when no pending surveys`() = runTest {
        // Given: No pending surveys
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(emptyList()))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: Empty list returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertTrue(surveys.isEmpty())
        verify(repository).getPendingSurveys()
    }

    @Test
    fun `invoke returns single pending survey`() = runTest {
        // Given: One pending survey
        val pendingSurvey = createSurvey(surveyId = "survey-1", syncStatus = SyncStatus.PENDING)

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(pendingSurvey)))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: Single survey returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(1, surveys.size)
        assertEquals(pendingSurvey, surveys[0])
        assertEquals(SyncStatus.PENDING, surveys[0].syncStatus)
    }

    @Test
    fun `invoke returns multiple pending surveys`() = runTest {
        // Given: Multiple pending surveys
        val pendingSurveys = listOf(
            createSurvey(surveyId = "survey-1", syncStatus = SyncStatus.PENDING),
            createSurvey(surveyId = "survey-2", syncStatus = SyncStatus.PENDING),
            createSurvey(surveyId = "survey-3", syncStatus = SyncStatus.PENDING)
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(pendingSurveys))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: All pending surveys returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(3, surveys.size)
        assertEquals(pendingSurveys, surveys)
        assertTrue(surveys.all { it.syncStatus == SyncStatus.PENDING })
    }

    @Test
    fun `invoke returns only PENDING surveys not other statuses`() = runTest {
        // Given: Only PENDING surveys (repository filters out other statuses)
        val pendingSurveys = listOf(
            createSurvey(surveyId = "survey-1", syncStatus = SyncStatus.PENDING),
            createSurvey(surveyId = "survey-2", syncStatus = SyncStatus.PENDING)
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(pendingSurveys))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: Only PENDING surveys returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(2, surveys.size)
        assertTrue(surveys.all { it.syncStatus == SyncStatus.PENDING })
        // Verify no SYNCED, SYNCING, or FAILED surveys
        assertTrue(surveys.none { it.syncStatus == SyncStatus.SYNCED })
        assertTrue(surveys.none { it.syncStatus == SyncStatus.SYNCING })
        assertTrue(surveys.none { it.syncStatus == SyncStatus.FAILED })
    }

    @Test
    fun `invoke returns pending surveys with answers`() = runTest {
        // Given: Pending surveys with answers
        val survey1 = createSurvey(
            surveyId = "survey-1",
            syncStatus = SyncStatus.PENDING,
            answers = listOf(
                com.survey.sync.engine.domain.createAnswer(answerUuid = "answer-1"),
                com.survey.sync.engine.domain.createAnswer(answerUuid = "answer-2")
            )
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey1)))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: Survey with answers returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(1, surveys.size)
        assertEquals(2, surveys[0].answers.size)
    }

    @Test
    fun `invoke can be called multiple times`() = runTest {
        // Given: Pending surveys
        val pendingSurveys = listOf(
            createSurvey(surveyId = "survey-1", syncStatus = SyncStatus.PENDING)
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(pendingSurveys))

        // When: Call multiple times
        val result1 = getPendingSurveysUseCase.invoke()
        val result2 = getPendingSurveysUseCase.invoke()

        // Then: Both calls succeed
        assertTrue(result1 is DomainResult.Success)
        assertTrue(result2 is DomainResult.Success)
        verify(repository, times(2)).getPendingSurveys()
    }

    @Test
    fun `invoke returns surveys ordered by creation time`() = runTest {
        // Given: Pending surveys with different creation times
        val oldestSurvey = createSurvey(
            surveyId = "survey-1",
            syncStatus = SyncStatus.PENDING,
            createdAt = 1000L
        )
        val newestSurvey = createSurvey(
            surveyId = "survey-2",
            syncStatus = SyncStatus.PENDING,
            createdAt = 3000L
        )
        val middleSurvey = createSurvey(
            surveyId = "survey-3",
            syncStatus = SyncStatus.PENDING,
            createdAt = 2000L
        )

        // Repository returns surveys in creation order (oldest first)
        whenever(repository.getPendingSurveys())
            .thenReturn(domainSuccess(listOf(oldestSurvey, middleSurvey, newestSurvey)))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: Surveys returned in order
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(3, surveys.size)
        assertEquals("survey-1", surveys[0].surveyId)
        assertEquals("survey-3", surveys[1].surveyId)
        assertEquals("survey-2", surveys[2].surveyId)
    }

    // ========== ERROR SCENARIOS ==========

    @Test
    fun `invoke returns DaoError when database query fails`() = runTest {
        // Given: Database error
        val daoError = createDaoError(operation = "query", message = "Database locked")

        whenever(repository.getPendingSurveys()).thenReturn(domainError(daoError))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertEquals("query", (error as DomainError.DaoError).operation)
        assertEquals("Database error during query: Database locked", error.errorMessage)
    }

    @Test
    fun `invoke returns DaoError on database corruption`() = runTest {
        // Given: Database corruption error
        val daoError = createDaoError(
            operation = "query",
            message = "Database disk image is malformed"
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainError(daoError))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertTrue(error.errorMessage.contains("malformed"))
    }

    @Test
    fun `invoke returns DaoError when database does not exist`() = runTest {
        // Given: Database not found error
        val daoError = createDaoError(
            operation = "query",
            message = "Database file does not exist"
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainError(daoError))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `invoke handles large number of pending surveys`() = runTest {
        // Given: Many pending surveys
        val largeSurveyList = (1..100).map {
            createSurvey(surveyId = "survey-$it", syncStatus = SyncStatus.PENDING)
        }

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(largeSurveyList))

        // When: Get pending surveys
        val result = getPendingSurveysUseCase.invoke()

        // Then: All surveys returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(100, surveys.size)
    }

    @Test
    fun `invoke returns fresh data on each call`() = runTest {
        // Given: Repository returns different data on each call
        val firstCall = listOf(createSurvey(surveyId = "survey-1"))
        val secondCall = listOf(
            createSurvey(surveyId = "survey-1"),
            createSurvey(surveyId = "survey-2")
        )

        whenever(repository.getPendingSurveys())
            .thenReturn(domainSuccess(firstCall))
            .thenReturn(domainSuccess(secondCall))

        // When: Call twice
        val result1 = getPendingSurveysUseCase.invoke()
        val result2 = getPendingSurveysUseCase.invoke()

        // Then: Different data returned
        assertTrue(result1 is DomainResult.Success)
        assertTrue(result2 is DomainResult.Success)
        assertEquals(1, (result1 as DomainResult.Success).value.size)
        assertEquals(2, (result2 as DomainResult.Success).value.size)
    }
}
