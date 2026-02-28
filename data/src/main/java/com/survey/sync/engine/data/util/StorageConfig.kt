package com.survey.sync.engine.data.util

/**
 * Configuration constants for storage cleanup operations.
 * Defines retention policies and cleanup thresholds for different scenarios.
 */
object StorageConfig {

    /**
     * Age thresholds for scheduled (routine) cleanup.
     * Conservative approach - only delete very old synced attachments.
     */
    const val SCHEDULED_CLEANUP_AGE_DAYS = 30

    /**
     * Age threshold for low storage cleanup.
     * Moderate approach - delete older data to free up space.
     */
    const val LOW_STORAGE_CLEANUP_AGE_DAYS = 14

    /**
     * Age threshold for almost full storage cleanup.
     * Aggressive approach - delete data older than a week.
     */
    const val ALMOST_FULL_CLEANUP_AGE_DAYS = 7

    /**
     * Progressive cleanup stages for critical storage (< 200 MB).
     * Stage 1: Delete attachments older than 7 days.
     * Stage 2: If still critical, delete attachments older than 3 days.
     * Stage 3: If still critical, delete attachments older than 1 day.
     */
    const val CRITICAL_STAGE_1_DAYS = 7
    const val CRITICAL_STAGE_2_DAYS = 3
    const val CRITICAL_STAGE_3_DAYS = 1

    /**
     * Worker scheduling configuration.
     * Daily cleanup runs every 24 hours with a 4-hour flex window.
     */
    const val WORKER_REPEAT_INTERVAL_HOURS = 24L
    const val WORKER_FLEX_INTERVAL_HOURS = 4L

    /**
     * Preferred time for cleanup (2 AM).
     * Runs during typical low-usage hours to minimize impact.
     */
    const val DAILY_CLEANUP_HOUR = 2

    /**
     * Estimated space freed per attachment (bytes).
     * Based on compressed image size (~300 KB average).
     */
    const val ESTIMATED_BYTES_PER_ATTACHMENT = 300L * 1024 // 300 KB

    /**
     * Batch size for cleanup operations.
     * Process attachments in batches to avoid memory issues.
     */
    const val CLEANUP_BATCH_SIZE = 100

    /**
     * Worker tags for monitoring and cancellation.
     */
    const val WORKER_TAG_STORAGE_CLEANUP = "STORAGE_CLEANUP"
    const val WORKER_TAG_MAINTENANCE = "MAINTENANCE"

    /**
     * Worker unique name for identifying the work.
     */
    const val WORKER_NAME_STORAGE_CLEANUP = "storage_cleanup_worker"
}
