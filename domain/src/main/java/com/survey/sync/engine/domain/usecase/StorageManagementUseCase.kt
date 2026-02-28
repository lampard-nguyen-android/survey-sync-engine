package com.survey.sync.engine.domain.usecase

import com.survey.sync.engine.data.util.StorageConfig
import com.survey.sync.engine.domain.error.DomainError
import com.survey.sync.engine.domain.error.DomainResult
import com.survey.sync.engine.domain.model.StorageStatus
import com.survey.sync.engine.domain.repository.SurveyRepository
import javax.inject.Inject

/**
 * Use case for managing local storage and performing proactive cleanup.
 * Prevents devices from running out of storage when collecting 50+ surveys/day with photos.
 *
 * Cleanup Strategy (in order):
 * 1. Delete synced attachments older than 7 days
 * 2. If still low, delete synced attachments older than 3 days
 * 3. If still low, delete synced attachments older than 1 day
 * 4. NEVER delete PENDING (unsynced) attachments
 *
 * Usage:
 * ```kotlin
 * val result = storageManagementUseCase.cleanupIfNeeded(storageStatus)
 * result.onSuccess { cleanupResult ->
 *     println("Freed ${cleanupResult.freedBytes / (1024*1024)} MB")
 * }
 * ```
 */
class StorageManagementUseCase @Inject constructor(
    private val repository: SurveyRepository,
    private val cleanupOldAttachmentsUseCase: CleanupOldAttachmentsUseCase
) {

    /**
     * Result of cleanup operation
     */
    data class CleanupResult(
        val deletedCount: Int,
        val freedBytes: Long,
        val reason: CleanupReason
    ) {
        val freedMB: Long get() = freedBytes / (1024 * 1024)

        fun getFormattedSummary(): String {
            return "Cleanup: $deletedCount attachments deleted, ${freedMB} MB freed (Reason: $reason)"
        }
    }

    /**
     * Reason for cleanup
     */
    enum class CleanupReason {
        CRITICAL_STORAGE,    // < 200 MB free
        LOW_STORAGE,         // < 500 MB free
        ALMOST_FULL,         // > 85% usage
        SCHEDULED,           // Regular maintenance
        MANUAL              // User requested
    }

    /**
     * Check storage and perform cleanup if needed
     *
     * @param storageStatus Current storage status
     * @param force Force cleanup even if storage is OK
     * @return CleanupResult with details of what was cleaned
     */
    suspend operator fun invoke(
        storageStatus: StorageStatus,
        force: Boolean = false
    ): DomainResult<DomainError, CleanupResult> {
        return try {
            // Determine if cleanup is needed
            val (shouldCleanup, reason) = when {
                storageStatus.isCritical -> true to CleanupReason.CRITICAL_STORAGE
                storageStatus.hasEnoughSpace.not() -> true to CleanupReason.LOW_STORAGE
                storageStatus.isAlmostFull -> true to CleanupReason.ALMOST_FULL
                force -> true to CleanupReason.MANUAL
                else -> false to CleanupReason.SCHEDULED
            }

            if (!shouldCleanup) {
                println("StorageManagement: No cleanup needed (${storageStatus.availableMB} MB free)")
                return DomainResult.success(
                    CleanupResult(
                        deletedCount = 0,
                        freedBytes = 0,
                        reason = reason
                    )
                )
            }

            println(
                "StorageManagement: Cleanup triggered - " +
                        "Reason: $reason, Available: ${storageStatus.availableMB} MB"
            )

            // Perform progressive cleanup
            val cleanupResult = performProgressiveCleanup(storageStatus, reason)

            println(
                "StorageManagement: ${cleanupResult.getFormattedSummary()}"
            )

            DomainResult.success(cleanupResult)

        } catch (e: Exception) {
            println("StorageManagement: Error during cleanup ${e.message}")
            DomainResult.error(DomainError.InternalError(e))
        }
    }

    /**
     * Perform cleanup in stages until storage is acceptable.
     * Uses different age thresholds based on cleanup reason for optimized cleanup:
     * - SCHEDULED: 30 days (conservative, routine maintenance)
     * - LOW_STORAGE: 14 days (moderate, preventive cleanup)
     * - ALMOST_FULL: 7 days (aggressive, proactive cleanup)
     * - CRITICAL_STORAGE: 7→3→1 days (very aggressive, emergency cleanup)
     * - MANUAL: User-initiated (follows same logic as needed)
     */
    private suspend fun performProgressiveCleanup(
        initialStorage: StorageStatus,
        reason: CleanupReason
    ): CleanupResult {
        var totalDeleted = 0
        var totalFreed = 0L
        var currentStorage = initialStorage

        // Determine cleanup strategy based on reason
        when (reason) {
            CleanupReason.SCHEDULED -> {
                // Conservative: Only delete very old attachments during routine maintenance
                println("StorageManagement: Scheduled cleanup - Deleting attachments > ${StorageConfig.SCHEDULED_CLEANUP_AGE_DAYS} days old")
                val result =
                    cleanupOldAttachments(daysOld = StorageConfig.SCHEDULED_CLEANUP_AGE_DAYS)
                totalDeleted += result.deletedCount
                totalFreed += result.estimatedFreedBytes
            }

            CleanupReason.LOW_STORAGE -> {
                // Moderate: Delete older attachments to free up space
                println("StorageManagement: Low storage cleanup - Deleting attachments > ${StorageConfig.LOW_STORAGE_CLEANUP_AGE_DAYS} days old")
                val result =
                    cleanupOldAttachments(daysOld = StorageConfig.LOW_STORAGE_CLEANUP_AGE_DAYS)
                totalDeleted += result.deletedCount
                totalFreed += result.estimatedFreedBytes
            }

            CleanupReason.ALMOST_FULL -> {
                // Aggressive: Delete recent-ish attachments to prevent critical state
                println("StorageManagement: Almost full cleanup - Deleting attachments > ${StorageConfig.ALMOST_FULL_CLEANUP_AGE_DAYS} days old")
                val result =
                    cleanupOldAttachments(daysOld = StorageConfig.ALMOST_FULL_CLEANUP_AGE_DAYS)
                totalDeleted += result.deletedCount
                totalFreed += result.estimatedFreedBytes
            }

            CleanupReason.CRITICAL_STORAGE, CleanupReason.MANUAL -> {
                // Very aggressive: Progressive cleanup stages for critical situations
                // Stage 1: Delete attachments older than 7 days
                if (currentStorage.isCritical || !currentStorage.hasEnoughSpace) {
                    println("StorageManagement: Stage 1 - Deleting attachments > ${StorageConfig.CRITICAL_STAGE_1_DAYS} days old")
                    val stage1 =
                        cleanupOldAttachments(daysOld = StorageConfig.CRITICAL_STAGE_1_DAYS)
                    totalDeleted += stage1.deletedCount
                    totalFreed += stage1.estimatedFreedBytes

                    // Re-check storage (simulated - in production, re-query)
                    currentStorage = StorageStatus(
                        availableBytes = currentStorage.availableBytes + stage1.estimatedFreedBytes,
                        totalBytes = currentStorage.totalBytes
                    )
                }

                // Stage 2: Delete attachments older than 3 days (if still low)
                if (currentStorage.isCritical) {
                    println("StorageManagement: Stage 2 - Deleting attachments > ${StorageConfig.CRITICAL_STAGE_2_DAYS} days old")
                    val stage2 =
                        cleanupOldAttachments(daysOld = StorageConfig.CRITICAL_STAGE_2_DAYS)
                    totalDeleted += stage2.deletedCount
                    totalFreed += stage2.estimatedFreedBytes

                    currentStorage = StorageStatus(
                        availableBytes = currentStorage.availableBytes + stage2.estimatedFreedBytes,
                        totalBytes = currentStorage.totalBytes
                    )
                }

                // Stage 3: Delete attachments older than 1 day (emergency only)
                if (currentStorage.isCritical && currentStorage.availableBytes < StorageStatus.MINIMAL_STORAGE_BYTES) {
                    println("StorageManagement: Stage 3 (Emergency) - Deleting attachments > ${StorageConfig.CRITICAL_STAGE_3_DAYS} day old")
                    val stage3 =
                        cleanupOldAttachments(daysOld = StorageConfig.CRITICAL_STAGE_3_DAYS)
                    totalDeleted += stage3.deletedCount
                    totalFreed += stage3.estimatedFreedBytes
                }
            }
        }

        return CleanupResult(
            deletedCount = totalDeleted,
            freedBytes = totalFreed,
            reason = reason
        )
    }

    /**
     * Delete attachments older than specified days
     */
    private suspend fun cleanupOldAttachments(daysOld: Int): CleanupStageResult {
        val result = cleanupOldAttachmentsUseCase(daysOld = daysOld)

        return result.handle(
            onSuccess = { count ->
                // Estimate freed space (average 300 KB per attachment after compression)
                val estimatedBytes = count * 300L * 1024
                CleanupStageResult(
                    deletedCount = count,
                    estimatedFreedBytes = estimatedBytes
                )
            },
            onError = {
                println("StorageManagement: Cleanup stage failed for $daysOld days: $it")
                CleanupStageResult(deletedCount = 0, estimatedFreedBytes = 0)
            }
        )
    }

    /**
     * Internal result for cleanup stages
     */
    private data class CleanupStageResult(
        val deletedCount: Int,
        val estimatedFreedBytes: Long
    )

    /**
     * Check if storage check/cleanup should be performed before photo capture
     *
     * @param storageStatus Current storage status
     * @return true if capture should be allowed
     */
    fun canCapturePhoto(storageStatus: StorageStatus): Boolean {
        return when {
            storageStatus.isCritical -> {
                println("StorageManagement: Photo capture blocked - Critical storage (${storageStatus.availableMB} MB)")
                false
            }

            storageStatus.availableBytes < StorageStatus.MINIMAL_STORAGE_BYTES -> {
                println("StorageManagement: Photo capture blocked - Insufficient storage (${storageStatus.availableMB} MB)")
                false
            }

            else -> true
        }
    }

    /**
     * Get recommended action based on storage status
     */
    fun getRecommendedAction(storageStatus: StorageStatus): StorageAction {
        return when {
            storageStatus.isCritical -> StorageAction.CLEANUP_REQUIRED
            !storageStatus.hasEnoughSpace -> StorageAction.CLEANUP_RECOMMENDED
            storageStatus.isAlmostFull -> StorageAction.MONITOR
            else -> StorageAction.OK
        }
    }

    /**
     * Recommended storage action
     */
    enum class StorageAction(val message: String) {
        OK("Storage OK"),
        MONITOR("Storage getting full - monitor"),
        CLEANUP_RECOMMENDED("Low storage - cleanup recommended"),
        CLEANUP_REQUIRED("Critical storage - cleanup required")
    }
}
