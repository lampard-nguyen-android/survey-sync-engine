package com.survey.sync.engine.domain.network

import com.survey.sync.engine.domain.error.DomainError

/**
 * Tracks network health during sync operations using a circuit breaker pattern.
 * Helps detect when network is likely down vs experiencing transient errors.
 *
 * States:
 * - HEALTHY: Network functioning normally (0-1 consecutive failures)
 * - DEGRADED: Network experiencing issues (2 consecutive failures)
 * - CIRCUIT_OPEN: Network likely down (3+ consecutive failures) - stop sync to conserve battery/data
 */
class NetworkHealthTracker(
    private val consecutiveFailureThreshold: Int = 3
) {

    private var consecutiveNetworkFailures = 0
    private var consecutiveSuccesses = 0
    private var totalAttempts = 0
    private var totalNetworkFailures = 0

    /**
     * Current health status based on failure pattern
     */
    val healthStatus: HealthStatus
        get() = when {
            consecutiveNetworkFailures >= consecutiveFailureThreshold -> HealthStatus.CIRCUIT_OPEN
            consecutiveNetworkFailures >= 2 -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }

    /**
     * Whether sync should continue based on network health
     */
    val shouldContinueSync: Boolean
        get() = healthStatus != HealthStatus.CIRCUIT_OPEN

    /**
     * Record a successful upload
     * Resets consecutive failure counter
     */
    fun recordSuccess() {
        consecutiveSuccesses++
        consecutiveNetworkFailures = 0 // Reset on success
        totalAttempts++

        println("NetworkHealthTracker: Success recorded. Health status: $healthStatus")
    }

    /**
     * Record a failed upload
     * Only counts network-related failures (not validation errors, server errors, etc.)
     *
     * @param error The domain error that occurred
     * @return true if this is a network failure that counts toward circuit breaker
     */
    fun recordFailure(error: DomainError): Boolean {
        totalAttempts++

        val isNetworkFailure = isNetworkRelatedFailure(error)

        if (isNetworkFailure) {
            consecutiveNetworkFailures++
            totalNetworkFailures++
            consecutiveSuccesses = 0

            println(
                "NetworkHealthTracker: Network failure recorded. " +
                        "Consecutive failures: $consecutiveNetworkFailures, " +
                        "Health status: $healthStatus"
            )
        } else {
            // Non-network failures don't affect circuit breaker
            println("NetworkHealthTracker: Non-network failure recorded (${error.javaClass.simpleName})")
        }

        return isNetworkFailure
    }

    /**
     * Determines if an error is network-related (vs server error, validation error, etc.)
     */
    private fun isNetworkRelatedFailure(error: DomainError): Boolean {
        return when (error) {
            is DomainError.NetworkFailure -> {
                true
            }

            is DomainError.ApiError -> {
                // 408 (timeout) and 503 (service unavailable) may indicate network issues
                // Other 5xx errors are server problems, not network problems
                error.httpCode == 408 || error.httpCode == 503
            }

            else -> false
        }
    }

    /**
     * Reset all counters (useful for new sync session)
     */
    fun reset() {
        consecutiveNetworkFailures = 0
        consecutiveSuccesses = 0
        totalAttempts = 0
        totalNetworkFailures = 0

        println("NetworkHealthTracker: Reset to initial state")
    }

    /**
     * Get detailed statistics for logging/debugging
     */
    fun getStats(): String {
        return "NetworkHealthTracker Stats: " +
                "Health=$healthStatus, " +
                "ConsecutiveFailures=$consecutiveNetworkFailures, " +
                "ConsecutiveSuccesses=$consecutiveSuccesses, " +
                "TotalAttempts=$totalAttempts, " +
                "TotalNetworkFailures=$totalNetworkFailures"
    }
}

/**
 * Network health states based on failure patterns
 */
enum class HealthStatus {
    HEALTHY,        // 0-1 consecutive network failures
    DEGRADED,       // 2 consecutive network failures
    CIRCUIT_OPEN    // 3+ consecutive network failures - network likely down
}