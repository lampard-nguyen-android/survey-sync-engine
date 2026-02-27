package com.survey.sync.engine.domain.sync

/**
 * Interface for scheduling and managing survey sync operations.
 *
 * This abstraction allows the presentation layer to trigger syncs
 * without depending on WorkManager implementation details.
 *
 * Addresses Scenario 4: Concurrent Sync Prevention
 * - Implementations must ensure only one sync runs at a time
 * - Duplicate sync requests should be safely ignored
 */
interface SyncScheduler {

    /**
     * Trigger an immediate one-time sync.
     * If a sync is already running, this request should be ignored.
     */
    suspend fun triggerImmediateSync()

    /**
     * Check if a sync operation is currently running.
     * Useful for UI to show appropriate feedback.
     *
     * @return true if sync is in progress, false otherwise
     */
    suspend fun isSyncRunning(): Boolean

    /**
     * Get the result of the last completed sync operation.
     *
     * @return SyncResult if available, null if no sync has completed yet
     */
    suspend fun getLastSyncResult(): SyncResult?

    /**
     * Cancel all running sync operations.
     * Use with caution - only for user-initiated cancellation.
     */
    suspend fun cancelAllSync()

    /**
     * Result data from a sync operation.
     */
    data class SyncResult(
        val totalSurveys: Int,
        val successCount: Int,
        val failureCount: Int,
        val timestamp: Long,
        val errorMessage: String?
    )
}
