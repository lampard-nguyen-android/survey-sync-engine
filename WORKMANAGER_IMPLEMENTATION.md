# WorkManager Implementation Documentation

This document provides comprehensive documentation of SurveySyncEngine's background work
architecture using Android's WorkManager. It covers periodic sync scheduling, manual sync triggers,
worker implementations with retry strategies, and storage cleanup automation.

---

## Table of Contents

1. [SyncWorkManager - Background Sync Scheduler](#1-syncworkmanager)
2. [SurveySyncWorker - Survey Synchronization Worker](#2-surveysyncworker)
3. [StorageCleanupWorker - Media Cleanup Worker](#3-storagecleanupworker)

---

## 1. SyncWorkManager

**File Path**: `app/src/main/java/com/survey/sync/engine/work/SyncWorkManager.kt`

### Overview

SyncWorkManager is responsible for scheduling both periodic and one-time background sync operations.
It configures WorkManager with appropriate constraints, backoff policies, and ensures no duplicate
sync jobs run concurrently.

### Periodic Sync Scheduling

**Configuration** (Lines 54-79):

```kotlin
fun schedulePeriodicSync() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)  // Any network
        .setRequiresBatteryNotLow(true)  // Don't drain low battery
        .build()

    val periodicSyncRequest = PeriodicWorkRequestBuilder<SurveySyncWorker>(
        repeatInterval = 4,        // Every 4 hours
        repeatIntervalTimeUnit = TimeUnit.HOURS,
        flexTimeInterval = 30,     // Can run ±30 minutes
        flexTimeIntervalUnit = TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30,  // Initial backoff: 30 seconds
            TimeUnit.SECONDS
        )
        .addTag(SurveySyncWorker.TAG_SYNC)          // "sync"
        .addTag(SurveySyncWorker.TAG_PERIODIC)      // "periodic_sync"
        .build()

    workManager.enqueueUniquePeriodicWork(
        SurveySyncWorker.WORKER_NAME,               // "survey_sync_worker"
        ExistingPeriodicWorkPolicy.KEEP,            // Prevent duplicates
        periodicSyncRequest
    )
}
```

**Periodic Sync Timeline**:

```
Day 1
─────────────────────────────────────────────────────────────
00:00                04:00                08:00         12:00
  │                   │                     │              │
  │                   ▼                     │              │
  │              First Sync                 │              │
  │          (±30 min flex)                 │              │
  │         Can run 3:30-4:30               │              │
  │                                         ▼              │
  │                                    Second Sync         │
  │                                   (±30 min flex)       │
  │                                  Can run 7:30-8:30     │
  │                                                        ▼
  └───────────────────────────────────────────────────Third Sync
                                                       (±30 min flex)
                                                     Can run 11:30-12:30

Benefits of Flex Window (±30 minutes):
  ✓ Allows OS to batch work for battery efficiency
  ✓ Avoids exact-time wake-ups that drain battery
  ✓ Can align with other app work for better resource usage
```

**Key Features**:

1. **Interval**: 4 hours (optimized for field work - not too frequent, not too rare)
2. **Flex Window**: ±30 minutes (battery-efficient scheduling)
3. **Constraints**:
    - Network must be CONNECTED (WiFi or Cellular)
    - Battery must NOT be low (system-defined threshold ~15%)
4. **Backoff Policy**: Exponential with 30-second initial delay
5. **Tags**: `sync`, `periodic_sync` for monitoring/cancellation
6. **Uniqueness**: `KEEP` policy prevents duplicate schedules

### One-Time Sync (Manual Trigger)

**Configuration** (Lines 144-164):

```kotlin
override suspend fun triggerImmediateSync() {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()  // No battery constraint for user-initiated sync

    val oneTimeSyncRequest = OneTimeWorkRequestBuilder<SurveySyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30,
            TimeUnit.SECONDS
        )
        .addTag(SurveySyncWorker.TAG_SYNC)
        .addTag(SurveySyncWorker.TAG_MANUAL)  // "manual_sync"
        .build()

    workManager.enqueueUniqueWork(
        "${SurveySyncWorker.WORKER_NAME}_manual",  // "survey_sync_worker_manual"
        ExistingWorkPolicy.KEEP,  // Ignore if already running
        oneTimeSyncRequest
    )
}
```

**User-Initiated Sync Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│ User Action: Taps "Sync Now" button                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │ App calls:                 │
        │ syncWorkManager.           │
        │   triggerImmediateSync()   │
        └────────────┬───────────────┘
                     │
                     ▼
        ┌────────────────────────────┐
        │ WorkManager checks:        │
        │ Is "survey_sync_worker_    │
        │      manual" running?      │
        └─────┬──────────────────┬───┘
              │                  │
        YES   │                  │ NO
              │                  │
              ▼                  ▼
    ┌─────────────────┐   ┌────────────────┐
    │ KEEP policy:    │   │ Enqueue work:  │
    │ Ignore new      │   │ Add to queue   │
    │ request         │   └────────┬───────┘
    │                 │            │
    │ User sees:      │            ▼
    │ "Sync already   │   ┌────────────────┐
    │  in progress"   │   │ Wait for       │
    └─────────────────┘   │ network        │
                          │ constraint     │
                          └────────┬───────┘
                                   │
                                   ▼
                          ┌────────────────┐
                          │ Execute        │
                          │ SurveySyncWorker│
                          │ .doWork()      │
                          └────────────────┘
```

**Differences from Periodic Sync**:

| Feature            | Periodic Sync                     | Manual Sync                 |
|--------------------|-----------------------------------|-----------------------------|
| Trigger            | Automatic (every 4 hours)         | User button tap             |
| Request Type       | `PeriodicWorkRequest`             | `OneTimeWorkRequest`        |
| Worker Name        | `survey_sync_worker`              | `survey_sync_worker_manual` |
| Battery Constraint | Yes (don't run if low)            | No (user wants it now)      |
| Flex Window        | ±30 minutes                       | None (ASAP)                 |
| Tags               | `sync`, `periodic_sync`           | `sync`, `manual_sync`       |
| Policy             | `ExistingPeriodicWorkPolicy.KEEP` | `ExistingWorkPolicy.KEEP`   |

### Backoff Policy

**Exponential Backoff Progression**:

```
Worker fails → Returns Result.retry()
    │
    ▼
Attempt 1:  Run immediately
    │  (FAIL)
    ▼
Attempt 2:  Wait 30 seconds        (2^0 × 30s = 30s)
    │  (FAIL)
    ▼
Attempt 3:  Wait 60 seconds        (2^1 × 30s = 60s)
    │  (FAIL)
    ▼
Attempt 4:  Wait 120 seconds       (2^2 × 30s = 120s)
    │  (FAIL)
    ▼
Attempt 5:  Wait 240 seconds       (2^3 × 30s = 240s)
    │  (FAIL)
    ▼
Max Delay:  300 seconds (5 min)    ← WorkManager default cap

Formula: delay = min(initialDelay × 2^(attemptCount-1), 300s)
```

**Code**:

```kotlin
.setBackoffCriteria(
    BackoffPolicy.EXPONENTIAL,
    30,  // initialDelaySeconds
    TimeUnit.SECONDS
)
```

**Why Exponential?**

- Reduces server load during outages (spacing out retries)
- Gives network time to recover
- More battery-efficient than fixed intervals
- Prevents "thundering herd" problem

### Duplicate Prevention

**Scenario: User Taps "Sync Now" Multiple Times**

```
Time    Event                    WorkManager State
──────────────────────────────────────────────────────────────
0:00    User tap #1             → Enqueue "survey_sync_worker_manual"
                                → State: ENQUEUED

0:01    Worker starts           → State: RUNNING
                                → Network check, get surveys...

0:05    User tap #2 (impatient) → Check existing work
                                → Found: "survey_sync_worker_manual" (RUNNING)
                                → Policy: KEEP
                                → Action: IGNORE new request
                                → User notification: "Sync in progress"

0:10    User tap #3             → Check existing work
                                → Found: "survey_sync_worker_manual" (RUNNING)
                                → Policy: KEEP
                                → Action: IGNORE new request

0:15    Sync completes          → State: SUCCEEDED
                                → Work finished, removed from queue

0:20    User tap #4             → Check existing work
                                → NOT FOUND (previous work finished)
                                → Action: Enqueue new work
                                → State: ENQUEUED → RUNNING
```

**KEEP Policy Benefits**:

- ✓ Prevents data corruption from concurrent syncs
- ✓ Saves battery (no duplicate work)
- ✓ Reduces server load
- ✓ Simpler state management

**Alternative Policies (NOT used)**:

| Policy              | Behavior                               | Why NOT Used                                      |
|---------------------|----------------------------------------|---------------------------------------------------|
| `REPLACE`           | Cancel running work, start new one     | **Bad**: Could corrupt mid-sync data              |
| `APPEND`            | Chain new work after existing          | **Unnecessary**: Periodic work handles scheduling |
| `APPEND_OR_REPLACE` | Append if RUNNING, replace if ENQUEUED | **Overkill**: KEEP is simpler                     |

### Monitoring Sync Status

**Check if Sync is Running** (Lines 171-175):

```kotlin
override suspend fun isSyncRunning(): Boolean {
    val workInfos = workManager
        .getWorkInfosByTag(SurveySyncWorker.TAG_SYNC)
        .await()

    return workInfos.any { it.state == WorkInfo.State.RUNNING }
}
```

**Observe Sync Progress** (Lines 181-187):

```kotlin
override fun observeSyncStatus(): Flow<WorkInfo?> {
    return workManager
        .getWorkInfosForUniqueWorkFlow(SurveySyncWorker.WORKER_NAME)
        .map { workInfos ->
            workInfos.firstOrNull()
        }
}
```

**UI Integration Example**:

```kotlin
// ViewModel
viewModelScope.launch {
    syncWorkManager.observeSyncStatus().collect { workInfo ->
        when (workInfo?.state) {
            WorkInfo.State.RUNNING -> {
                _uiState.update {
                    it.copy(
                        syncStatus = "Syncing...",
                        isSyncing = true
                    )
                }
            }
            WorkInfo.State.SUCCEEDED -> {
                val successCount = workInfo.outputData
                    .getInt(KEY_SUCCESS_COUNT, 0)
                _uiState.update {
                    it.copy(
                        syncStatus = "Synced $successCount surveys",
                        isSyncing = false
                    )
                }
            }
            WorkInfo.State.FAILED -> {
                val error = workInfo.outputData
                    .getString(KEY_ERROR_MESSAGE)
                _uiState.update {
                    it.copy(
                        syncStatus = "Sync failed: $error",
                        isSyncing = false
                    )
                }
            }
            else -> {
                // ENQUEUED, BLOCKED, CANCELLED
            }
        }
    }
}
```

---

## 2. SurveySyncWorker

**File Path**: `app/src/main/java/com/survey/sync/engine/work/SurveySyncWorker.kt`

### Overview

SurveySyncWorker is the background worker that executes survey synchronization. It integrates with
`BatchSyncUseCase`, monitors device resources, implements circuit breaker pattern, and makes
intelligent retry decisions.

### doWork() Implementation

**Complete Flow** (Lines 40-159):

```
┌──────────────────────────────────────────────────────────────┐
│ SurveySyncWorker.doWork() START                             │
└────────────────────┬─────────────────────────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────┐
    │ [1] Check Network Status           │
    │ val networkStatus =                │
    │   connectivityManager              │
    │     .networkStatusFlow.value       │
    └────────────────┬───────────────────┘
                     │
                     ▼
         ┌───────────┴───────────┐
         │ Network Unavailable?  │
         └───┬───────────────┬───┘
             │ YES           │ NO
             │               │
             ▼               ▼
    ┌────────────────┐  ┌──────────────────────┐
    │ Return:        │  │ [2] Get Device       │
    │ Result.retry() │  │ Resources            │
    │                │  │ - Battery level      │
    │ Will retry when│  │ - Storage available  │
    │ network returns│  │ - Network type       │
    └────────────────┘  └──────────┬───────────┘
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │ [3] Create           │
                        │ NetworkHealthTracker │
                        │ (circuit breaker)    │
                        │ threshold = 3        │
                        └──────────┬───────────┘
                                   │
                                   ▼
                        ┌──────────────────────┐
                        │ [4] Execute          │
                        │ BatchSyncUseCase     │
                        └──────────┬───────────┘
                                   │
                                   ▼
                    ┌──────────────┴──────────────┐
                    │ BatchSync Result            │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │ SUCCESS                     │ ERROR
                    ▼                             ▼
    ┌─────────────────────────────┐    ┌───────────────────┐
    │ [5] Analyze Result          │    │ [6] Check Retry   │
    │ Check stopReason:           │    │ Attempts          │
    │                             │    │                   │
    │ - NETWORK_DOWN?             │    │ runAttemptCount < │
    │   → retry()                 │    │ MAX_RETRY (3)?    │
    │                             │    └────┬──────────┬───┘
    │ - STORAGE_CRITICAL?         │         │ YES      │ NO
    │   → retry()                 │         │          │
    │                             │         ▼          ▼
    │ - BATTERY_LOW?              │    ┌──────────┐ ┌─────────┐
    │   → retry()                 │    │ retry()  │ │failure()│
    │                             │    └──────────┘ └─────────┘
    │ - COMPLETED?                │
    │   → success(outputData)     │
    │     with detailed metrics   │
    └─────────────────────────────┘
                 │
                 ▼
    ┌─────────────────────────────┐
    │ [7] Create Output Data      │
    │ - Total surveys             │
    │ - Success/failure/skip counts│
    │ - Survey IDs (succeeded/    │
    │   failed/skipped)           │
    │ - Stop reason               │
    │ - Network health status     │
    │ - Timestamp                 │
    └─────────────────────────────┘
                 │
                 ▼
    ┌─────────────────────────────┐
    │ Return Result.success()     │
    │ (or retry/failure based on  │
    │  stop reason)               │
    └─────────────────────────────┘
```

**Code** (Simplified):

```kotlin
override suspend fun doWork(): Result {
    return try {
        // [1] Network check
        val networkStatus = connectivityManager.networkStatusFlow.value
        if (networkStatus == NetworkStatus.Unavailable) {
            return Result.retry()
        }

        // [2] Device resources
        val deviceResources = deviceResourceManager.currentResources

        // [3] Circuit breaker
        val networkHealthTracker = NetworkHealthTracker(
            consecutiveFailureThreshold = 3
        )

        // [4] Execute batch sync
        val syncResult = batchSyncUseCase(
            networkHealthTracker,
            networkStatus,
            deviceResources
        )

        // [5] Handle result
        syncResult.handle(
            onSuccess = { batchResult ->
                // Create detailed output data
                val outputData = createOutputData(batchResult)

                // Decision based on stop reason
                when (batchResult.stopReason) {
                    StopReason.NETWORK_DOWN -> Result.retry()
                    StopReason.STORAGE_CRITICAL -> Result.retry()
                    StopReason.BATTERY_LOW -> Result.retry()
                    else -> Result.success(outputData)
                }
            },
            onError = { error ->
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.failure(errorData)
                }
            }
        )
    } catch (e: Exception) {
        if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure(errorData)
        }
    }
}
```

### Retry Approach Decision Matrix

**When to Return `Result.retry()`**:

| Scenario                         | Condition                           | Reason                | Max Retries             |
|----------------------------------|-------------------------------------|-----------------------|-------------------------|
| **Network unavailable at start** | `networkStatus == Unavailable`      | Wait for connectivity | Infinite (with backoff) |
| **Circuit breaker opened**       | `stopReason == NETWORK_DOWN`        | Network likely down   | Infinite (with backoff) |
| **Storage critical**             | `stopReason == STORAGE_CRITICAL`    | Wait for cleanup      | Infinite (with backoff) |
| **Battery low**                  | `stopReason == BATTERY_LOW`         | Wait for charging     | Infinite (with backoff) |
| **Network lost mid-sync**        | `stopReason == NETWORK_UNAVAILABLE` | Network disappeared   | Infinite (with backoff) |
| **Complete failure**             | Exception thrown                    | Transient error       | 3 attempts              |

**When to Return `Result.failure()`**:

| Scenario                   | Condition                            | Reason                         |
|----------------------------|--------------------------------------|--------------------------------|
| **Max retries exceeded**   | `runAttemptCount >= 3`               | Permanent failure, stop trying |
| **Unexpected fatal error** | Unhandled exception after 3 attempts | Can't recover                  |

**When to Return `Result.success()`**:

| Scenario            | Condition                 | Note                          |
|---------------------|---------------------------|-------------------------------|
| **Sync completed**  | `stopReason == COMPLETED` | All surveys processed         |
| **Partial success** | Some surveys failed       | Failed surveys remain PENDING |

**Important**: `Result.success()` does NOT mean all surveys succeeded. It means the sync operation
completed without critical infrastructure errors.

**Code** (Lines 87-125):

```kotlin
when (batchResult.stopReason) {
    BatchSyncUseCase.StopReason.NETWORK_DOWN -> {
        Timber.w("Network likely down, will retry later")
        Result.retry()
    }
    BatchSyncUseCase.StopReason.NETWORK_UNAVAILABLE -> {
        Timber.w("Network unavailable during sync, will retry")
        Result.retry()
    }
    BatchSyncUseCase.StopReason.STORAGE_CRITICAL -> {
        Timber.w("Storage critical, will retry after cleanup")
        Result.retry()
    }
    BatchSyncUseCase.StopReason.BATTERY_LOW -> {
        Timber.w("Battery too low, will retry later")
        Result.retry()
    }
    else -> {
        // COMPLETED or partial success
        // Failed surveys remain PENDING for next sync
        Timber.i(
            "Sync completed - Success: ${batchResult.successCount}, " +
                    "Failed: ${batchResult.failureCount}"
        )
        Result.success(outputData)
    }
}
```

### Device Resource Checks

**Getting Device Resources** (Lines 51-58):

```kotlin
val deviceResources = deviceResourceManager.currentResources

Timber.d(
    "SurveySyncWorker: Starting sync - " +
            "Network: $networkStatus (${deviceResources.network}), " +
            "Battery: ${deviceResources.battery.level}% " +
            "${if (deviceResources.battery.isCharging) "(charging)" else ""}, " +
            "Storage: ${deviceResources.storage.availableMB} MB free"
)
```

**Device Resources Structure**:

```kotlin
data class DeviceResources(
    val battery: BatteryStatus,
    val storage: StorageStatus,
    val network: NetworkType
)

data class BatteryStatus(
    val level: Int,          // 0-100%
    val isCharging: Boolean,
    val temperature: Float?
) {
    // Good for sync if charging OR battery >= 20%
    val isGoodForSync: Boolean = isCharging || level >= 20
}

data class StorageStatus(
    val availableBytes: Long,
    val totalBytes: Long
) {
    val availableMB: Long = availableBytes / (1024 * 1024)
    val isCritical: Boolean = availableBytes < 200_000_000  // 200 MB
    val hasEnoughSpace: Boolean = availableBytes >= 500_000_000  // 500 MB
}

enum class NetworkType {
    WIFI,           // Unmetered, good for media
    CELLULAR,       // Metered, skip media
    WEAK,           // Available but slow
    UNAVAILABLE
}
```

**How Resources Affect Sync**:

```
Resource Checks Throughout Sync:
═══════════════════════════════════════════════════════════

BEFORE SYNC STARTS (in Worker):
┌────────────────────────────────────────┐
│ Network unavailable?                   │
│   → Return Result.retry()              │
│   → Won't even attempt sync            │
└────────────────────────────────────────┘

DURING SYNC (in BatchSyncUseCase):
┌────────────────────────────────────────┐
│ BEFORE EACH SURVEY:                    │
│                                        │
│ Storage < 200 MB (critical)?           │
│   → STOP sync immediately              │
│   → StopReason: STORAGE_CRITICAL       │
│                                        │
│ Circuit breaker open?                  │
│   → STOP sync immediately              │
│   → StopReason: NETWORK_DOWN           │
└────────────────────────────────────────┘

MEDIA UPLOAD DECISION (in BatchSyncUseCase):
┌────────────────────────────────────────┐
│ Should skip media if:                  │
│                                        │
│ • Network is WEAK                      │
│ • Network is CELLULAR (metered data)   │
│ • Battery < 20% AND not charging       │
│                                        │
│ If skipped:                            │
│   → Upload survey text only            │
│   → Media remains PENDING for later    │
└────────────────────────────────────────┘
```

**Example Scenarios**:

**Scenario 1: Ideal Conditions**

```
Resources:
  Network: WiFi
  Battery: 70%, charging
  Storage: 2.5 GB free

Sync Behavior:
  ✓ Start sync
  ✓ Upload survey data
  ✓ Upload media (WiFi + good battery)
  ✓ Process all pending surveys
  Result: Full sync with media
```

**Scenario 2: Low Battery, WiFi**

```
Resources:
  Network: WiFi
  Battery: 15%, not charging
  Storage: 1.2 GB free

Sync Behavior:
  ✓ Start sync
  ✓ Upload survey data
  ✗ Skip media (battery too low)
  ✓ Process all pending surveys
  Result: Text-only sync, media deferred
```

**Scenario 3: Cellular Network**

```
Resources:
  Network: Cellular (4G)
  Battery: 60%
  Storage: 800 MB free

Sync Behavior:
  ✓ Start sync
  ✓ Upload survey data
  ✗ Skip media (cellular = metered data)
  ✓ Process all pending surveys
  Result: Text-only sync to save data costs
```

**Scenario 4: Critical Storage**

```
Resources:
  Network: WiFi
  Battery: 80%, charging
  Storage: 150 MB free (CRITICAL!)

Sync Behavior:
  ✓ Start sync
  ✓ Upload survey 1 → SUCCESS
  ✓ Upload survey 2 → SUCCESS
  ✗ STOP sync (storage critical check before survey 3)
  Result: Partial sync
  Return: Result.retry() (will resume after cleanup)
```

### Circuit Breaker Integration

**Creation and Usage**:

```kotlin
// [1] Create tracker (Line 60-63)
val networkHealthTracker = NetworkHealthTracker(
    consecutiveFailureThreshold = CIRCUIT_BREAKER_THRESHOLD  // = 3
)

// [2] Pass to BatchSyncUseCase (Line 66)
val syncResult = batchSyncUseCase(
    networkHealthTracker = networkHealthTracker,
    networkStatus = networkStatus,
    deviceResources = deviceResources
)

// [3] Inside BatchSyncUseCase - Per Survey:
for (survey in pendingSurveys) {
    // Check before each upload
    if (!networkHealthTracker.shouldContinueSync) {
        // Circuit opened - stop sync
        return BatchSyncResult(stopReason = NETWORK_DOWN)
    }

    // Upload survey
    val result = uploadSurvey(survey)

    // Record result
    if (result.isSuccess) {
        networkHealthTracker.recordSuccess()  // Reset counter
    } else if (isNetworkError(result.error)) {
        networkHealthTracker.recordFailure(result.error)  // Increment
    }
}

// [4] Back in Worker - Handle result (Lines 86-87)
val outputData = workDataOf(
    KEY_NETWORK_HEALTH to batchResult.networkHealthStatus.name
)
```

**Circuit Breaker State Machine**:

```
┌─────────────────────────────────────────────────────────────┐
│ State: HEALTHY                                              │
│ Consecutive Failures: 0-1                                   │
│ shouldContinueSync: true                                    │
│ Action: Continue uploading surveys                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ 2nd network failure
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ State: DEGRADED                                             │
│ Consecutive Failures: 2                                     │
│ shouldContinueSync: true (still allowing sync)              │
│ Action: Continue but close to threshold                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ 3rd network failure
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ State: CIRCUIT_OPEN                                         │
│ Consecutive Failures: 3+                                    │
│ shouldContinueSync: FALSE                                   │
│ Action: STOP sync immediately to conserve battery/data      │
└─────────────────────────────────────────────────────────────┘
                       │
                       │ Success resets state
                       ▼
                 ┌─────────────┐
                 │ Back to     │
                 │ HEALTHY     │
                 │ (failures=0)│
                 └─────────────┘
```

**Example Worker Execution**:

```
Sync starts with 10 pending surveys:

Survey 1: Upload → SUCCESS
  networkHealthTracker.recordSuccess()
  failures = 0, state = HEALTHY

Survey 2: Upload → SUCCESS
  networkHealthTracker.recordSuccess()
  failures = 0, state = HEALTHY

Survey 3: Upload → NETWORK ERROR (timeout)
  networkHealthTracker.recordFailure(error)
  failures = 1, state = HEALTHY

Survey 4: Upload → NETWORK ERROR (timeout)
  networkHealthTracker.recordFailure(error)
  failures = 2, state = DEGRADED

Survey 5: Upload → NETWORK ERROR (timeout)
  networkHealthTracker.recordFailure(error)
  failures = 3, state = CIRCUIT_OPEN
  shouldContinueSync = FALSE

Surveys 6-10: SKIPPED (circuit breaker opened)

BatchSyncResult:
  stopReason = NETWORK_DOWN
  networkHealthStatus = CIRCUIT_OPEN
  successCount = 2
  failureCount = 3
  skippedCount = 5

Worker Returns: Result.retry()
  → WorkManager will retry with exponential backoff
  → Surveys 3-10 remain PENDING for next attempt
```

### Output Data Structure

**Complete Output** (Lines 79-91):

```kotlin
val outputData = workDataOf(
    KEY_TOTAL_SURVEYS to batchResult.totalSurveys,
    KEY_SUCCESS_COUNT to batchResult.successCount,
    KEY_FAILURE_COUNT to batchResult.failureCount,
    KEY_SKIPPED_COUNT to batchResult.skippedCount,
    KEY_STOP_REASON to batchResult.stopReason.name,
    KEY_NETWORK_HEALTH to batchResult.networkHealthStatus.name,
    KEY_SUCCEEDED_IDS to batchResult.succeededSurveyIds.joinToString(","),
    KEY_FAILED_IDS to batchResult.failedSurveyIds.joinToString(","),
    KEY_SKIPPED_IDS to batchResult.skippedSurveyIds.joinToString(","),
    KEY_SYNC_TIMESTAMP to System.currentTimeMillis()
)
```

**Example Output Data**:

```kotlin
WorkData {
    "total_surveys" = 10
    "success_count" = 5
    "failure_count" = 2
    "skipped_count" = 3
    "stop_reason" = "NETWORK_DOWN"
    "network_health" = "CIRCUIT_OPEN"
    "succeeded_ids" = "uuid-1,uuid-2,uuid-3,uuid-4,uuid-5"
    "failed_ids" = "uuid-6,uuid-7"
    "skipped_ids" = "uuid-8,uuid-9,uuid-10"
    "sync_timestamp" = 1709298000000
}
```

**UI Consumption**:

```kotlin
// Observe work status
syncWorkManager.observeSyncStatus().collect { workInfo ->
    if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
        val data = workInfo.outputData

        showSyncSummary(
            total = data.getInt(KEY_TOTAL_SURVEYS, 0),
            success = data.getInt(KEY_SUCCESS_COUNT, 0),
            failed = data.getInt(KEY_FAILURE_COUNT, 0),
            skipped = data.getInt(KEY_SKIPPED_COUNT, 0),
            stopReason = data.getString(KEY_STOP_REASON),
            networkHealth = data.getString(KEY_NETWORK_HEALTH)
        )
    }
}
```

---

## 3. StorageCleanupWorker

**File Path**: `app/src/main/java/com/survey/sync/engine/work/StorageCleanupWorker.kt`

### Overview

StorageCleanupWorker runs daily to proactively clean up old synced media attachments (photos) from
local storage. It uses a tiered cleanup strategy based on storage availability and implements FIFO (
First-In-First-Out) deletion.

### What Gets Cleaned Up

**Target: Old SYNCED Media Attachments**

```
┌─────────────────────────────────────────────────────────────┐
│ Cleanup Targets (what gets DELETED):                       │
├─────────────────────────────────────────────────────────────┤
│ ✓ Photo files with syncStatus = SYNCED                     │
│ ✓ Uploaded more than X days ago (based on severity)        │
│ ✓ Both local file AND database record deleted              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ Preserved (what's KEPT):                                    │
├─────────────────────────────────────────────────────────────┤
│ ✗ Media with syncStatus = PENDING (not yet uploaded)       │
│ ✗ Recently synced media (< threshold age)                  │
│ ✗ Survey data (text answers) - NEVER deleted               │
│ ✗ Survey metadata - NEVER deleted                          │
└─────────────────────────────────────────────────────────────┘
```

**FIFO Deletion Strategy**:

```
Attachments Sorted by uploadedAt (oldest first):
═══════════════════════════════════════════════════

ID      Uploaded    Age   Status   Size    Action (30-day threshold)
─────────────────────────────────────────────────────────────────────
att-1   Jan 1      60d   SYNCED   2.1 MB  ✓ DELETE (oldest, past threshold)
att-2   Jan 15     45d   SYNCED   1.8 MB  ✓ DELETE (old, past threshold)
att-3   Feb 1      30d   SYNCED   2.5 MB  ✓ DELETE (exactly at threshold)
att-4   Feb 10     20d   SYNCED   1.9 MB  ✗ Keep (too recent)
att-5   Feb 15     15d   PENDING  2.0 MB  ✗ Keep (not uploaded yet!)
att-6   Feb 25     5d    SYNCED   1.7 MB  ✗ Keep (too recent)
att-7   Mar 1      1d    SYNCED   2.2 MB  ✗ Keep (very recent)

Result: Delete att-1, att-2, att-3 → Free 6.4 MB
```

### Scheduling

**Daily Cleanup Configuration** (from SyncWorkManager Lines 99-119):

```kotlin
val periodicCleanupRequest = PeriodicWorkRequestBuilder<StorageCleanupWorker>(
    repeatInterval = StorageConfig.WORKER_REPEAT_INTERVAL_HOURS,  // 24 hours
    repeatIntervalTimeUnit = TimeUnit.HOURS,
    flexTimeInterval = StorageConfig.WORKER_FLEX_INTERVAL_HOURS,  // 6 hours
    flexTimeIntervalUnit = TimeUnit.HOURS
)
    .setConstraints(constraints)
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        1,  // 1 hour initial backoff (longer than sync worker)
        TimeUnit.HOURS
    )
    .addTag(StorageCleanupWorker.TAG_CLEANUP)
    .build()
```

**Execution Timeline**:

```
Weekly Schedule (24-hour intervals with ±6-hour flex):
═══════════════════════════════════════════════════════

Monday    Tuesday   Wednesday Thursday  Friday    Saturday  Sunday
──────────────────────────────────────────────────────────────────
02:00 AM  02:00 AM  02:00 AM  02:00 AM  02:00 AM  02:00 AM  02:00 AM
  ▼         ▼         ▼         ▼         ▼         ▼         ▼
Cleanup   Cleanup   Cleanup   Cleanup   Cleanup   Cleanup   Cleanup
(±6 hrs)  (±6 hrs)  (±6 hrs)  (±6 hrs)  (±6 hrs)  (±6 hrs)  (±6 hrs)

Can run:  Can run:  Can run:  Can run:  Can run:  Can run:  Can run:
8 PM Mon  8 PM Tue  8 PM Wed  8 PM Thu  8 PM Fri  8 PM Sat  8 PM Sun
to        to        to        to        to        to        to
8 AM Tue  8 AM Wed  8 AM Thu  8 AM Fri  8 AM Sat  8 AM Sun  8 AM Mon

Why 2 AM?
  ✓ Device likely idle (user asleep)
  ✓ Charging overnight (battery not an issue)
  ✓ Less likely to interfere with user activity
  ✓ WiFi often available at home
```

**Constraints**:

```kotlin
val constraints = Constraints.Builder()
    .setRequiresBatteryNotLow(true)  // Don't drain battery
    .setRequiresDeviceIdle(false)    // Can run anytime (not just idle)
    .build()
```

### Multi-Tier Cleanup Strategy

**Three Cleanup Tiers Based on Storage Severity**:

```
┌─────────────────────────────────────────────────────────────┐
│ TIER 1: SCHEDULED MAINTENANCE (Normal Storage)             │
├─────────────────────────────────────────────────────────────┤
│ Trigger:  • Daily schedule OR                              │
│           • Storage > 85% full                              │
│ Threshold: 30 days old                                      │
│ Target:   SYNCED attachments uploaded > 30 days ago         │
│ Goal:     Preventive maintenance, avoid getting full        │
│ Aggressiveness: ★☆☆☆☆ (Conservative)                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ TIER 2: LOW STORAGE WARNING                                │
├─────────────────────────────────────────────────────────────┤
│ Trigger:  Storage < 500 MB available                        │
│ Threshold: 14 days old                                      │
│ Target:   SYNCED attachments uploaded > 14 days ago         │
│ Goal:     Free up space before critical level               │
│ Aggressiveness: ★★★☆☆ (Moderate)                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ TIER 3: CRITICAL STORAGE EMERGENCY                         │
├─────────────────────────────────────────────────────────────┤
│ Trigger:  Storage < 200 MB available                        │
│ Threshold: 0 days (ALL synced attachments)                  │
│ Target:   ALL SYNCED attachments (any age)                  │
│ Goal:     Emergency cleanup to prevent app/device failure   │
│ Aggressiveness: ★★★★★ (Aggressive)                         │
└─────────────────────────────────────────────────────────────┘
```

**Decision Logic** (inside StorageManagementUseCase):

```kotlin
val deleteAge = when {
    storage.isCritical -> {
        // Emergency: Delete all synced attachments
        0  // days
    }
    storage.availableMB < 500 -> {
        // Warning: More aggressive cleanup
        14  // days
    }
    else -> {
        // Normal: Conservative cleanup
        30  // days
    }
}

val thresholdDate = Date(
    System.currentTimeMillis() - (deleteAge * 24 * 60 * 60 * 1000L)
)

// Delete synced attachments older than threshold
val oldAttachments = repository.getOldestSyncedAttachments(
    limit = 50,  // Batch size for performance
    daysOld = deleteAge
)
```

**Example Scenarios**:

**Scenario 1: Normal Maintenance**

```
Storage Status:
  Available: 2.5 GB (plenty)
  Total: 16 GB
  Used: 84%

Cleanup Decision:
  Tier: 1 (Scheduled Maintenance)
  Threshold: 30 days
  Query: SYNCED attachments uploaded before Feb 1, 2024

Attachments Found:
  - 8 photos uploaded in January (total 12 MB)

Action:
  ✓ Delete 8 photos
  ✓ Free 12 MB

Result:
  Success: 8 deleted
  Freed: 12 MB
  New Available: 2.512 GB
```

**Scenario 2: Low Storage Warning**

```
Storage Status:
  Available: 350 MB (low!)
  Total: 16 GB
  Used: 97.8%

Cleanup Decision:
  Tier: 2 (Low Storage)
  Threshold: 14 days
  Query: SYNCED attachments uploaded before Feb 17, 2024

Attachments Found:
  - 25 photos uploaded in Jan-mid Feb (total 45 MB)

Action:
  ✓ Delete 25 photos
  ✓ Free 45 MB

Result:
  Success: 25 deleted
  Freed: 45 MB
  New Available: 395 MB
```

**Scenario 3: Critical Storage Emergency**

```
Storage Status:
  Available: 150 MB (CRITICAL!)
  Total: 16 GB
  Used: 99.1%

Cleanup Decision:
  Tier: 3 (Emergency)
  Threshold: 0 days (ALL synced)
  Query: ALL SYNCED attachments

Attachments Found:
  - 120 photos (all synced, any age) (total 180 MB)

Action:
  ✓ Delete ALL 120 synced photos
  ✓ Free 180 MB

Result:
  Success: 120 deleted
  Freed: 180 MB
  New Available: 330 MB

Note: Preserves PENDING attachments (not yet uploaded)
```

### Success/Failure Criteria

**Success Cases**:

**Case 1: Cleanup Performed** (Lines 96-100):

```kotlin
if (result.deletedCount > 0) {
    Timber.i(result.getFormattedSummary())
    Result.success(outputData)
}
```

**Case 2: No Cleanup Needed** (Lines 98-104):

```kotlin
else {
    Timber.d("StorageCleanupWorker: No cleanup needed - Storage OK")
    Result.success(outputData)  // Still success!
}
```

**Output Data for Success**:

```kotlin
val outputData = workDataOf(
    KEY_DELETED_COUNT to result.deletedCount,
    KEY_FREED_MB to result.freedMB,
    KEY_CLEANUP_REASON to result.reason.name,
    KEY_BEFORE_AVAILABLE_MB to storageBeforeMB,
    KEY_AFTER_AVAILABLE_MB to storageAfterMB,
    KEY_EXECUTION_TIME_MS to executionTimeMs,
    KEY_TIMESTAMP to System.currentTimeMillis(),
    KEY_SUCCESS to true
)
```

**Failure Cases**:

**Case 1: Retry (Attempt < 2)** (Lines 118-120):

```kotlin
if (runAttemptCount < MAX_RETRY_ATTEMPTS) {  // MAX = 2
    Timber.w("Retrying cleanup (attempt ${runAttemptCount + 1}/2)")
    Result.retry()
}
```

**Case 2: Permanent Failure** (Lines 121-124):

```kotlin
else {
    Timber.e("Max retries exceeded for cleanup, marking as failed")
    Result.failure(errorData)
}
```

**Error Output Data**:

```kotlin
val errorData = workDataOf(
    KEY_ERROR_MESSAGE to error.errorMessage,
    KEY_TIMESTAMP to System.currentTimeMillis(),
    KEY_SUCCESS to false
)
```

**Key Points**:

1. ✓ Returns `success()` even if 0 files deleted (normal when storage is fine)
2. ✓ Returns `success()` for partial deletions (some files failed to delete)
3. ✗ Returns `retry()` if database error or exception thrown (< 2 attempts)
4. ✗ Returns `failure()` only after max retries exceeded

**Example Execution Results**:

**Success - Files Deleted**:

```
WorkData {
    "deleted_count" = 15
    "freed_mb" = 28
    "cleanup_reason" = "SCHEDULED_MAINTENANCE"
    "before_available_mb" = 1200
    "after_available_mb" = 1228
    "execution_time_ms" = 3420
    "timestamp" = 1709298000000
    "success" = true
}
Result: Result.success(outputData)
```

**Success - Nothing to Delete**:

```
WorkData {
    "deleted_count" = 0
    "freed_mb" = 0
    "cleanup_reason" = "SCHEDULED_MAINTENANCE"
    "before_available_mb" = 2500
    "after_available_mb" = 2500
    "execution_time_ms" = 245
    "timestamp" = 1709298000000
    "success" = true
}
Result: Result.success(outputData)
```

**Retry - Transient Error**:

```
Attempt 1:
  Error: "Database locked"
  runAttemptCount = 0
  Result: Result.retry()
  → Wait 1 hour (backoff)

Attempt 2:
  Success: Deleted 10 files
  Result: Result.success(outputData)
```

**Failure - Permanent**:

```
Attempt 1: Database error → retry()
Attempt 2: Database error → retry()
Attempt 3: Max retries exceeded → failure()

WorkData {
    "error_message" = "Database connection failed after 2 retries"
    "timestamp" = 1709298000000
    "success" = false
}
Result: Result.failure(errorData)
```

---

## Summary

SurveySyncEngine's WorkManager implementation provides robust background synchronization with
intelligent resource management:

1. **SyncWorkManager**: Schedules periodic (4-hour) and manual sync with duplicate prevention and
   exponential backoff
2. **SurveySyncWorker**: Executes sync with circuit breaker pattern, device resource checks, and
   smart retry decisions
3. **StorageCleanupWorker**: Daily cleanup of old media with tiered strategy based on storage
   availability

This architecture ensures reliable survey synchronization in challenging field conditions while
conserving battery, data, and storage resources.
