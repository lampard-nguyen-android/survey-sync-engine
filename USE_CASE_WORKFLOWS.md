# Use Case Workflows Documentation

This document provides detailed insights into the core domain layer use cases that power
SurveySyncEngine's synchronization logic. These use cases encapsulate business logic for uploading
surveys, managing media attachments, and orchestrating batch synchronization with intelligent
failure handling.

---

## Table of Contents

1. [BatchSyncUseCase - Orchestrating Batch Synchronization](#1-batchsyncusecase)
2. [UploadSurveyUseCase - Individual Survey Upload](#2-uploadsurveyusecase)
3. [GetMediaAttachmentsUseCase - Media Retrieval](#3-getmediaattachmentsusecase)

---

## 1. BatchSyncUseCase

**File Path**: `domain/src/main/java/com/survey/sync/engine/domain/usecase/BatchSyncUseCase.kt`

### Overview

BatchSyncUseCase orchestrates the synchronization of multiple pending surveys with intelligent
failure handling, circuit breaker pattern, and device-aware optimizations. It's designed for field
operations where network reliability is unpredictable and device resources (battery, storage) must
be conserved.

### Process Flow

```
┌─────────────────────────────────────────────────────────────┐
│ BatchSyncUseCase.invoke()                                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────┐
    │ Get all pending surveys from repository│
    │ (status = PENDING or FAILED)           │
    └────────────────┬───────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────┐
    │ Initialize tracking:                   │
    │ - NetworkHealthTracker (circuit breaker│
    │ - Result collections (success/fail/skip)│
    └────────────────┬───────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────┐
    │ FOR EACH survey in pending list:      │
    └────────────────┬───────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │ [1] Check Storage              │
        │ Available < 200MB?             │
        └─────┬──────────────────┬───────┘
              │ YES              │ NO
              │                  │
              ▼                  ▼
        ┌─────────────┐    ┌────────────────────┐
        │ STOP sync   │    │ [2] Check Circuit  │
        │ StopReason: │    │ Breaker            │
        │ STORAGE_    │    │ shouldContinueSync?│
        │ CRITICAL    │    └─────┬──────────────┘
        └─────────────┘          │
                                 ▼
                        ┌────────┴────────┐
                        │ NO (3+ failures)│ YES
                        ▼                 ▼
                  ┌─────────────┐   ┌────────────────────┐
                  │ Skip survey │   │ [3] Sync Survey    │
                  │ Mark skipped│   │ syncSingleSurvey() │
                  │ STOP sync   │   └─────┬──────────────┘
                  │ StopReason: │         │
                  │ NETWORK_DOWN│         ▼
                  └─────────────┘   ┌─────┴──────┐
                                    │ SUCCESS?   │
                                    └─────┬──────┘
                                          │
                        ┌─────────────────┼─────────────────┐
                        │ YES              │ NO              │
                        ▼                  ▼
              ┌─────────────────┐   ┌─────────────────┐
              │ Record success  │   │ Record failure  │
              │ Reset circuit   │   │ Track for retry │
              │ breaker counter │   │ Increment       │
              │                 │   │ circuit breaker │
              └─────────────────┘   └─────────────────┘
                        │                  │
                        └────────┬─────────┘
                                 │
                                 ▼
                        ┌────────────────┐
                        │ Continue to    │
                        │ next survey    │
                        └────────┬───────┘
                                 │
                                 ▼
    ┌────────────────────────────────────────┐
    │ All surveys processed or stopped early │
    └────────────────┬───────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────┐
    │ Return BatchSyncResult:                │
    │ - Total surveys attempted              │
    │ - Success count                        │
    │ - Failure count                        │
    │ - Skipped count                        │
    │ - Individual survey results            │
    │ - Stop reason                          │
    │ - Network health status                │
    └────────────────────────────────────────┘
```

### Circuit Breaker Pattern

The circuit breaker prevents wasting battery and data when the network is unreliable. It uses
`NetworkHealthTracker` to monitor consecutive network failures.

**Three States**:

```
┌───────────────────────────────────────────────────────────────┐
│ State: HEALTHY                                                │
│ Consecutive Failures: 0-1                                     │
│ Action: Continue syncing normally                             │
└───────────────────────────────────────────────────────────────┘
                         │
                         │ 2nd network failure
                         ▼
┌───────────────────────────────────────────────────────────────┐
│ State: DEGRADED                                               │
│ Consecutive Failures: 2                                       │
│ Action: Continue syncing, but closely monitor                 │
└───────────────────────────────────────────────────────────────┘
                         │
                         │ 3rd network failure
                         ▼
┌───────────────────────────────────────────────────────────────┐
│ State: CIRCUIT_OPEN                                           │
│ Consecutive Failures: 3+                                      │
│ Action: STOP sync immediately (network likely down)           │
└───────────────────────────────────────────────────────────────┘
```

**Implementation** (Lines 145-158):

```kotlin
// Check network health before each upload (circuit breaker pattern)
if (!networkHealthTracker.shouldContinueSync) {
    // Network likely down - stop early to conserve battery and data
    val skippedResult = SurveyResult(
        surveyId = survey.surveyId,
        isSuccess = false,
        isSkipped = true,
        errorMessage = "Skipped: Network likely down (${networkHealthTracker.healthStatus})"
    )
    results[survey.surveyId] = skippedResult
    skippedIds.add(survey.surveyId)
    skippedCount++
    stopReason = StopReason.NETWORK_DOWN
    continue  // Skip remaining surveys
}
```

**Failure Recording Logic**:

Only network-related errors count toward circuit breaker:

- Network timeouts (408)
- Network unavailable (no connection)
- Server unavailable (503)

**Not counted**:

- Validation errors (400)
- Authentication errors (401, 403)
- Internal errors (bugs)

### Partial Failure Handling

BatchSyncUseCase handles partial failures gracefully using granular tracking.

**Result Structure**:

```kotlin
data class BatchSyncResult(
    val totalSurveys: Int,                    // Total attempted
    val successCount: Int,                    // Successfully synced
    val failureCount: Int,                    // Failed to sync
    val skippedCount: Int = 0,                // Skipped (circuit breaker)
    val surveyResults: Map<String, SurveyResult>,  // Individual results
    val succeededSurveyIds: List<String> = emptyList(),
    val failedSurveyIds: List<String> = emptyList(),
    val skippedSurveyIds: List<String> = emptyList(),
    val stopReason: StopReason = StopReason.COMPLETED,
    val networkHealthStatus: HealthStatus = HealthStatus.HEALTHY
)
```

**Key Behavior**:

- **Successful surveys** are marked `SYNCED` and won't be re-uploaded
- **Failed surveys** remain `PENDING` for next sync attempt
- **Skipped surveys** remain `PENDING` (circuit breaker opened early)
- Each survey has individual `SurveyResult` with media upload details

**Example Scenario**:

```
Batch of 10 surveys:
───────────────────────────────────────────────────────
Survey 1:  Upload SUCCESS → Media 2/2 SUCCESS → SYNCED
Survey 2:  Upload SUCCESS → Media 3/3 SUCCESS → SYNCED
Survey 3:  Upload SUCCESS → Media 1/3 FAILED  → SYNCED (partial media)
Survey 4:  Upload SUCCESS → No media         → SYNCED
Survey 5:  Upload SUCCESS → Media 2/2 SUCCESS → SYNCED
Survey 6:  Upload FAILED  → (network timeout)  → PENDING (1st failure)
Survey 7:  Upload FAILED  → (network timeout)  → PENDING (2nd failure)
Survey 8:  Upload FAILED  → (network timeout)  → PENDING (3rd failure - circuit opens!)
Survey 9:  SKIPPED (circuit breaker opened)   → PENDING
Survey 10: SKIPPED (circuit breaker opened)   → PENDING

Result:
- totalSurveys: 10
- successCount: 5
- failureCount: 3
- skippedCount: 2
- stopReason: NETWORK_DOWN
- networkHealthStatus: CIRCUIT_OPEN

Next sync will retry surveys 6-10 only (1-5 already SYNCED)
```

### Retry Logic

**Survey-Level Retry** (from UploadSurveyUseCase):

```kotlin
onError = { error ->
    when {
        error.isRetryable -> {
            // Retryable error - increment and mark FAILED
            repository.incrementSurveyRetryCount(survey.surveyId)
            repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
        }
        else -> {
            // Non-retryable - permanent failure
            repository.markSurveyAsPermanentlyFailed(survey.surveyId, maxRetries)
        }
    }
}
```

**Retry Behavior**:

| Error Type         | isRetryable | Survey Status | Retry? | Max Retries |
|--------------------|-------------|---------------|--------|-------------|
| Network timeout    | ✓ Yes       | FAILED        | ✓ Yes  | 3           |
| Server error (5xx) | ✓ Yes       | FAILED        | ✓ Yes  | 3           |
| Validation (400)   | ✗ No        | FAILED        | ✗ No   | Permanent   |
| Auth error (401)   | ✗ No        | FAILED        | ✗ No   | Permanent   |

**Permanent Failure**:

```kotlin
// Sets retryCount = maxRetries to exclude from future syncs
repository.markSurveyAsPermanentlyFailed(surveyId, maxRetries = 3)
```

### Device-Aware Optimizations

**Media Skip Logic** (Lines 297-322):

```kotlin
private fun shouldSkipMedia(
    networkStatus: NetworkStatus?,
    deviceResources: DeviceResources?
): Boolean {
    // Skip on weak networks
    if (networkStatus == NetworkStatus.Weak) return true

    if (deviceResources == null) return false

    // Skip on cellular (metered data)
    if (!deviceResources.network.isGoodForMediaUpload) return true

    // Skip if battery < 20% and not charging
    if (!deviceResources.battery.isGoodForSync) return true

    return false
}
```

**Decision Matrix**:

| Network  | Battery           | Storage | Action                                    |
|----------|-------------------|---------|-------------------------------------------|
| WiFi     | 50%, Charging     | 1 GB    | ✓ Upload survey + media                   |
| WiFi     | 15%, Not charging | 1 GB    | ✓ Upload survey, ✗ skip media             |
| Cellular | 50%               | 1 GB    | ✓ Upload survey, ✗ skip media (save data) |
| WiFi     | 50%               | 150 MB  | ✗ STOP sync (critical storage)            |
| Weak     | 5%                | 50 MB   | ✗ STOP sync (all constraints failed)      |

**Stop Reasons**:

```kotlin
enum class StopReason {
    COMPLETED,            // All surveys processed
    NETWORK_DOWN,         // Circuit breaker opened
    STORAGE_CRITICAL,     // < 200 MB available
    BATTERY_LOW,          // Battery too low
    NETWORK_UNAVAILABLE   // Network lost during sync
}
```

---

## 2. UploadSurveyUseCase

**File Path**: `domain/src/main/java/com/survey/sync/engine/domain/usecase/UploadSurveyUseCase.kt`

### Overview

UploadSurveyUseCase handles the upload of a single survey, including its text answers and optional
media attachments. It manages state transitions, error handling, and coordinates with the repository
layer.

### Step-by-Step Upload Process

```
┌──────────────────────────────────────────────────────────────┐
│ START: UploadSurveyUseCase.invoke()                          │
│ Input: Survey, MediaAttachments, cleanupAttachments, skipMedia│
└─────────────────────────┬────────────────────────────────────┘
                          │
                          ▼
         ┌────────────────────────────────────┐
         │ [STEP 1] Update Status to SYNCING │
         │ repository.updateSyncStatus(       │
         │   surveyId, SyncStatus.SYNCING)    │
         └────────────────┬───────────────────┘
                          │
                          ▼
         ┌────────────────────────────────────┐
         │ [STEP 2] Upload Survey Data        │
         │ (text answers only, no photos)     │
         │ repository.uploadSurvey(survey)    │
         └────────────────┬───────────────────┘
                          │
                          ▼
              ┌───────────┴───────────┐
              │ SUCCESS?              │
              └───────┬───────────┬───┘
                      │           │
                   NO │           │ YES
                      │           │
                      ▼           ▼
         ┌────────────────────┐  ┌────────────────────────┐
         │ [ERROR HANDLER]    │  │ [STEP 4] Upload Media  │
         └────────────────────┘  └────────────────────────┘
                  │                         │
                  │                         ▼
                  │              ┌──────────┴──────────┐
                  │              │ skipMedia = true?   │
                  │              └──────────┬──────────┘
                  │                         │
                  │               ┌─────────┼─────────┐
                  │               │ YES     │ NO      │
                  │               ▼         ▼
                  │      ┌────────────┐  ┌──────────────────┐
                  │      │ Skip media │  │ attachments.     │
                  │      │ upload     │  │ isEmpty()?       │
                  │      └────────────┘  └─────┬────────────┘
                  │                            │
                  │                   ┌────────┼────────┐
                  │                   │ YES    │ NO     │
                  │                   ▼        ▼
                  │          ┌────────────┐ ┌──────────────┐
                  │          │ No media   │ │ Upload each  │
                  │          │ to upload  │ │ attachment   │
                  │          └────────────┘ │ Track counts │
                  │                         └──────┬───────┘
                  │                                │
                  │              ┌─────────────────┘
                  │              │
                  │              ▼
                  │   ┌──────────────────────────┐
                  │   │ [STEP 5] Mark as SYNCED  │
                  │   │ repository.updateSyncStatus│
                  │   │   (surveyId, SYNCED)     │
                  │   └──────────┬───────────────┘
                  │              │
                  │              ▼
                  │   ┌──────────────────────────┐
                  │   │ [STEP 6] Cleanup Media?  │
                  │   │ if cleanupAttachments && │
                  │   │    mediaSuccessCount > 0 │
                  │   └──────────┬───────────────┘
                  │              │
                  │              ▼
                  │   ┌──────────────────────────┐
                  │   │ repository.              │
                  │   │ cleanupSyncedAttachments │
                  │   │ (delete uploaded photos) │
                  │   └──────────┬───────────────┘
                  │              │
                  │              ▼
                  │   ┌──────────────────────────┐
                  │   │ [STEP 7] Return Result   │
                  │   │ UploadSurveyResult(...)  │
                  │   └──────────────────────────┘
                  │
                  ▼
    ┌─────────────────────────────────────┐
    │ [ERROR HANDLER]                     │
    ├─────────────────────────────────────┤
    │ Check: error.isRetryable?           │
    └──────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       │ YES           │ NO
       ▼               ▼
┌──────────────┐  ┌────────────────────┐
│ Retryable    │  │ Non-Retryable      │
│ Error        │  │ Error              │
├──────────────┤  ├────────────────────┤
│ - Increment  │  │ - Mark permanently │
│   retryCount │  │   failed           │
│ - Mark FAILED│  │ - Set retryCount = │
│ - Will retry │  │   maxRetries       │
│              │  │ - Won't retry      │
└──────────────┘  └────────────────────┘
       │                     │
       └──────────┬──────────┘
                  │
                  ▼
         ┌────────────────┐
         │ Return Error   │
         └────────────────┘
```

### Repository Interactions

**1. Update Status to SYNCING** (Line 56):

```kotlin
repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCING)
```

**2. Upload Survey Data** (Line 59):

```kotlin
val surveyUploadResult = repository.uploadSurvey(survey)
```

**3a. On Retryable Error** (Lines 68-69):

```kotlin
repository.incrementSurveyRetryCount(survey.surveyId)  // retryCount++
repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
```

**3b. On Non-Retryable Error** (Line 76):

```kotlin
repository.markSurveyAsPermanentlyFailed(survey.surveyId, maxRetries)
```

**4. On Success - Mark Synced** (Line 106):

```kotlin
repository.updateSyncStatus(survey.surveyId, SyncStatus.SYNCED)
```

**5. Cleanup Attachments** (Line 110):

```kotlin
repository.cleanupSyncedAttachments(survey.surveyId)  // Delete local files
```

### Error Handling

**Two Error Categories**:

**Retryable Errors** (transient issues):

- Network failures (timeout, connection lost)
- Server errors (500, 502, 503)
- HTTP 408 (Request Timeout)
- HTTP 429 (Rate Limit)

**Non-Retryable Errors** (permanent issues):

- Validation errors (400 Bad Request)
- Authentication errors (401 Unauthorized)
- Authorization errors (403 Forbidden)
- Business logic errors

**Code** (Lines 63-78):

```kotlin
onError = { error ->
    when {
        error.isRetryable -> {
            // Retryable error (network, timeout, server error)
            // Increment retry count and mark as FAILED
            // Survey will be retried on next sync if retryCount < maxRetries
            repository.incrementSurveyRetryCount(survey.surveyId)
            repository.updateSyncStatus(survey.surveyId, SyncStatus.FAILED)
        }
        else -> {
            // Non-retryable error (validation, authentication, business logic)
            // Mark as permanently FAILED - sets retryCount = maxRetries
            // This prevents any future retry attempts
            repository.markSurveyAsPermanentlyFailed(survey.surveyId, maxRetries)
        }
    }
    DomainResult.error(error)
}
```

### State Transitions

```
Initial State: PENDING
        │
        │ [invoke() called]
        ▼
    SYNCING ──────┐
        │         │
        │         │ [Upload error - retryable]
        │         ▼
        │     FAILED ◄────────┐
        │         │           │
        │         │ [Next sync attempt]
        │         └───────────┘
        │         │
        │         │ [Max retries exceeded]
        │         ▼
        │     FAILED (permanent, retryCount = maxRetries)
        │
        │ [Upload success]
        ▼
    SYNCED (final state)
```

### Example Scenarios

#### Scenario 1: Full Success

```
Input:
- Survey: survey-001 (3 text answers)
- Media: 3 photos (2 MB each)
- Network: WiFi
- Battery: 60%, charging

Execution:
Step 1: Status PENDING → SYNCING
Step 2: Upload survey data → SUCCESS
Step 3: Upload photo 1 → SUCCESS (2 MB)
Step 4: Upload photo 2 → SUCCESS (2 MB)
Step 5: Upload photo 3 → SUCCESS (2 MB)
Step 6: Status SYNCING → SYNCED
Step 7: Delete 3 local photo files
Step 8: Return result

Output:
UploadSurveyResult(
    surveyUploadResult = UploadResult(surveyId="survey-001", message="Success"),
    mediaUploadSuccessCount = 3,
    mediaUploadFailureCount = 0,
    mediaSkippedCount = 0,
    totalMediaCount = 3
)
```

#### Scenario 2: Partial Media Failure

```
Input:
- Survey: survey-002 (5 text answers)
- Media: 3 photos
- Network: WiFi (intermittent)
- Battery: 40%

Execution:
Step 1: Status PENDING → SYNCING
Step 2: Upload survey data → SUCCESS
Step 3: Upload photo 1 → SUCCESS
Step 4: Upload photo 2 → FAILED (network timeout)
Step 5: Upload photo 3 → SUCCESS
Step 6: Status SYNCING → SYNCED (survey still marked synced!)
Step 7: Delete 2 successfully uploaded photos
Step 8: Photo 2 remains PENDING for future retry
Step 9: Return result

Output:
UploadSurveyResult(
    surveyUploadResult = UploadResult(surveyId="survey-002", message="Success"),
    mediaUploadSuccessCount = 2,
    mediaUploadFailureCount = 1,
    mediaSkippedCount = 0,
    totalMediaCount = 3
)

Important: Survey is SYNCED, but photo 2 is still PENDING
```

#### Scenario 3: Network Error (Retryable)

```
Input:
- Survey: survey-003
- Media: 1 photo
- Network: WiFi → Suddenly disconnected
- Current retryCount: 1

Execution:
Step 1: Status PENDING → SYNCING
Step 2: Upload survey data → NETWORK ERROR (timeout)
Step 3: Error Handler:
    - error.isRetryable = true
    - Increment retryCount (1 → 2)
    - Update status to FAILED
Step 4: Return error

Output:
DomainResult.error(
    DomainError.NetworkFailure("Connection timeout")
)

Survey Status: FAILED
Retry Count: 2
Next Action: Will retry on next sync (2 < maxRetries=3)
```

#### Scenario 4: Validation Error (Non-Retryable)

```
Input:
- Survey: survey-004 (invalid farmer ID)
- Media: 0 photos
- Network: WiFi

Execution:
Step 1: Status PENDING → SYNCING
Step 2: Upload survey data → VALIDATION ERROR (400 Bad Request)
Step 3: Error Handler:
    - error.isRetryable = false
    - Mark permanently failed
    - Set retryCount = 3 (maxRetries)
    - Update status to FAILED
Step 4: Return error

Output:
DomainResult.error(
    DomainError.ApiError(httpCode=400, message="Invalid farmer ID")
)

Survey Status: FAILED (permanent)
Retry Count: 3 (maxRetries)
Next Action: Will NOT retry (excluded from future syncs)
```

---

## 3. GetMediaAttachmentsUseCase

**File Path**:
`domain/src/main/java/com/survey/sync/engine/domain/usecase/GetMediaAttachmentsUseCase.kt`

### Overview

GetMediaAttachmentsUseCase retrieves all media attachments (photos) associated with a specific
survey. It uses a simple delegation pattern, forwarding the request directly to the repository
layer.

### Implementation

```kotlin
class GetMediaAttachmentsUseCase @Inject constructor(
    private val repository: SurveyRepository
) {
    suspend operator fun invoke(
        surveyId: String
    ): DomainResult<DomainError, List<MediaAttachment>> {
        return repository.getMediaAttachments(surveyId)
    }
}
```

**Underlying Database Query**:

```sql
SELECT * FROM media_attachments
WHERE parentSurveyId = :surveyId
ORDER BY attachmentId
```

### Parameters and Return Types

**Input Parameters**:

- `surveyId: String` - The unique identifier (UUID) of the survey

**Return Type**:

```kotlin
DomainResult<DomainError, List<MediaAttachment>>
```

**Success Case**:

```kotlin
DomainResult.success(
    listOf(
        MediaAttachment(
            attachmentId = "att-uuid-1",
            surveyId = "survey-uuid",
            answerUuid = "answer-uuid-1",
            localFilePath = "/storage/emulated/0/SurveySyncEngine/photos/photo_20240301_143022.jpg",
            fileSize = 2048576L,  // 2 MB
            uploadedAt = null,     // Not uploaded yet
            syncStatus = SyncStatus.PENDING
        ),
        MediaAttachment(
            attachmentId = "att-uuid-2",
            surveyId = "survey-uuid",
            answerUuid = "answer-uuid-2",
            localFilePath = "/storage/emulated/0/SurveySyncEngine/photos/photo_20240301_143145.jpg",
            fileSize = 1835008L,  // 1.75 MB
            uploadedAt = Date(1709298705000),  // Uploaded timestamp
            syncStatus = SyncStatus.SYNCED
        )
    )
)
```

**Error Case**:

```kotlin
DomainResult.error(
    DomainError.DaoError("Failed to retrieve media attachments")
)
```

### Use Cases

#### Use Case 1: Before Upload (in BatchSyncUseCase)

**Purpose**: Retrieve media attachments to upload with survey

**Code** (BatchSyncUseCase Line 228):

```kotlin
// Get media attachments for this survey
val attachments = getMediaAttachmentsUseCase(survey.surveyId)
    .getOrNull() ?: emptyList()

// Upload survey with attachments
val uploadResult = uploadSurveyUseCase(
    survey = survey,
    mediaAttachments = attachments,
    cleanupAttachments = true,
    skipMedia = shouldSkipMedia(networkStatus, deviceResources)
)
```

**Example**:

```kotlin
Survey ID : "survey-001"
Attachments Retrieved :
-att - 1: /storage/photo1.jpg (PENDING, 2 MB)
-att - 2: /storage/photo2.jpg (PENDING, 1.5 MB)
-att - 3: /storage/photo3.jpg (SYNCED, 1.8 MB)

Upload Decision :
-Upload att -1 and att - 2(PENDING status)
-Skip att -3(already SYNCED)
```

#### Use Case 2: Display Survey Details (in UI)

**Purpose**: Show user which photos are attached to a survey

**Code**:

```kotlin
// ViewModel
fun loadSurveyDetails(surveyId: String) {
    viewModelScope.launch {
        val attachments = getMediaAttachmentsUseCase(surveyId)
            .getOrNull() ?: emptyList()

        _uiState.update { state ->
            state.copy(
                mediaAttachments = attachments.map { attachment ->
                    MediaAttachmentUiModel(
                        id = attachment.attachmentId,
                        filePath = attachment.localFilePath,
                        size = formatFileSize(attachment.fileSize),
                        status = attachment.syncStatus.toUiStatus(),
                        uploadedAt = attachment.uploadedAt?.formatDateTime()
                    )
                }
            )
        }
    }
}
```

**UI Display**:

```
Survey: Farm Visit - Farmer John Doe
Photos:
  📸 photo_20240301_143022.jpg (2.0 MB) - ⏳ Pending upload
  📸 photo_20240301_143145.jpg (1.8 MB) - ✓ Uploaded on Mar 1, 2024 2:31 PM
  📸 photo_20240301_143200.jpg (1.5 MB) - ⏳ Pending upload
```

#### Use Case 3: Storage Management

**Purpose**: Calculate total storage used by survey media

**Code**:

```kotlin
suspend fun calculateStorageForSurvey(surveyId: String): Long {
    val attachments = getMediaAttachmentsUseCase(surveyId).getOrNull()
    return attachments?.sumOf { it.fileSize } ?: 0L
}

// Calculate across all surveys
suspend fun calculateTotalMediaStorage(): Long {
    val allSurveys = getAllSurveysUseCase().getOrNull() ?: return 0L
    return allSurveys.sumOf { survey ->
        calculateStorageForSurvey(survey.surveyId)
    }
}
```

**Example Output**:

```
Survey survey-001: 5.3 MB (3 photos)
Survey survey-002: 8.7 MB (5 photos)
Survey survey-003: 2.1 MB (1 photo)
─────────────────────────────────────
Total Media Storage: 16.1 MB
```

#### Use Case 4: Retry Failed Media

**Purpose**: Identify and retry failed media uploads

**Code**:

```kotlin
suspend fun retryFailedMediaForSurvey(surveyId: String) {
    val attachments = getMediaAttachmentsUseCase(surveyId).getOrNull()

    val pendingMedia = attachments?.filter {
        it.syncStatus == SyncStatus.PENDING
    } ?: emptyList()

    if (pendingMedia.isNotEmpty()) {
        uploadMediaAttachmentsUseCase(
            surveyId = surveyId,
            attachments = pendingMedia
        )
    }
}
```

**Scenario**:

```
Survey survey-002 has 5 photos:
  - photo1.jpg: SYNCED
  - photo2.jpg: SYNCED
  - photo3.jpg: PENDING (failed on previous sync)
  - photo4.jpg: SYNCED
  - photo5.jpg: PENDING (not yet attempted)

Retry Action:
  → Upload only photo3.jpg and photo5.jpg
  → Skip photo1.jpg, photo2.jpg, photo4.jpg (already SYNCED)
```

#### Use Case 5: Pre-Upload Validation

**Purpose**: Verify all media files exist before upload

**Code**:

```kotlin
suspend fun validateMediaBeforeUpload(surveyId: String): ValidationResult {
    val attachments = getMediaAttachmentsUseCase(surveyId).getOrNull()
        ?: return ValidationResult.Error("Failed to retrieve attachments")

    val missingFiles = attachments.filter { attachment ->
        !File(attachment.localFilePath).exists()
    }

    return if (missingFiles.isEmpty()) {
        ValidationResult.Success
    } else {
        ValidationResult.MissingFiles(
            count = missingFiles.size,
            files = missingFiles.map { it.localFilePath }
        )
    }
}
```

**Example Output**:

```
Validation for survey-003:
  ✓ photo1.jpg exists (2.1 MB)
  ✗ photo2.jpg MISSING (was deleted by user)
  ✓ photo3.jpg exists (1.8 MB)

Validation Result: FAILED
Missing files: 1
Action: Skip photo2.jpg, upload photo1.jpg and photo3.jpg
```

---

## Summary

These three use cases form the core synchronization logic of SurveySyncEngine:

1. **BatchSyncUseCase**: Orchestrates batch syncing with circuit breaker pattern, partial failure
   handling, and device-aware optimizations
2. **UploadSurveyUseCase**: Handles individual survey uploads with intelligent retry logic and state
   management
3. **GetMediaAttachmentsUseCase**: Provides media retrieval for uploads, UI display, storage
   management, and validation

Together, they enable reliable, battery-efficient survey synchronization in challenging field
conditions with poor network connectivity and limited device resources.
