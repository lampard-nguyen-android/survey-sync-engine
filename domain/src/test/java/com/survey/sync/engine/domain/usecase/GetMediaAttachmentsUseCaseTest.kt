package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.domain.createDaoError
import com.survey.sync.engine.domain.createMediaAttachment
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
 * Unit tests for GetMediaAttachmentsUseCase.
 * Tests retrieving media attachments for surveys from local database.
 */
class GetMediaAttachmentsUseCaseTest {

    private lateinit var repository: SurveyRepository
    private lateinit var getMediaAttachmentsUseCase: GetMediaAttachmentsUseCase

    @Before
    fun setup() {
        repository = mock()
        getMediaAttachmentsUseCase = GetMediaAttachmentsUseCase(repository)
    }

    // ========== SUCCESS SCENARIOS ==========

    @Test
    fun `invoke returns empty list when survey has no attachments`() = runTest {
        // Given: Survey with no attachments
        val surveyId = "survey-1"
        whenever(repository.getMediaAttachments(surveyId)).thenReturn(domainSuccess(emptyList()))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: Empty list returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertTrue(attachments.isEmpty())
        verify(repository).getMediaAttachments(surveyId)
    }

    @Test
    fun `invoke returns single media attachment`() = runTest {
        // Given: Survey with one attachment
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(
            attachmentId = "att-1",
            answerUuid = "answer-1",
            syncStatus = SyncStatus.PENDING
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(listOf(attachment)))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: Single attachment returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertEquals(1, attachments.size)
        assertEquals(attachment, attachments[0])
    }

    @Test
    fun `invoke returns multiple media attachments`() = runTest {
        // Given: Survey with multiple attachments
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1", answerUuid = "answer-1"),
            createMediaAttachment(attachmentId = "att-2", answerUuid = "answer-2"),
            createMediaAttachment(attachmentId = "att-3", answerUuid = "answer-3")
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: All attachments returned
        assertTrue(result is DomainResult.Success)
        val resultAttachments = (result as DomainResult.Success).value
        assertEquals(3, resultAttachments.size)
        assertEquals(attachments, resultAttachments)
    }

    @Test
    fun `invoke returns attachments with different sync statuses`() = runTest {
        // Given: Attachments with various sync statuses
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1", syncStatus = SyncStatus.PENDING),
            createMediaAttachment(attachmentId = "att-2", syncStatus = SyncStatus.SYNCING),
            createMediaAttachment(attachmentId = "att-3", syncStatus = SyncStatus.SYNCED),
            createMediaAttachment(attachmentId = "att-4", syncStatus = SyncStatus.FAILED)
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: All attachments returned regardless of status
        assertTrue(result is DomainResult.Success)
        val resultAttachments = (result as DomainResult.Success).value
        assertEquals(4, resultAttachments.size)
        assertTrue(resultAttachments.any { it.syncStatus == SyncStatus.PENDING })
        assertTrue(resultAttachments.any { it.syncStatus == SyncStatus.SYNCING })
        assertTrue(resultAttachments.any { it.syncStatus == SyncStatus.SYNCED })
        assertTrue(resultAttachments.any { it.syncStatus == SyncStatus.FAILED })
    }

    @Test
    fun `invoke returns attachments with different file sizes`() = runTest {
        // Given: Attachments with various file sizes
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(attachmentId = "att-1", fileSize = 100_000L),     // 100 KB
            createMediaAttachment(attachmentId = "att-2", fileSize = 1_000_000L),   // 1 MB
            createMediaAttachment(attachmentId = "att-3", fileSize = 10_000_000L)   // 10 MB
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: All attachments returned with correct sizes
        assertTrue(result is DomainResult.Success)
        val resultAttachments = (result as DomainResult.Success).value
        assertEquals(3, resultAttachments.size)
        assertEquals(100_000L, resultAttachments[0].fileSize)
        assertEquals(1_000_000L, resultAttachments[1].fileSize)
        assertEquals(10_000_000L, resultAttachments[2].fileSize)
    }

    @Test
    fun `invoke can be called for different surveys`() = runTest {
        // Given: Multiple surveys with different attachments
        val survey1Attachments = listOf(createMediaAttachment(attachmentId = "att-1"))
        val survey2Attachments = listOf(createMediaAttachment(attachmentId = "att-2"))

        whenever(repository.getMediaAttachments("survey-1"))
            .thenReturn(domainSuccess(survey1Attachments))
        whenever(repository.getMediaAttachments("survey-2"))
            .thenReturn(domainSuccess(survey2Attachments))

        // When: Get attachments for different surveys
        val result1 = getMediaAttachmentsUseCase.invoke("survey-1")
        val result2 = getMediaAttachmentsUseCase.invoke("survey-2")

        // Then: Correct attachments returned for each survey
        assertTrue(result1 is DomainResult.Success)
        assertTrue(result2 is DomainResult.Success)
        assertEquals("att-1", (result1 as DomainResult.Success).value[0].attachmentId)
        assertEquals("att-2", (result2 as DomainResult.Success).value[0].attachmentId)
    }

    @Test
    fun `invoke can be called multiple times for same survey`() = runTest {
        // Given: Survey with attachments
        val surveyId = "survey-1"
        val attachments = listOf(createMediaAttachment(attachmentId = "att-1"))

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Call multiple times
        val result1 = getMediaAttachmentsUseCase.invoke(surveyId)
        val result2 = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: Both calls succeed
        assertTrue(result1 is DomainResult.Success)
        assertTrue(result2 is DomainResult.Success)
        verify(repository, times(2)).getMediaAttachments(surveyId)
    }

    // ========== ERROR SCENARIOS ==========

    @Test
    fun `invoke returns DaoError when database query fails`() = runTest {
        // Given: Database error
        val surveyId = "survey-1"
        val daoError = createDaoError(operation = "query", throwable = Throwable("Database locked"))

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainError(daoError))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertEquals("query", (error as DomainError.DaoError).operation)
        assertEquals("Database error during query: Database locked", error.errorMessage)
    }

    @Test
    fun `invoke returns DaoError on database corruption`() = runTest {
        // Given: Database corruption
        val surveyId = "survey-1"
        val daoError = createDaoError(
            operation = "query",
            message = "Database disk image is malformed"
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainError(daoError))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
        assertTrue(error.errorMessage.contains("malformed"))
    }

    @Test
    fun `invoke returns DaoError when survey does not exist`() = runTest {
        // Given: Non-existent survey
        val surveyId = "non-existent-survey"
        val daoError = createDaoError(
            operation = "query",
            message = "Survey not found"
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainError(daoError))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `invoke handles large number of attachments`() = runTest {
        // Given: Survey with many attachments
        val surveyId = "survey-1"
        val largeAttachmentList = (1..50).map {
            createMediaAttachment(attachmentId = "att-$it")
        }

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(largeAttachmentList))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: All attachments returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertEquals(50, attachments.size)
    }

    @Test
    fun `invoke returns attachments with various file paths`() = runTest {
        // Given: Attachments with different file paths
        val surveyId = "survey-1"
        val attachments = listOf(
            createMediaAttachment(
                attachmentId = "att-1",
                localFilePath = "/storage/emulated/0/Pictures/photo1.jpg"
            ),
            createMediaAttachment(
                attachmentId = "att-2",
                localFilePath = "/storage/emulated/0/Pictures/photo2.png"
            ),
            createMediaAttachment(
                attachmentId = "att-3",
                localFilePath = "/storage/emulated/0/DCIM/Camera/IMG_001.jpg"
            )
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: All attachments with file paths returned
        assertTrue(result is DomainResult.Success)
        val resultAttachments = (result as DomainResult.Success).value
        assertEquals(3, resultAttachments.size)
        assertTrue(resultAttachments[0].localFilePath.contains("photo1.jpg"))
        assertTrue(resultAttachments[1].localFilePath.contains("photo2.png"))
        assertTrue(resultAttachments[2].localFilePath.contains("IMG_001.jpg"))
    }

    @Test
    fun `invoke returns attachments with upload timestamps`() = runTest {
        // Given: Mix of uploaded and pending attachments
        val surveyId = "survey-1"
        val now = System.currentTimeMillis()
        val attachments = listOf(
            createMediaAttachment(
                attachmentId = "att-1",
                uploadedAt = now,
                syncStatus = SyncStatus.SYNCED
            ),
            createMediaAttachment(
                attachmentId = "att-2",
                uploadedAt = null,
                syncStatus = SyncStatus.PENDING
            )
        )

        whenever(repository.getMediaAttachments(surveyId))
            .thenReturn(domainSuccess(attachments))

        // When: Get media attachments
        val result = getMediaAttachmentsUseCase.invoke(surveyId)

        // Then: Attachments with correct timestamps returned
        assertTrue(result is DomainResult.Success)
        val resultAttachments = (result as DomainResult.Success).value
        assertEquals(2, resultAttachments.size)
        assertEquals(now, resultAttachments[0].uploadedAt)
        assertEquals(null, resultAttachments[1].uploadedAt)
    }
}
