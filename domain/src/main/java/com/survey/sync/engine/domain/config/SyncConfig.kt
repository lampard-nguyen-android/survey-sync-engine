package com.survey.sync.engine.domain.config

/**
 * Configuration constants for sync retry logic.
 * Provides centralized control over retry behavior across surveys, answers, and media attachments.
 */
object SyncConfig {

    /**
     * Maximum number of retry attempts for failed sync operations.
     * After this many failures, the entity will be marked as permanently FAILED.
     *
     * Applies to:
     * - Survey uploads
     * - Answer uploads
     * - Media attachment uploads
     */
    const val MAX_RETRY_COUNT = 3

    /**
     * Minimum delay between retry attempts in milliseconds.
     * Helps prevent overwhelming the server with rapid retry attempts.
     */
    const val MIN_RETRY_DELAY_MS = 5000L // 5 seconds
}
