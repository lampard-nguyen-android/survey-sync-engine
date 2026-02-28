package com.survey.sync.engine.domain.model

/**
 * Device resource status for sync decisions.
 * Encapsulates battery, storage, and network information.
 */
data class DeviceResources(
    val battery: BatteryStatus,
    val storage: StorageStatus,
    val network: NetworkType
) {
    /**
     * Whether device is in good condition for full sync (including media)
     */
    val isGoodForFullSync: Boolean
        get() = battery.isGoodForSync &&
                storage.hasEnoughSpace &&
                network.isGoodForMediaUpload

    /**
     * Whether device can sync text data (skip media)
     */
    val canSyncTextOnly: Boolean
        get() = battery.level > 10 && storage.hasMinimalSpace

    /**
     * Human-readable summary of constraints
     */
    val constraintSummary: String
        get() = buildList {
            if (!battery.isGoodForSync) {
                add("Low battery (${battery.level}%)")
            }
            if (!storage.hasEnoughSpace) {
                add("Low storage (${storage.availableMB} MB free)")
            }
            if (!network.isGoodForMediaUpload) {
                add("Poor network (${network.name})")
            }
        }.joinToString(", ")
}

/**
 * Battery status information
 */
data class BatteryStatus(
    val level: Int, // 0-100 percentage
    val isCharging: Boolean,
    val temperature: Float? = null // Celsius
) {
    /**
     * Whether battery level is sufficient for sync operations
     * - If charging: Always OK
     * - If not charging: Need at least 20%
     */
    val isGoodForSync: Boolean
        get() = isCharging || level >= MINIMUM_BATTERY_LEVEL

    /**
     * Whether battery is critically low
     */
    val isCritical: Boolean
        get() = level < CRITICAL_BATTERY_LEVEL && !isCharging

    companion object {
        const val MINIMUM_BATTERY_LEVEL = 20
        const val CRITICAL_BATTERY_LEVEL = 10
    }
}

/**
 * Storage status information
 */
data class StorageStatus(
    val availableBytes: Long,
    val totalBytes: Long
) {
    val availableMB: Long
        get() = availableBytes / (1024 * 1024)

    val totalMB: Long
        get() = totalBytes / (1024 * 1024)

    val usagePercentage: Float
        get() = ((totalBytes - availableBytes).toFloat() / totalBytes) * 100

    /**
     * Whether there's enough space for media uploads
     * Minimum: 500 MB free
     */
    val hasEnoughSpace: Boolean
        get() = availableBytes >= MINIMUM_STORAGE_BYTES

    /**
     * Whether storage is critically low
     * Critical: < 200 MB free
     */
    val isCritical: Boolean
        get() = availableBytes < CRITICAL_STORAGE_BYTES

    /**
     * Whether there's minimal space for text-only sync
     * Minimal: At least 100 MB free
     */
    val hasMinimalSpace: Boolean
        get() = availableBytes >= MINIMAL_STORAGE_BYTES

    /**
     * Whether storage is approaching full (> 85%)
     */
    val isAlmostFull: Boolean
        get() = usagePercentage > ALMOST_FULL_THRESHOLD

    companion object {
        const val MINIMUM_STORAGE_BYTES = 500L * 1024 * 1024 // 500 MB
        const val CRITICAL_STORAGE_BYTES = 200L * 1024 * 1024 // 200 MB
        const val MINIMAL_STORAGE_BYTES = 100L * 1024 * 1024 // 100 MB
        const val ALMOST_FULL_THRESHOLD = 85f // 85%
    }
}

/**
 * Network type categorization for sync decisions
 */
enum class NetworkType(val description: String) {
    /**
     * WiFi connection - unmetered, fast
     * Best for media uploads
     */
    WIFI("WiFi"),

    /**
     * Cellular connection - metered, variable speed
     * Use cautiously for media uploads
     */
    CELLULAR("Cellular"),

    /**
     * Weak connection - available but slow
     * Skip media uploads
     */
    WEAK("Weak"),

    /**
     * No network connection
     * Cannot sync
     */
    UNAVAILABLE("Unavailable");

    /**
     * Whether this network type is suitable for uploading media
     */
    val isGoodForMediaUpload: Boolean
        get() = this == WIFI

    /**
     * Whether this network type allows any sync (text at minimum)
     */
    val canSync: Boolean
        get() = this != UNAVAILABLE
}
