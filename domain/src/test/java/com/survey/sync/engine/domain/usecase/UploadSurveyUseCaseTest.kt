package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.createApiError
import com.survey.sync.engine.domain.createInternalError
import com.survey.sync.engine.domain.createMediaAttachment
import com.survey.sync.engine.domain.createMediaUploadResult
import com.survey.sync.engine.domain.createNetworkFailure
import com.survey.sync.engine.domain.createSurvey
import com.survey.sync.engine.domain.createUploadResult
import com.survey.sync.engine.domain.createValidationError
import com.survey.sync.engine.domain.domainError
import com.survey.sync.engine.domain.domainSuccess
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.MediaUploadResult
import com.survey.sync.engine.domain.model.SyncStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for UploadSurveyUseCase.
 * Tests survey upload flow, status transitions, media handling, and error handling.
 */
class UploadSurveyUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var uploadMediaAttachmentsUseCase: UploadMediaAttachmentsUseCase
    private lateinit var uploadSurveyUseCase: UploadSurveyUseCase

    @Before
    fun setup() {
        repository = mock()
        uploadMediaAttachmentsUseCase = mock()
        uploadSurveyUseCase = UploadSurveyUseCase(
            repository = repository,
            uploadMediaAttachmentsUseCase = uploadMediaAttachmentsUseCase
        )
    }

    // ========== SUCCESSFUL UPLOAD SCENARIOS ==========

    @Test
    fun `invoke successfully uploads survey without media`() = runTest {
        // Given: Survey with no media attachments
        val survey = createSurvey(surveyId = "survey-1")
        val uploadResult = createUploadResult(surveyId = "survey-1")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Success with zero media counts
        assertTrue(result is DomainResult.Success)
        val surveyResult = (result as DomainResult.Success).value
        assertEquals(uploadResult, surveyResult.surveyUploadResult)
        assertEquals(0, surveyResult.mediaUploadSuccessCount)
        assertEquals(0, surveyResult.mediaUploadFailureCount)
        assertEquals(0, surveyResult.mediaSkippedCount)
        assertEquals(0, surveyResult.totalMediaCount)

        // Verify: Status transitions PENDING → SYNCING → SYNCED
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCING)
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCED)
        verify(repository).uploadSurvey(survey)
        verify(uploadMediaAttachmentsUseCase, never()).invoke(any(), any())
        verify(repository, never()).cleanupSyncedAttachments(any())
    }

    @Test
    fun `invoke successfully uploads survey with media attachments`() = runTest {
        // Given: Survey with 3 media attachments, all upload successfully
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )
        val uploadResult = createUploadResult(surveyId = "survey-1")

        val mediaResults = mapOf(
            "att-1" to domainSuccess(createMediaUploadResult("att-1")),
            "att-2" to domainSuccess(createMediaUploadResult("att-2")),
            "att-3" to domainSuccess(createMediaUploadResult("att-3"))
        )

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))
        whenever(uploadMediaAttachmentsUseCase.invoke(any(), any())).thenReturn(mediaResults)
        whenever(repository.cleanupSyncedAttachments(any())).thenReturn(domainSuccess(3))

        // When: Upload survey with media
        val result = uploadSurveyUseCase.invoke(
            survey = survey,
            mediaAttachments = attachments,
            cleanupAttachments = true
        )

        // Then: Success with all media uploaded
        assertTrue(result is DomainResult.Success)
        val surveyResult = (result as DomainResult.Success).value
        assertEquals(3, surveyResult.mediaUploadSuccessCount)
        assertEquals(0, surveyResult.mediaUploadFailureCount)
        assertEquals(0, surveyResult.mediaSkippedCount)
        assertEquals(3, surveyResult.totalMediaCount)

        // Verify: Media uploaded and cleanup called
        verify(uploadMediaAttachmentsUseCase).invoke("survey-1", attachments)
        verify(repository).cleanupSyncedAttachments("survey-1")
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCED)
    }

    @Test
    fun `invoke uploads survey with partial media success`() = runTest {
        // Given: Survey with 3 attachments, 2 succeed, 1 fails
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )
        val uploadResult = createUploadResult(surveyId = "survey-1")

        val mediaResults = mapOf(
            "att-1" to domainSuccess(createMediaUploadResult("att-1")),
            "att-2" to domainError(createNetworkFailure()),
            "att-3" to domainSuccess(createMediaUploadResult("att-3"))
        )

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))
        whenever(uploadMediaAttachmentsUseCase.invoke(any(), any())).thenReturn(mediaResults)
        whenever(repository.cleanupSyncedAttachments(any())).thenReturn(domainSuccess(2))

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(
            survey = survey,
            mediaAttachments = attachments
        )

        // Then: Survey still marked as SYNCED, partial media success
        assertTrue(result is DomainResult.Success)
        val surveyResult = (result as DomainResult.Success).value
        assertEquals(2, surveyResult.mediaUploadSuccessCount)
        assertEquals(1, surveyResult.mediaUploadFailureCount)
        assertEquals(3, surveyResult.totalMediaCount)

        // Verify: Survey marked as SYNCED even with media failures
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCED)
        verify(repository).cleanupSyncedAttachments("survey-1")
    }

    // ========== SKIP MEDIA SCENARIOS ==========

    @Test
    fun `invoke skips media upload when skipMedia is true`() = runTest {
        // Given: Survey with media attachments, skipMedia = true
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2")
        )
        val uploadResult = createUploadResult(surveyId = "survey-1")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))

        // When: Upload survey with skipMedia = true
        val result = uploadSurveyUseCase.invoke(
            survey = survey,
            mediaAttachments = attachments,
            skipMedia = true
        )

        // Then: Media skipped, survey still synced
        assertTrue(result is DomainResult.Success)
        val surveyResult = (result as DomainResult.Success).value
        assertEquals(0, surveyResult.mediaUploadSuccessCount)
        assertEquals(0, surveyResult.mediaUploadFailureCount)
        assertEquals(2, surveyResult.mediaSkippedCount)
        assertEquals(2, surveyResult.totalMediaCount)

        // Verify: Media upload not called, no cleanup
        verify(uploadMediaAttachmentsUseCase, never()).invoke(any(), any())
        verify(repository, never()).cleanupSyncedAttachments(any())
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCED)
    }

    // ========== CLEANUP SCENARIOS ==========

    @Test
    fun `invoke does not cleanup attachments when cleanupAttachments is false`() = runTest {
        // Given: Survey with media, cleanupAttachments = false
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))
        val uploadResult = createUploadResult(surveyId = "survey-1")

        val mediaResults = mapOf(
            "att-1" to domainSuccess(createMediaUploadResult("att-1"))
        )

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))
        whenever(uploadMediaAttachmentsUseCase.invoke(any(), any())).thenReturn(mediaResults)

        // When: Upload with cleanupAttachments = false
        val result = uploadSurveyUseCase.invoke(
            survey = survey,
            mediaAttachments = attachments,
            cleanupAttachments = false
        )

        // Then: Success but no cleanup
        assertTrue(result is DomainResult.Success)

        // Verify: Cleanup not called
        verify(repository, never()).cleanupSyncedAttachments(any())
    }

    @Test
    fun `invoke does not cleanup when no media uploads succeeded`() = runTest {
        // Given: Survey with media, all media uploads fail
        val survey = createSurvey(surveyId = "survey-1")
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2")
        )
        val uploadResult = createUploadResult(surveyId = "survey-1")

        val mediaResults = mapOf(
            "att-1" to domainError<MediaUploadResult>(createNetworkFailure()),
            "att-2" to domainError<MediaUploadResult>(createNetworkFailure()),
        )

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))
        whenever(uploadMediaAttachmentsUseCase.invoke(any(), any())).thenReturn(mediaResults)

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(
            survey = survey,
            mediaAttachments = attachments,
            cleanupAttachments = true
        )

        // Then: Success but no cleanup (no successful media uploads)
        assertTrue(result is DomainResult.Success)
        val surveyResult = (result as DomainResult.Success).value
        assertEquals(0, surveyResult.mediaUploadSuccessCount)
        assertEquals(2, surveyResult.mediaUploadFailureCount)

        // Verify: Cleanup not called (no successful uploads)
        verify(repository, never()).cleanupSyncedAttachments(any())
    }

    // ========== ERROR HANDLING SCENARIOS ==========

    @Test
    fun `invoke handles retryable error by incrementing retry count`() = runTest {
        // Given: Survey upload fails with retryable error (network error)
        val survey = createSurvey(surveyId = "survey-1")
        val networkError = createNetworkFailure(message = "Network unavailable")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(networkError))
        whenever(repository.incrementSurveyRetryCount(any())).thenReturn(domainSuccess(Unit))

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)
        assertEquals(networkError, (result as DomainResult.Error).error)

        // Verify: Retry count incremented, status updated to FAILED
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCING)
        verify(repository).incrementSurveyRetryCount("survey-1")
        verify(repository).updateSyncStatus("survey-1", SyncStatus.FAILED)
        verify(repository, never()).markSurveyAsPermanentlyFailed(any(), any())
    }

    @Test
    fun `invoke handles retryable error with ApiError 500`() = runTest {
        // Given: Survey upload fails with retryable API error (server error)
        val survey = createSurvey(surveyId = "survey-1")
        val apiError = createApiError(httpCode = 500, httpMessage = "Internal Server Error")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(apiError))
        whenever(repository.incrementSurveyRetryCount(any())).thenReturn(domainSuccess(Unit))

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)

        // Verify: Treated as retryable - retry count incremented
        verify(repository).incrementSurveyRetryCount("survey-1")
        verify(repository).updateSyncStatus("survey-1", SyncStatus.FAILED)
    }

    @Test
    fun `invoke handles non-retryable error by marking as permanently failed`() = runTest {
        // Given: Survey upload fails with non-retryable error (validation error)
        val survey = createSurvey(surveyId = "survey-1")
        val validationError =
            createValidationError(errorCode = "invalid anwer", errorMessage = "Invalid answers")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(validationError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload survey with maxRetries = 3
        val result = uploadSurveyUseCase.invoke(survey = survey, maxRetries = 3)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)
        assertEquals(validationError, (result as DomainResult.Error).error)

        // Verify: Marked as permanently failed, retry count NOT incremented
        verify(repository).updateSyncStatus("survey-1", SyncStatus.SYNCING)
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 3)
        verify(repository, never()).incrementSurveyRetryCount(any())
        verify(repository, never()).updateSyncStatus("survey-1", SyncStatus.FAILED)
    }

    @Test
    fun `invoke handles non-retryable ApiError 400`() = runTest {
        // Given: Survey upload fails with client error (bad request)
        val survey = createSurvey(surveyId = "survey-1")
        val apiError = createApiError(httpCode = 400, httpMessage = "Bad Request")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(apiError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey, maxRetries = 3)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)

        // Verify: Treated as non-retryable - marked as permanently failed
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 3)
        verify(repository, never()).incrementSurveyRetryCount(any())
    }

    @Test
    fun `invoke handles non-retryable ApiError 401 Unauthorized`() = runTest {
        // Given: Survey upload fails with authentication error
        val survey = createSurvey(surveyId = "survey-1")
        val apiError = createApiError(httpCode = 401, httpMessage = "Unauthorized")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(apiError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey, maxRetries = 5)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)

        // Verify: Treated as non-retryable
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 5)
    }

    @Test
    fun `invoke handles InternalError as non-retryable`() = runTest {
        // Given: Survey upload fails with internal error
        val survey = createSurvey(surveyId = "survey-1")
        val internalError = createInternalError(message = "Internal error occurred")

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(internalError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)

        // Verify: Treated as non-retryable
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 3)
    }

    // ========== MAX RETRIES SCENARIOS ==========

    @Test
    fun `invoke passes maxRetries to markSurveyAsPermanentlyFailed`() = runTest {
        // Given: Non-retryable error with custom maxRetries
        val survey = createSurvey(surveyId = "survey-1")
        val validationError = createValidationError()

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(validationError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload with custom maxRetries = 5
        val result = uploadSurveyUseCase.invoke(survey = survey, maxRetries = 5)

        // Then: maxRetries passed correctly
        assertTrue(result is DomainResult.Error)
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 5)
    }

    @Test
    fun `invoke uses default maxRetries of 3`() = runTest {
        // Given: Non-retryable error, no maxRetries specified
        val survey = createSurvey(surveyId = "survey-1")
        val validationError = createValidationError()

        whenever(repository.updateSyncStatus(any(), any())).thenReturn(domainSuccess(Unit))
        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(validationError))
        whenever(repository.markSurveyAsPermanentlyFailed(any(), any())).thenReturn(
            domainSuccess(
                Unit
            )
        )

        // When: Upload without specifying maxRetries
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Default maxRetries = 3 used
        assertTrue(result is DomainResult.Error)
        verify(repository).markSurveyAsPermanentlyFailed("survey-1", 3)
    }

    // ========== STATUS TRANSITION SCENARIOS ==========

    @Test
    fun `invoke updates status to SYNCING before upload`() = runTest {
        // Given: Survey to upload
        val survey = createSurvey(surveyId = "survey-1")
        val uploadResult = createUploadResult(surveyId = "survey-1")

        whenever(repository.uploadSurvey(survey)).thenReturn(domainSuccess(uploadResult))

        val statusUpdates = mutableListOf<SyncStatus>()
        whenever(repository.updateSyncStatus(eq("survey-1"), any())).thenAnswer { invocation ->
            statusUpdates.add(invocation.getArgument(1))
            domainSuccess(Unit)
        }

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Status updated in correct order
        assertTrue(result is DomainResult.Success)
        assertEquals(2, statusUpdates.size)
        assertEquals(SyncStatus.SYNCING, statusUpdates[0]) // First: SYNCING
        assertEquals(SyncStatus.SYNCED, statusUpdates[1])  // Then: SYNCED
    }

    @Test
    fun `invoke updates status to FAILED on retryable error`() = runTest {
        // Given: Retryable error
        val survey = createSurvey(surveyId = "survey-1")
        val networkError = createNetworkFailure()

        whenever(repository.uploadSurvey(survey)).thenReturn(domainError(networkError))

        val statusUpdates = mutableListOf<SyncStatus>()
        whenever(repository.updateSyncStatus(eq("survey-1"), any())).thenAnswer { invocation ->
            statusUpdates.add(invocation.getArgument(1))
            domainSuccess(Unit)
        }
        whenever(repository.incrementSurveyRetryCount(any())).thenReturn(domainSuccess(Unit))

        // When: Upload survey
        val result = uploadSurveyUseCase.invoke(survey = survey)

        // Then: Status updated to SYNCING, then FAILED
        assertTrue(result is DomainResult.Error)
        assertEquals(2, statusUpdates.size)
        assertEquals(SyncStatus.SYNCING, statusUpdates[0])
        assertEquals(SyncStatus.FAILED, statusUpdates[1])
    }
}
