package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.createApiError
import com.survey.sync.engine.domain.createInternalError
import com.survey.sync.engine.domain.createMediaAttachment
import com.survey.sync.engine.domain.createMediaUploadResult
import com.survey.sync.engine.domain.createNetworkFailure
import com.survey.sync.engine.domain.domainError
import com.survey.sync.engine.domain.domainSuccess
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.MediaAttachment
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for UploadMediaAttachmentsUseCase.
 * Tests uploading media attachments with success and error scenarios.
 */
class UploadMediaAttachmentsUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var uploadMediaAttachmentsUseCase: UploadMediaAttachmentsUseCase

    @Before
    fun setup() {
        repository = mock()
        uploadMediaAttachmentsUseCase = UploadMediaAttachmentsUseCase(repository)
    }

    // ========== uploadSingle() TESTS ==========

    @Test
    fun `uploadSingle returns success when upload succeeds`() = runTest {
        // Given: Single attachment to upload
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(attachmentId = "att-1")
        val uploadResult = createMediaUploadResult(attachmentId = "att-1")

        whenever(repository.uploadMediaAttachment(surveyId, attachment))
            .thenReturn(domainSuccess(uploadResult))

        // When: Upload single attachment
        val result = uploadMediaAttachmentsUseCase.uploadSingle(surveyId, attachment)

        // Then: Success returned
        assertTrue(result is DomainResult.Success)
        assertEquals(uploadResult, (result as DomainResult.Success).value)
        verify(repository).uploadMediaAttachment(surveyId, attachment)
    }

    @Test
    fun `uploadSingle returns NetworkFailure when network fails`() = runTest {
        // Given: Network failure
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(attachmentId = "att-1")
        val networkError = createNetworkFailure(message = "Network unavailable")

        whenever(repository.uploadMediaAttachment(surveyId, attachment))
            .thenReturn(domainError(networkError))

        // When: Upload single attachment
        val result = uploadMediaAttachmentsUseCase.uploadSingle(surveyId, attachment)

        // Then: NetworkFailure returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.NetworkFailure)
        assertEquals("Network unavailable", error.errorMessage)
    }

    @Test
    fun `uploadSingle returns ApiError when server rejects upload`() = runTest {
        // Given: API error
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(attachmentId = "att-1")
        val apiError = createApiError(httpCode = 413, httpMessage = "File too large")

        whenever(repository.uploadMediaAttachment(surveyId, attachment))
            .thenReturn(domainError(apiError))

        // When: Upload single attachment
        val result = uploadMediaAttachmentsUseCase.uploadSingle(surveyId, attachment)

        // Then: ApiError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.ApiError)
        assertEquals(413, (error as DomainError.ApiError).httpCode)
    }

    // ========== invoke() SUCCESS SCENARIOS ==========

    @Test
    fun `invoke returns empty map when no attachments to upload`() = runTest {
        // Given: Empty attachments list
        val surveyId = "survey-1"
        val attachments = emptyList<MediaAttachment>()

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: Empty map returned
        assertTrue(results.isEmpty())
        verify(repository, never()).uploadMediaAttachment(any(), any())
    }

    @Test
    fun `invoke successfully uploads single attachment`() = runTest {
        // Given: Single attachment
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(attachmentId = "att-1")
        val uploadResult = createMediaUploadResult(attachmentId = "att-1")

        whenever(repository.uploadMediaAttachment(surveyId, attachment))
            .thenReturn(domainSuccess(uploadResult))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, listOf(attachment))

        // Then: Single result returned
        assertEquals(1, results.size)
        assertTrue(results.containsKey("att-1"))
        assertTrue(results["att-1"] is DomainResult.Success)
        assertEquals(uploadResult, (results["att-1"] as DomainResult.Success).value)
    }

    @Test
    fun `invoke successfully uploads multiple attachments`() = runTest {
        // Given: Multiple attachments
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )

        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenAnswer { invocation ->
                val att = invocation.getArgument<MediaAttachment>(1)
                domainSuccess(createMediaUploadResult(attachmentId = att.attachmentId))
            }

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: All results returned successfully
        assertEquals(3, results.size)
        assertTrue(results.containsKey("att-1"))
        assertTrue(results.containsKey("att-2"))
        assertTrue(results.containsKey("att-3"))
        assertTrue(results.all { it.value is DomainResult.Success })

        // Verify: All uploads attempted
        verify(repository, times(3)).uploadMediaAttachment(eq(surveyId), any())
    }

    // ========== invoke() PARTIAL FAILURE SCENARIOS ==========

    @Test
    fun `invoke continues uploading when some attachments fail`() = runTest {
        // Given: 3 attachments, first fails, others succeed
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )

        val networkError = createNetworkFailure()

        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[0])))
            .thenReturn(domainError(networkError))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[1])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-2")))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[2])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-3")))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: All attachments attempted, 1 failed, 2 succeeded
        assertEquals(3, results.size)
        assertTrue(results["att-1"] is DomainResult.Error)
        assertTrue(results["att-2"] is DomainResult.Success)
        assertTrue(results["att-3"] is DomainResult.Success)

        // Verify: All uploads attempted despite first failure
        verify(repository, times(3)).uploadMediaAttachment(eq(surveyId), any())
    }

    @Test
    fun `invoke tracks partial success with mixed results`() = runTest {
        // Given: 5 attachments with mixed results
        val surveyId = "survey-1"
        val attachments = (1..5).map {
            createMediaAttachment(attachmentId = "att-$it")
        }

        // att-1: success, att-2: network fail, att-3: success, att-4: api error, att-5: success
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[0])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-1")))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[1])))
            .thenReturn(domainError(createNetworkFailure()))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[2])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-3")))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[3])))
            .thenReturn(domainError(createApiError(httpCode = 500)))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[4])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-5")))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: 5 results with 3 successes, 2 failures
        assertEquals(5, results.size)
        val successCount = results.values.count { it is DomainResult.Success }
        val failureCount = results.values.count { it is DomainResult.Error }
        assertEquals(3, successCount)
        assertEquals(2, failureCount)

        // Verify specific results
        assertTrue(results["att-1"] is DomainResult.Success)
        assertTrue(results["att-2"] is DomainResult.Error)
        assertTrue(results["att-3"] is DomainResult.Success)
        assertTrue(results["att-4"] is DomainResult.Error)
        assertTrue(results["att-5"] is DomainResult.Success)
    }

    @Test
    fun `invoke handles all attachments failing`() = runTest {
        // Given: Multiple attachments, all fail
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )

        val networkError = createNetworkFailure()
        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenReturn(domainError(networkError))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: All results are errors
        assertEquals(3, results.size)
        assertTrue(results.values.all { it is DomainResult.Error })
        verify(repository, times(3)).uploadMediaAttachment(eq(surveyId), any())
    }

    // ========== ERROR TYPE SCENARIOS ==========

    @Test
    fun `invoke handles different error types for different attachments`() = runTest {
        // Given: 3 attachments with different error types
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1"),
            createMediaAttachment(attachmentId = "att-2"),
            createMediaAttachment(attachmentId = "att-3")
        )

        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[0])))
            .thenReturn(domainError(createNetworkFailure()))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[1])))
            .thenReturn(domainError(createApiError(httpCode = 413)))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[2])))
            .thenReturn(domainError(createInternalError()))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: Different error types tracked
        assertEquals(3, results.size)
        assertTrue((results["att-1"] as DomainResult.Error).error is DomainError.NetworkFailure)
        assertTrue((results["att-2"] as DomainResult.Error).error is DomainError.ApiError)
        assertTrue((results["att-3"] as DomainResult.Error).error is DomainError.InternalError)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `invoke handles large number of attachments`() = runTest {
        // Given: Many attachments
        val surveyId = "survey-1"
        val attachments = (1..20).map {
            createMediaAttachment(attachmentId = "att-$it")
        }

        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenAnswer { invocation ->
                val att = invocation.getArgument<MediaAttachment>(1)
                domainSuccess(createMediaUploadResult(attachmentId = att.attachmentId))
            }

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: All results returned
        assertEquals(20, results.size)
        assertTrue(results.all { it.value is DomainResult.Success })
        verify(repository, times(20)).uploadMediaAttachment(eq(surveyId), any())
    }

    @Test
    fun `invoke processes attachments in order`() = runTest {
        // Given: 3 attachments in specific order
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-first"),
            createMediaAttachment(attachmentId = "att-second"),
            createMediaAttachment(attachmentId = "att-third")
        )

        val uploadOrder = mutableListOf<String>()
        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenAnswer { invocation ->
                val att = invocation.getArgument<MediaAttachment>(1)
                uploadOrder.add(att.attachmentId)
                domainSuccess(createMediaUploadResult(attachmentId = att.attachmentId))
            }

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: Attachments processed in order
        assertEquals(3, results.size)
        assertEquals(listOf("att-first", "att-second", "att-third"), uploadOrder)
    }

    @Test
    fun `invoke handles attachments with different file sizes`() = runTest {
        // Given: Attachments with varying file sizes
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1", fileSize = 100_000L),      // 100 KB
            createMediaAttachment(attachmentId = "att-2", fileSize = 10_000_000L),   // 10 MB
            createMediaAttachment(attachmentId = "att-3", fileSize = 50_000_000L)    // 50 MB
        )

        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenAnswer { invocation ->
                val att = invocation.getArgument<MediaAttachment>(1)
                domainSuccess(createMediaUploadResult(attachmentId = att.attachmentId))
            }

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: All sizes handled successfully
        assertEquals(3, results.size)
        assertTrue(results.all { it.value is DomainResult.Success })
    }

    @Test
    fun `invoke returns correct attachment ID keys in result map`() = runTest {
        // Given: Multiple attachments with specific IDs
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "photo-123"),
            createMediaAttachment(attachmentId = "image-456"),
            createMediaAttachment(attachmentId = "pic-789")
        )

        whenever(repository.uploadMediaAttachment(eq(surveyId), any()))
            .thenReturn(domainSuccess(createMediaUploadResult("dummy")))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: Result map uses correct attachment IDs as keys
        assertEquals(3, results.size)
        assertTrue(results.containsKey("photo-123"))
        assertTrue(results.containsKey("image-456"))
        assertTrue(results.containsKey("pic-789"))
    }

    @Test
    fun `invoke can be called multiple times for different surveys`() = runTest {
        // Given: Multiple surveys with attachments
        val survey1Attachments = listOf(createMediaAttachment(attachmentId = "att-1"))
        val survey2Attachments = listOf(createMediaAttachment(attachmentId = "att-2"))

        whenever(repository.uploadMediaAttachment(any(), any()))
            .thenReturn(domainSuccess(createMediaUploadResult("dummy")))

        // When: Upload for different surveys
        val results1 = uploadMediaAttachmentsUseCase.invoke("survey-1", survey1Attachments)
        val results2 = uploadMediaAttachmentsUseCase.invoke("survey-2", survey2Attachments)

        // Then: Both succeed
        assertEquals(1, results1.size)
        assertEquals(1, results2.size)
        verify(repository).uploadMediaAttachment("survey-1", survey1Attachments[0])
        verify(repository).uploadMediaAttachment("survey-2", survey2Attachments[0])
    }

    // ========== COUNT VERIFICATION ==========

    @Test
    fun `invoke result map allows counting successes and failures`() = runTest {
        // Given: 4 attachments, 2 succeed, 2 fail
        val surveyId = "survey-1"
        val attachments = (1..4).map { createMediaAttachment(attachmentId = "att-$it") }

        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[0])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-1")))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[1])))
            .thenReturn(domainError(createNetworkFailure()))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[2])))
            .thenReturn(domainSuccess(createMediaUploadResult("att-3")))
        whenever(repository.uploadMediaAttachment(eq(surveyId), eq(attachments[3])))
            .thenReturn(domainError(createNetworkFailure()))

        // When: Upload attachments
        val results = uploadMediaAttachmentsUseCase.invoke(surveyId, attachments)

        // Then: Can count successes and failures
        val successCount = results.values.count { it is DomainResult.Success }
        val failureCount = results.values.count { it is DomainResult.Error }

        assertEquals(2, successCount)
        assertEquals(2, failureCount)
        assertEquals(4, results.size)
    }
}
