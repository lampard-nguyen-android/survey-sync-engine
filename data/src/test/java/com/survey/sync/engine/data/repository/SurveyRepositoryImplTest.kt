package com.survey.sync.engine.data.repository

import com.survey.sync.engine.data.createAnswer
import com.survey.sync.engine.data.createMediaAttachment
import com.survey.sync.engine.data.createMediaAttachmentEntity
import com.survey.sync.engine.data.createQuestionDefinitionEntity
import com.survey.sync.engine.data.createSurvey
import com.survey.sync.engine.data.createSurveyEntity
import com.survey.sync.engine.data.createUploadResponseDto
import com.survey.sync.engine.data.dao.AnswerDao
import com.survey.sync.engine.data.dao.MediaAttachmentDao
import com.survey.sync.engine.data.dao.QuestionDefinitionDao
import com.survey.sync.engine.data.dao.SurveyDao
import com.survey.sync.engine.data.domainSuccess
import com.survey.sync.engine.data.entity.SyncStatusEntity
import com.survey.sync.engine.data.pojo.FullSurveyDetail
import com.survey.sync.engine.data.remote.api.SurveyApiService
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.InputType
import com.survey.sync.engine.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/**
 * Unit tests for SurveyRepositoryImpl.
 * Tests all repository methods with success and error scenarios.
 *
 * Uses Robolectric for File operations and Android framework classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SurveyRepositoryImplTest {

    private lateinit var surveyDao: SurveyDao
    private lateinit var answerDao: AnswerDao
    private lateinit var mediaAttachmentDao: MediaAttachmentDao
    private lateinit var questionDefinitionDao: QuestionDefinitionDao
    private lateinit var apiService: SurveyApiService
    private lateinit var repository: SurveyRepositoryImpl

    @Before
    fun setup() {
        surveyDao = mock()
        answerDao = mock()
        mediaAttachmentDao = mock()
        questionDefinitionDao = mock()
        apiService = mock()

        repository = SurveyRepositoryImpl(
            surveyDao = surveyDao,
            answerDao = answerDao,
            mediaAttachmentDao = mediaAttachmentDao,
            questionDefinitionDao = questionDefinitionDao,
            apiService = apiService
        )
    }

    // ========== uploadSurvey() TESTS ==========

    @Test
    fun `uploadSurvey returns success when API call succeeds`() = runTest {
        // Given: Survey to upload
        val survey = createSurvey(surveyId = "survey-1")
        val uploadResponseDto = createUploadResponseDto(surveyId = "survey-1")

        whenever(apiService.uploadSurvey(any()))
            .thenReturn(domainSuccess(uploadResponseDto))

        // When: Upload survey
        val result = repository.uploadSurvey(survey)

        // Then: Success returned
        assertTrue(result is DomainResult.Success)
        val uploadResult = (result as DomainResult.Success).value
        assertEquals("survey-1", uploadResult.surveyId)
        assertEquals("Survey uploaded successfully", uploadResult.message)
        verify(apiService).uploadSurvey(any())
    }

    @Test
    fun `uploadSurvey returns error when API call fails`() = runTest {
        // Given: API error
        val survey = createSurvey(surveyId = "survey-1")
        val apiError = DomainError.ApiError(
            httpCode = 500,
            httpMessage = "Internal Server Error",
        )

        whenever(apiService.uploadSurvey(any()))
            .thenReturn(DomainResult.error(apiError))

        // When: Upload survey
        val result = repository.uploadSurvey(survey)

        // Then: Error returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.ApiError)
        assertEquals(500, (error as DomainError.ApiError).httpCode)
    }

    // ========== uploadMediaAttachment() TESTS ==========

    @Test
    fun `uploadMediaAttachment returns error when file does not exist`() = runTest {
        // Given: Attachment with non-existent file
        val surveyId = "survey-1"
        val attachment = createMediaAttachment(
            attachmentId = "att-1",
            localFilePath = "/non/existent/file.jpg"
        )

        // When: Upload media attachment
        val result = repository.uploadMediaAttachment(surveyId, attachment)

        // Then: ValidationError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.ValidationError)
        assertEquals("FILE_NOT_FOUND", (error as DomainError.ValidationError).errorCode)
        assertTrue(error.errorMessage.contains("not found"))

        // Verify: API not called
        verify(apiService, never()).uploadMedia(any(), any(), any(), any())
    }

    // ========== getSurveysByStatus() TESTS ==========

    @Test
    fun `getSurveysByStatus returns surveys for given status`() = runTest {
        // Given: Surveys with SYNCED status
        val surveyEntities = listOf(
            createSurveyEntity(surveyId = "survey-1", syncStatus = SyncStatusEntity.SYNCED),
            createSurveyEntity(surveyId = "survey-2", syncStatus = SyncStatusEntity.SYNCED)
        )

        whenever(surveyDao.getSurveysByStatus(SyncStatusEntity.SYNCED))
            .thenReturn(surveyEntities)

        // When: Get surveys by status
        val result = repository.getSurveysByStatus(SyncStatus.SYNCED)

        // Then: Surveys returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(2, surveys.size)
        assertEquals("survey-1", surveys[0].surveyId)
        assertEquals("survey-2", surveys[1].surveyId)
        verify(surveyDao).getSurveysByStatus(SyncStatusEntity.SYNCED)
    }

    @Test
    fun `getSurveysByStatus returns empty list when no surveys match`() = runTest {
        // Given: No surveys with FAILED status
        whenever(surveyDao.getSurveysByStatus(SyncStatusEntity.FAILED))
            .thenReturn(emptyList())

        // When: Get surveys by status
        val result = repository.getSurveysByStatus(SyncStatus.FAILED)

        // Then: Empty list returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertTrue(surveys.isEmpty())
    }

    @Test
    fun `getSurveysByStatus handles DAO exception`() = runTest {
        // Given: DAO throws exception
        whenever(surveyDao.getSurveysByStatus(any()))
            .thenThrow(RuntimeException("Database error"))

        // When: Get surveys by status
        val result = repository.getSurveysByStatus(SyncStatus.PENDING)

        // Then: DaoError returned
        assertTrue(result is DomainResult.Error)
        val error = (result as DomainResult.Error).error
        assertTrue(error is DomainError.DaoError)
    }

    // ========== getPendingSurveys() TESTS ==========

    @Test
    fun `getPendingSurveys returns pending surveys`() = runTest {
        // Given: Pending surveys with retryCount less than maxRetries
        val fullSurveyDetails = listOf(
            FullSurveyDetail(
                survey = createSurveyEntity(
                    surveyId = "survey-1",
                    syncStatus = SyncStatusEntity.PENDING,
                    retryCount = 0
                ),
                answersWithDefinitions = emptyList()
            ),
            FullSurveyDetail(
                survey = createSurveyEntity(
                    surveyId = "survey-2",
                    syncStatus = SyncStatusEntity.PENDING,
                    retryCount = 2
                ),
                answersWithDefinitions = emptyList()
            )
        )

        whenever(surveyDao.getPendingSurveys(maxRetries = 3))
            .thenReturn(fullSurveyDetails)

        // When: Get pending surveys
        val result = repository.getPendingSurveys()

        // Then: Pending surveys returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertEquals(2, surveys.size)
        verify(surveyDao).getPendingSurveys(maxRetries = 3)
    }

    @Test
    fun `getPendingSurveys returns empty list when no pending surveys`() = runTest {
        // Given: No pending surveys
        whenever(surveyDao.getPendingSurveys(maxRetries = 3))
            .thenReturn(emptyList())

        // When: Get pending surveys
        val result = repository.getPendingSurveys()

        // Then: Empty list returned
        assertTrue(result is DomainResult.Success)
        val surveys = (result as DomainResult.Success).value
        assertTrue(surveys.isEmpty())
    }

    // ========== saveSurvey() TESTS ==========

    @Test
    fun `saveSurvey saves survey and answers without media`() = runTest {
        // Given: Survey with text answers only
        val survey = createSurvey(
            surveyId = "survey-1",
            answers = listOf(
                createAnswer(
                    answerUuid = "answer-1",
                    questionKey = "farmer_name",
                    answerValue = "John"
                )
            )
        )

        whenever(questionDefinitionDao.getQuestionByKey("farmer_name"))
            .thenReturn(
                createQuestionDefinitionEntity(
                    questionKey = "farmer_name",
                    inputType = "TEXT"
                )
            )

        // When: Save survey
        val result = repository.saveSurvey(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)

        // Verify: Survey and answers saved
        verify(surveyDao).insertSurvey(any())
        verify(answerDao).insertAllAnswers(any())
        verify(mediaAttachmentDao, never()).insertAllAttachments(any())
    }

    @Test
    fun `saveSurvey creates media attachments for PHOTO answers`() = runTest {
        // Given: Survey with PHOTO answer
        val photoAnswer = createAnswer(
            answerUuid = "answer-1",
            questionKey = "farm_photo",
            answerValue = "/storage/photo.jpg"
        )
        val survey = createSurvey(
            surveyId = "survey-1",
            answers = listOf(photoAnswer)
        )

        whenever(questionDefinitionDao.getQuestionByKey("farm_photo"))
            .thenReturn(
                createQuestionDefinitionEntity(
                    questionKey = "farm_photo",
                    inputType = InputType.PHOTO.name
                )
            )
        whenever(mediaAttachmentDao.getAttachmentByAnswer("answer-1"))
            .thenReturn(null)

        // When: Save survey
        val result = repository.saveSurvey(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)

        // Verify: Media attachments created
        verify(surveyDao).insertSurvey(any())
        verify(answerDao).insertAllAnswers(any())
        verify(mediaAttachmentDao).insertAllAttachments(any())
    }

    @Test
    fun `saveSurvey reuses existing media attachment for PHOTO answers`() = runTest {
        // Given: Survey with PHOTO answer and existing attachment
        val photoAnswer = createAnswer(
            answerUuid = "answer-1",
            questionKey = "farm_photo",
            answerValue = "/storage/photo.jpg"
        )
        val survey = createSurvey(
            surveyId = "survey-1",
            answers = listOf(photoAnswer)
        )

        val existingAttachment = createMediaAttachmentEntity(
            attachmentId = "existing-att-1",
            answerUuid = "answer-1"
        )

        whenever(questionDefinitionDao.getQuestionByKey("farm_photo"))
            .thenReturn(
                createQuestionDefinitionEntity(
                    questionKey = "farm_photo",
                    inputType = InputType.PHOTO.name
                )
            )
        whenever(mediaAttachmentDao.getAttachmentByAnswer("answer-1"))
            .thenReturn(existingAttachment)

        // When: Save survey
        val result = repository.saveSurvey(survey)

        // Then: Success
        assertTrue(result is DomainResult.Success)

        // Verify: Existing attachment ID reused
        verify(mediaAttachmentDao).insertAllAttachments(argThat { entities ->
            entities.any { it.attachmentId == "existing-att-1" }
        })
    }

    @Test
    fun `saveSurvey skips blank file paths for PHOTO answers`() = runTest {
        // Given: Survey with blank PHOTO answer
        val photoAnswer = createAnswer(
            answerUuid = "answer-1",
            questionKey = "farm_photo",
            answerValue = ""  // Blank value
        )
        val survey = createSurvey(
            surveyId = "survey-1",
            answers = listOf(photoAnswer)
        )

        whenever(questionDefinitionDao.getQuestionByKey("farm_photo"))
            .thenReturn(
                createQuestionDefinitionEntity(
                    questionKey = "farm_photo",
                    inputType = InputType.PHOTO.name
                )
            )

        // When: Save survey
        val result = repository.saveSurvey(survey)

        // Then: Success but no media attachments created
        assertTrue(result is DomainResult.Success)
        verify(mediaAttachmentDao, never()).insertAllAttachments(any())
    }

    // ========== updateSyncStatus() TESTS ==========

    @Test
    fun `updateSyncStatus updates survey status successfully`() = runTest {
        // Given: Survey ID and new status
        val surveyId = "survey-1"

        // When: Update sync status
        val result = repository.updateSyncStatus(surveyId, SyncStatus.SYNCING)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(surveyDao).updateSyncStatus(surveyId, SyncStatusEntity.SYNCING)
    }

    @Test
    fun `updateSyncStatus handles all status transitions`() = runTest {
        // Given: Survey ID
        val surveyId = "survey-1"

        // When: Update to different statuses
        repository.updateSyncStatus(surveyId, SyncStatus.PENDING)
        repository.updateSyncStatus(surveyId, SyncStatus.SYNCING)
        repository.updateSyncStatus(surveyId, SyncStatus.SYNCED)
        repository.updateSyncStatus(surveyId, SyncStatus.FAILED)

        // Then: All transitions called
        verify(surveyDao).updateSyncStatus(surveyId, SyncStatusEntity.PENDING)
        verify(surveyDao).updateSyncStatus(surveyId, SyncStatusEntity.SYNCING)
        verify(surveyDao).updateSyncStatus(surveyId, SyncStatusEntity.SYNCED)
        verify(surveyDao).updateSyncStatus(surveyId, SyncStatusEntity.FAILED)
    }

    // ========== incrementSurveyRetryCount() TESTS ==========

    @Test
    fun `incrementSurveyRetryCount increments retry count`() = runTest {
        // Given: Survey ID
        val surveyId = "survey-1"

        // When: Increment retry count
        val result = repository.incrementSurveyRetryCount(surveyId)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(surveyDao).incrementRetryCount(eq(surveyId), any<Date>())
    }

    // ========== markSurveyAsPermanentlyFailed() TESTS ==========

    @Test
    fun `markSurveyAsPermanentlyFailed sets retry count to maxRetries`() = runTest {
        // Given: Survey ID and maxRetries
        val surveyId = "survey-1"
        val maxRetries = 3

        // When: Mark as permanently failed
        val result = repository.markSurveyAsPermanentlyFailed(surveyId, maxRetries)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(surveyDao).updateRetryInfo(
            surveyId = surveyId,
            retryCount = maxRetries,
            lastAttemptAt = any<Date>(),
            status = SyncStatusEntity.FAILED
        )
    }

    @Test
    fun `markSurveyAsPermanentlyFailed handles different maxRetries values`() = runTest {
        // Given: Survey ID with custom maxRetries
        val surveyId = "survey-1"

        // When: Mark as permanently failed with different maxRetries
        repository.markSurveyAsPermanentlyFailed(surveyId, 5)

        // Then: Correct retry count set
        verify(surveyDao).updateRetryInfo(
            surveyId = surveyId,
            retryCount = 5,
            lastAttemptAt = any<Date>(),
            status = SyncStatusEntity.FAILED
        )
    }

    // ========== getSurveyById() TESTS ==========

    @Test
    fun `getSurveyById returns survey when found`() = runTest {
        // Given: Survey exists
        val surveyId = "survey-1"
        val surveyEntity = createSurveyEntity(surveyId = "survey-1")
        val fullSurveyDetail = FullSurveyDetail(
            survey = surveyEntity,
            answersWithDefinitions = emptyList()
        )

        whenever(surveyDao.getFullSurveyDetail(surveyId))
            .thenReturn(fullSurveyDetail)

        // When: Get survey by ID
        val result = repository.getSurveyById(surveyId)

        // Then: Survey returned
        assertTrue(result is DomainResult.Success)
        val survey = (result as DomainResult.Success).value
        assertNotNull(survey)
        assertEquals("survey-1", survey?.surveyId)
    }

    @Test
    fun `getSurveyById returns null when survey not found`() = runTest {
        // Given: Survey does not exist
        val surveyId = "non-existent"

        whenever(surveyDao.getFullSurveyDetail(surveyId))
            .thenReturn(null)

        // When: Get survey by ID
        val result = repository.getSurveyById(surveyId)

        // Then: Null returned
        assertTrue(result is DomainResult.Success)
        val survey = (result as DomainResult.Success).value
        assertNull(survey)
    }

    // ========== deleteSurvey() TESTS ==========

    @Test
    fun `deleteSurvey deletes survey successfully`() = runTest {
        // Given: Survey ID
        val surveyId = "survey-1"

        // When: Delete survey
        val result = repository.deleteSurvey(surveyId)

        // Then: Success
        assertTrue(result is DomainResult.Success)
        verify(surveyDao).deleteSurveyById(surveyId)
    }

    // ========== getMediaAttachments() TESTS ==========

    @Test
    fun `getMediaAttachments returns attachments for survey`() = runTest {
        // Given: Survey with attachments
        val surveyId = "survey-1"
        val attachmentEntities = listOf(
            createMediaAttachmentEntity(attachmentId = "att-1", parentSurveyId = surveyId),
            createMediaAttachmentEntity(attachmentId = "att-2", parentSurveyId = surveyId)
        )

        whenever(mediaAttachmentDao.getAttachmentsBySurvey(surveyId))
            .thenReturn(attachmentEntities)

        // When: Get media attachments
        val result = repository.getMediaAttachments(surveyId)

        // Then: Attachments returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertEquals(2, attachments.size)
        assertEquals("att-1", attachments[0].attachmentId)
        assertEquals("att-2", attachments[1].attachmentId)
    }

    @Test
    fun `getMediaAttachments returns empty list when no attachments`() = runTest {
        // Given: Survey with no attachments
        val surveyId = "survey-1"

        whenever(mediaAttachmentDao.getAttachmentsBySurvey(surveyId))
            .thenReturn(emptyList())

        // When: Get media attachments
        val result = repository.getMediaAttachments(surveyId)

        // Then: Empty list returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertTrue(attachments.isEmpty())
    }

    // ========== getOldestSyncedAttachments() TESTS ==========

    @Test
    fun `getOldestSyncedAttachments returns limited old attachments`() = runTest {
        // Given: Multiple old synced attachments
        val oldDate = Date(System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L))
        val attachmentEntities = (1..5).map {
            createMediaAttachmentEntity(
                attachmentId = "att-$it",
                syncStatus = SyncStatusEntity.SYNCED,
                uploadedAt = oldDate
            )
        }

        whenever(mediaAttachmentDao.getSyncedAttachmentsOlderThan(any<Date>()))
            .thenReturn(attachmentEntities)

        // When: Get oldest synced attachments with limit
        val result = repository.getOldestSyncedAttachments(limit = 3, daysOld = 7)

        // Then: Limited attachments returned
        assertTrue(result is DomainResult.Success)
        val attachments = (result as DomainResult.Success).value
        assertEquals(3, attachments.size)
    }

    @Test
    fun `getOldestSyncedAttachments calculates threshold date correctly`() = runTest {
        // Given: DAO setup
        whenever(mediaAttachmentDao.getSyncedAttachmentsOlderThan(any<Date>()))
            .thenReturn(emptyList())

        // When: Get attachments older than 7 days
        repository.getOldestSyncedAttachments(limit = 10, daysOld = 7)

        // Then: Correct threshold date passed
        verify(mediaAttachmentDao).getSyncedAttachmentsOlderThan(argThat { date ->
            val expectedTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            val actualTime = date.time
            // Allow 1 second tolerance for test execution time
            Math.abs(expectedTime - actualTime) < 1000
        })
    }

    // ========== deleteAttachmentsByIds() TESTS ==========

    @Test
    fun `deleteAttachmentsByIds deletes attachments successfully`() = runTest {
        // Given: Attachments to delete
        val attachmentIds = listOf("att-1", "att-2")
        val attachment1 = createMediaAttachmentEntity(
            attachmentId = "att-1",
            localFilePath = "/storage/photo1.jpg"
        )
        val attachment2 = createMediaAttachmentEntity(
            attachmentId = "att-2",
            localFilePath = "/storage/photo2.jpg"
        )

        whenever(mediaAttachmentDao.getAttachmentById("att-1")).thenReturn(attachment1)
        whenever(mediaAttachmentDao.getAttachmentById("att-2")).thenReturn(attachment2)

        // When: Delete attachments
        val result = repository.deleteAttachmentsByIds(attachmentIds)

        // Then: Both deleted
        assertTrue(result is DomainResult.Success)
        val deletedCount = (result as DomainResult.Success).value
        assertEquals(2, deletedCount)
        verify(mediaAttachmentDao).deleteAttachmentById("att-1")
        verify(mediaAttachmentDao).deleteAttachmentById("att-2")
    }

    @Test
    fun `deleteAttachmentsByIds skips non-existent attachments`() = runTest {
        // Given: Mix of existing and non-existent attachments
        val attachmentIds = listOf("att-1", "att-2", "att-3")
        val attachment1 = createMediaAttachmentEntity(attachmentId = "att-1")

        whenever(mediaAttachmentDao.getAttachmentById("att-1")).thenReturn(attachment1)
        whenever(mediaAttachmentDao.getAttachmentById("att-2")).thenReturn(null)
        whenever(mediaAttachmentDao.getAttachmentById("att-3")).thenReturn(null)

        // When: Delete attachments
        val result = repository.deleteAttachmentsByIds(attachmentIds)

        // Then: Only existing attachment deleted
        assertTrue(result is DomainResult.Success)
        val deletedCount = (result as DomainResult.Success).value
        assertEquals(1, deletedCount)
        verify(mediaAttachmentDao).deleteAttachmentById("att-1")
        verify(mediaAttachmentDao, never()).deleteAttachmentById("att-2")
        verify(mediaAttachmentDao, never()).deleteAttachmentById("att-3")
    }

    // ========== cleanupSyncedAttachments() TESTS ==========

    @Test
    fun `cleanupSyncedAttachments deletes synced attachments for survey`() = runTest {
        // Given: Survey with synced attachments
        val surveyId = "survey-1"
        val attachmentEntities = listOf(
            createMediaAttachmentEntity(
                attachmentId = "att-1",
                parentSurveyId = surveyId,
                syncStatus = SyncStatusEntity.SYNCED,
                uploadedAt = Date()
            ),
            createMediaAttachmentEntity(
                attachmentId = "att-2",
                parentSurveyId = surveyId,
                syncStatus = SyncStatusEntity.SYNCED,
                uploadedAt = Date()
            )
        )

        whenever(mediaAttachmentDao.getAttachmentsBySurvey(surveyId))
            .thenReturn(attachmentEntities)

        // When: Cleanup synced attachments
        val result = repository.cleanupSyncedAttachments(surveyId)

        // Then: Attachments deleted
        assertTrue(result is DomainResult.Success)
        val deletedCount = (result as DomainResult.Success).value
        assertEquals(2, deletedCount)
        verify(mediaAttachmentDao).deleteAttachmentById("att-1")
        verify(mediaAttachmentDao).deleteAttachmentById("att-2")
    }

    @Test
    fun `cleanupSyncedAttachments skips pending attachments`() = runTest {
        // Given: Survey with mix of synced and pending attachments
        val surveyId = "survey-1"
        val attachmentEntities = listOf(
            createMediaAttachmentEntity(
                attachmentId = "att-1",
                syncStatus = SyncStatusEntity.SYNCED,
                uploadedAt = Date()
            ),
            createMediaAttachmentEntity(
                attachmentId = "att-2",
                syncStatus = SyncStatusEntity.PENDING,
                uploadedAt = null
            )
        )

        whenever(mediaAttachmentDao.getAttachmentsBySurvey(surveyId))
            .thenReturn(attachmentEntities)

        // When: Cleanup synced attachments
        val result = repository.cleanupSyncedAttachments(surveyId)

        // Then: Only synced attachment deleted
        assertTrue(result is DomainResult.Success)
        val deletedCount = (result as DomainResult.Success).value
        assertEquals(1, deletedCount)
        verify(mediaAttachmentDao).deleteAttachmentById("att-1")
        verify(mediaAttachmentDao, never()).deleteAttachmentById("att-2")
    }

    // ========== cleanupOldSyncedAttachments() TESTS ==========

    @Test
    fun `cleanupOldSyncedAttachments deletes old synced attachments`() = runTest {
        // Given: Old synced attachments
        val oldTimestamp = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        val oldAttachments = listOf(
            createMediaAttachmentEntity(
                attachmentId = "att-1",
                uploadedAt = Date(oldTimestamp)
            ),
            createMediaAttachmentEntity(
                attachmentId = "att-2",
                uploadedAt = Date(oldTimestamp)
            )
        )

        whenever(mediaAttachmentDao.getSyncedAttachmentsOlderThan(any<Date>()))
            .thenReturn(oldAttachments)

        // When: Cleanup old synced attachments
        val result = repository.cleanupOldSyncedAttachments(olderThan = oldTimestamp)

        // Then: Old attachments deleted
        assertTrue(result is DomainResult.Success)
        val deletedCount = (result as DomainResult.Success).value
        assertEquals(2, deletedCount)
        verify(mediaAttachmentDao).deleteAttachmentById("att-1")
        verify(mediaAttachmentDao).deleteAttachmentById("att-2")
    }
}
