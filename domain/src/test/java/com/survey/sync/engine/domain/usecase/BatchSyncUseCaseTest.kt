package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.createApiError
import com.survey.sync.engine.domain.createDaoError
import com.survey.sync.engine.domain.createDeviceResources
import com.survey.sync.engine.domain.createInternalError
import com.survey.sync.engine.domain.createMediaAttachment
import com.survey.sync.engine.domain.createNetworkFailure
import com.survey.sync.engine.domain.createSurvey
import com.survey.sync.engine.domain.createUploadResult
import com.survey.sync.engine.domain.domainError
import com.survey.sync.engine.domain.domainSuccess
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.NetworkType
import com.survey.sync.engine.domain.network.HealthStatus
import com.survey.sync.engine.domain.network.NetworkHealthTracker
import com.survey.sync.engine.domain.network.NetworkStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for BatchSyncUseCase.
 * Tests all scenarios: empty queue, all succeed, partial failure, network issues, device constraints.
 */
class BatchSyncUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var uploadSurveyUseCase: UploadSurveyUseCase
    private lateinit var getMediaAttachmentsUseCase: GetMediaAttachmentsUseCase
    private lateinit var batchSyncUseCase: BatchSyncUseCase

    @Before
    fun setup() {
        repository = mock()
        uploadSurveyUseCase = mock()
        getMediaAttachmentsUseCase = mock()
        batchSyncUseCase = BatchSyncUseCase(
            repository = repository,
            uploadSurveyUseCase = uploadSurveyUseCase,
            getMediaAttachmentsUseCase = getMediaAttachmentsUseCase
        )
    }

    // ========== EMPTY QUEUE SCENARIO ==========

    @Test
    fun `invoke with empty pending surveys returns success with zero counts`() = runTest {
        // Given: No pending surveys
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(emptyList()))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: Success with zero counts
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(0, batchResult.totalSurveys)
        assertEquals(0, batchResult.successCount)
        assertEquals(0, batchResult.failureCount)
        assertEquals(0, batchResult.skippedCount)
        assertTrue(batchResult.surveyResults.isEmpty())
        assertTrue(batchResult.succeededSurveyIds.isEmpty())
        assertTrue(batchResult.failedSurveyIds.isEmpty())
        assertTrue(batchResult.skippedSurveyIds.isEmpty())
        assertEquals(BatchSyncUseCase.StopReason.COMPLETED, batchResult.stopReason)
        assertEquals(HealthStatus.HEALTHY, batchResult.networkHealthStatus)

        // Verify: getPendingSurveys called once, no uploads attempted
        verify(repository, times(1)).getPendingSurveys()
        verify(uploadSurveyUseCase, never()).invoke(any(), any(), any(), any(), any())
    }

    // ========== ALL SUCCEED SCENARIO ==========

    @Test
    fun `invoke with multiple pending surveys all succeed`() = runTest {
        // Given: 3 pending surveys
        val survey1 = createSurvey(surveyId = "survey-1")
        val survey2 = createSurvey(surveyId = "survey-2")
        val survey3 = createSurvey(surveyId = "survey-3")
        val pendingSurveys = listOf(survey1, survey2, survey3)

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(pendingSurveys))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        // All uploads succeed
        val uploadResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(uploadResult))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: All surveys succeed
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(3, batchResult.totalSurveys)
        assertEquals(3, batchResult.successCount)
        assertEquals(0, batchResult.failureCount)
        assertEquals(0, batchResult.skippedCount)
        assertEquals(3, batchResult.succeededSurveyIds.size)
        assertTrue(
            batchResult.succeededSurveyIds.containsAll(
                listOf(
                    "survey-1",
                    "survey-2",
                    "survey-3"
                )
            )
        )
        assertTrue(batchResult.failedSurveyIds.isEmpty())
        assertTrue(batchResult.skippedSurveyIds.isEmpty())
        assertEquals(BatchSyncUseCase.StopReason.COMPLETED, batchResult.stopReason)
        assertEquals(HealthStatus.HEALTHY, batchResult.networkHealthStatus)

        // Verify: Upload called for each survey
        verify(uploadSurveyUseCase, times(3)).invoke(any(), any(), any(), any(), any())
    }

    // ========== PARTIAL FAILURE SCENARIO ==========

    @Test
    fun `invoke with partial failure - some succeed some fail`() = runTest {
        // Given: 4 pending surveys
        val survey1 = createSurvey(surveyId = "survey-1")
        val survey2 = createSurvey(surveyId = "survey-2")
        val survey3 = createSurvey(surveyId = "survey-3")
        val survey4 = createSurvey(surveyId = "survey-4")
        val pendingSurveys = listOf(survey1, survey2, survey3, survey4)

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(pendingSurveys))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        // Survey 1 & 3 succeed, Survey 2 & 4 fail
        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        val failureError = createApiError(httpCode = 500)

        whenever(uploadSurveyUseCase.invoke(eq(survey1), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))
        whenever(uploadSurveyUseCase.invoke(eq(survey2), any(), any(), any(), any()))
            .thenReturn(domainError(failureError))
        whenever(uploadSurveyUseCase.invoke(eq(survey3), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))
        whenever(uploadSurveyUseCase.invoke(eq(survey4), any(), any(), any(), any()))
            .thenReturn(domainError(failureError))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: Partial success
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(4, batchResult.totalSurveys)
        assertEquals(2, batchResult.successCount)
        assertEquals(2, batchResult.failureCount)
        assertEquals(0, batchResult.skippedCount)
        assertEquals(2, batchResult.succeededSurveyIds.size)
        assertEquals(2, batchResult.failedSurveyIds.size)
        assertTrue(batchResult.succeededSurveyIds.containsAll(listOf("survey-1", "survey-3")))
        assertTrue(batchResult.failedSurveyIds.containsAll(listOf("survey-2", "survey-4")))
        assertEquals(BatchSyncUseCase.StopReason.COMPLETED, batchResult.stopReason)

        // Verify: All surveys attempted
        verify(uploadSurveyUseCase, times(4)).invoke(any(), any(), any(), any(), any())
    }

    // ========== NETWORK CIRCUIT BREAKER SCENARIO ==========

    @Test
    fun `invoke stops early when circuit breaker opens after 3 consecutive network failures`() =
        runTest {
            // Given: 5 pending surveys
            val surveys = (1..5).map { createSurvey(surveyId = "survey-$it") }
            whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(surveys))
            whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

            // All uploads fail with network error
            val networkError = createNetworkFailure(message = "Network unavailable")
            whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
                .thenReturn(domainError(networkError))

            // When: Batch sync is invoked with NetworkHealthTracker
            val networkHealthTracker = NetworkHealthTracker(consecutiveFailureThreshold = 3)
            val result = batchSyncUseCase.invoke(networkHealthTracker = networkHealthTracker)

            // Then: Sync stopped after 3 failures, remaining surveys skipped
            assertTrue(result is DomainResult.Success)
            val batchResult = (result as DomainResult.Success).value
            assertEquals(5, batchResult.totalSurveys)
            assertEquals(0, batchResult.successCount)
            assertEquals(3, batchResult.failureCount) // First 3 failed
            assertEquals(2, batchResult.skippedCount) // Last 2 skipped due to circuit breaker
            assertEquals(BatchSyncUseCase.StopReason.NETWORK_DOWN, batchResult.stopReason)
            assertEquals(HealthStatus.CIRCUIT_OPEN, batchResult.networkHealthStatus)

            // Verify: Only 3 uploads attempted (circuit breaker stops after 3 failures)
            verify(uploadSurveyUseCase, times(3)).invoke(any(), any(), any(), any(), any())
        }

    @Test
    fun `invoke with network failures followed by success resets circuit breaker`() = runTest {
        // Given: 5 pending surveys
        val surveys = (1..5).map { createSurvey(surveyId = "survey-$it") }
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(surveys))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        // First 2 fail, third succeeds, then rest succeed
        val networkError = createNetworkFailure()
        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )

        whenever(uploadSurveyUseCase.invoke(eq(surveys[0]), any(), any(), any(), any()))
            .thenReturn(domainError(networkError))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[1]), any(), any(), any(), any()))
            .thenReturn(domainError(networkError))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[2]), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[3]), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[4]), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync is invoked
        val networkHealthTracker = NetworkHealthTracker(consecutiveFailureThreshold = 3)
        val result = batchSyncUseCase.invoke(networkHealthTracker = networkHealthTracker)

        // Then: All surveys attempted (circuit breaker reset by success)
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(5, batchResult.totalSurveys)
        assertEquals(3, batchResult.successCount)
        assertEquals(2, batchResult.failureCount)
        assertEquals(0, batchResult.skippedCount)
        assertEquals(BatchSyncUseCase.StopReason.COMPLETED, batchResult.stopReason)

        // Verify: All uploads attempted (circuit didn't open because of success in between)
        verify(uploadSurveyUseCase, times(5)).invoke(any(), any(), any(), any(), any())
    }

    // ========== DEVICE CONSTRAINTS SCENARIOS ==========

    @Test
    fun `invoke stops when storage is critical`() = runTest {
        // Given: 3 pending surveys, storage is critical (< 200 MB)
        val surveys = (1..3).map { createSurvey(surveyId = "survey-$it") }
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(surveys))

        val deviceResources = createDeviceResources(
            availableBytes = 100_000_000L // 100 MB - critical
        )

        // When: Batch sync with critical storage
        val result = batchSyncUseCase.invoke(deviceResources = deviceResources)

        // Then: All surveys skipped due to critical storage
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(3, batchResult.totalSurveys)
        assertEquals(0, batchResult.successCount)
        assertEquals(0, batchResult.failureCount)
        assertEquals(3, batchResult.skippedCount)
        assertEquals(BatchSyncUseCase.StopReason.STORAGE_CRITICAL, batchResult.stopReason)

        // Verify: No uploads attempted
        verify(uploadSurveyUseCase, never()).invoke(any(), any(), any(), any(), any())
    }

    @Test
    fun `invoke skips media when battery is low and not charging`() = runTest {
        // Given: 1 pending survey, battery low (< 20%) and not charging
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(attachments))

        val deviceResources = createDeviceResources(
            batteryLevel = 15, // Low battery
            isCharging = false
        )

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync with low battery
        val result = batchSyncUseCase.invoke(deviceResources = deviceResources)

        // Then: Upload called with skipMedia = true
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = eq(attachments),
            cleanupAttachments = eq(true),
            skipMedia = eq(true), // Media skipped due to low battery
            maxRetries = eq(3)
        )
    }

    @Test
    fun `invoke skips media on weak network`() = runTest {
        // Given: 1 pending survey, weak network
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(attachments))

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync with weak network
        val result = batchSyncUseCase.invoke(networkStatus = NetworkStatus.Weak)

        // Then: Upload called with skipMedia = true
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = eq(attachments),
            cleanupAttachments = eq(true),
            skipMedia = eq(true), // Media skipped due to weak network
            maxRetries = eq(3)
        )
    }

    @Test
    fun `invoke skips media on cellular network`() = runTest {
        // Given: 1 pending survey, cellular network
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(attachments))

        val deviceResources = createDeviceResources(
            networkType = NetworkType.CELLULAR
        )

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync with cellular network
        val result = batchSyncUseCase.invoke(deviceResources = deviceResources)

        // Then: Upload called with skipMedia = true
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = eq(attachments),
            cleanupAttachments = eq(true),
            skipMedia = eq(true), // Media skipped due to cellular network
            maxRetries = eq(3)
        )
    }

    @Test
    fun `invoke uploads media on WiFi with good battery`() = runTest {
        // Given: 1 pending survey, WiFi, good battery
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(attachments))

        val deviceResources = createDeviceResources(
            batteryLevel = 80,
            isCharging = false,
            networkType = NetworkType.WIFI
        )

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 1,
            mediaUploadFailureCount = 0,
            totalMediaCount = 1
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync with good conditions
        val result = batchSyncUseCase.invoke(deviceResources = deviceResources)

        // Then: Upload called with skipMedia = false
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = eq(attachments),
            cleanupAttachments = eq(true),
            skipMedia = eq(false), // Media uploaded with good conditions
            maxRetries = eq(3)
        )
    }

    // ========== ERROR HANDLING SCENARIOS ==========

    @Test
    fun `invoke handles repository error when getting pending surveys`() = runTest {
        // Given: Repository error when getting pending surveys
        val daoError = createDaoError(operation = "getPendingSurveys")
        whenever(repository.getPendingSurveys()).thenReturn(domainError(daoError))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: Error returned
        assertTrue(result is DomainResult.Error)
        val errorResult = (result as DomainResult.Error).error
        assertEquals(daoError, errorResult)

        // Verify: No uploads attempted
        verify(uploadSurveyUseCase, never()).invoke(any(), any(), any(), any(), any())
    }

    @Test
    fun `invoke handles different error types correctly`() = runTest {
        // Given: 4 pending surveys with different error types
        val surveys = (1..4).map { createSurvey(surveyId = "survey-$it") }
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(surveys))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        // Different error types
        val networkError = createNetworkFailure()
        val apiError = createApiError(httpCode = 400) // Client error
        val daoError = createDaoError()
        val internalError = createInternalError()

        whenever(uploadSurveyUseCase.invoke(eq(surveys[0]), any(), any(), any(), any()))
            .thenReturn(domainError(networkError))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[1]), any(), any(), any(), any()))
            .thenReturn(domainError(apiError))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[2]), any(), any(), any(), any()))
            .thenReturn(domainError(daoError))
        whenever(uploadSurveyUseCase.invoke(eq(surveys[3]), any(), any(), any(), any()))
            .thenReturn(domainError(internalError))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: All surveys failed with appropriate errors
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        assertEquals(4, batchResult.totalSurveys)
        assertEquals(0, batchResult.successCount)
        assertEquals(4, batchResult.failureCount)
        assertEquals(4, batchResult.failedSurveyIds.size)

        // Verify error messages contain appropriate error information
        val survey1Result = batchResult.surveyResults["survey-1"]!!
        assertFalse(survey1Result.isSuccess)
        assertTrue(survey1Result.errorMessage!!.contains("Network"))

        val survey2Result = batchResult.surveyResults["survey-2"]!!
        assertFalse(survey2Result.isSuccess)

        // Verify: All uploads attempted
        verify(uploadSurveyUseCase, times(4)).invoke(any(), any(), any(), any(), any())
    }

    @Test
    fun `invoke wraps unexpected exceptions in DomainError`() = runTest {
        // Given: Repository throws unexpected exception
        whenever(repository.getPendingSurveys()).thenThrow(RuntimeException("Unexpected error"))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: Exception wrapped in DomainError
        assertTrue(result is DomainResult.Error)
        val errorResult = (result as DomainResult.Error).error
        assertTrue(errorResult is DomainError.UnexpectedError)
        assertEquals("Unexpected error", errorResult.errorMessage)
    }

    // ========== RETRY LOGIC SCENARIOS ==========

    @Test
    fun `invoke passes maxRetries parameter to uploadSurveyUseCase`() = runTest {
        // Given: 1 pending survey
        val survey = createSurvey(surveyId = "survey-1")
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync with custom maxRetries
        val customMaxRetries = 5
        val result = batchSyncUseCase.invoke(maxRetries = customMaxRetries)

        // Then: maxRetries passed to upload use case
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = any(),
            cleanupAttachments = any(),
            skipMedia = any(),
            maxRetries = eq(customMaxRetries)
        )
    }

    @Test
    fun `invoke uses default maxRetries of 3 when not specified`() = runTest {
        // Given: 1 pending survey
        val survey = createSurvey(surveyId = "survey-1")
        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(emptyList()))

        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 0,
            mediaUploadFailureCount = 0,
            totalMediaCount = 0
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync without specifying maxRetries
        val result = batchSyncUseCase.invoke()

        // Then: Default maxRetries of 3 passed to upload use case
        assertTrue(result is DomainResult.Success)
        verify(uploadSurveyUseCase).invoke(
            survey = eq(survey),
            mediaAttachments = any(),
            cleanupAttachments = any(),
            skipMedia = any(),
            maxRetries = eq(3) // Default value
        )
    }

    // ========== MEDIA UPLOAD SCENARIOS ==========

    @Test
    fun `invoke tracks media upload results correctly`() = runTest {
        // Given: 1 pending survey with media
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2")
        )

        whenever(repository.getPendingSurveys()).thenReturn(domainSuccess(listOf(survey)))
        whenever(getMediaAttachmentsUseCase.invoke(any())).thenReturn(domainSuccess(attachments))

        // Media upload: 1 success, 1 failure
        val successResult = UploadSurveyUseCase.UploadSurveyResult(
            surveyUploadResult = createUploadResult(surveyId = "survey-1"),
            mediaUploadSuccessCount = 1,
            mediaUploadFailureCount = 1,
            totalMediaCount = 2
        )
        whenever(uploadSurveyUseCase.invoke(any(), any(), any(), any(), any()))
            .thenReturn(domainSuccess(successResult))

        // When: Batch sync is invoked
        val result = batchSyncUseCase.invoke()

        // Then: Media counts tracked correctly
        assertTrue(result is DomainResult.Success)
        val batchResult = (result as DomainResult.Success).value
        val surveyResult = batchResult.surveyResults["survey-1"]!!
        assertTrue(surveyResult.isSuccess)
        assertEquals(1, surveyResult.mediaUploadSuccessCount)
        assertEquals(1, surveyResult.mediaUploadFailureCount)
        assertEquals(2, surveyResult.totalMediaCount)
    }
}
