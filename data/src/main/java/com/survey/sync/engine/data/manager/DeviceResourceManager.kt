package com.survey.sync.engine.data.manager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.content.getSystemService
import com.survey.sync.engine.domain.model.BatteryStatus
import com.survey.sync.engine.domain.model.DeviceResources
import com.survey.sync.engine.domain.model.NetworkType
import com.survey.sync.engine.domain.model.StorageStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for monitoring device resources (battery, storage, network).
 * Provides reactive streams for resource changes to enable device-aware sync decisions.
 *
 * Usage:
 * ```kotlin
 * val resources = deviceResourceManager.currentResources
 * if (resources.isGoodForFullSync) {
 *     // Upload everything including media
 * } else if (resources.canSyncTextOnly) {
 *     // Upload text data only, skip media
 * }
 * ```
 */
@Singleton
class DeviceResourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityManager: ConnectivityManager
) {

    private val _deviceResourcesFlow = MutableStateFlow<DeviceResources?>(null)

    /**
     * Current device resources state.
     * Emits updates when battery, storage, or network changes.
     */
    val deviceResourcesFlow: StateFlow<DeviceResources?> = _deviceResourcesFlow.asStateFlow()

    /**
     * Current device resources (synchronous access)
     */
    val currentResources: DeviceResources
        get() = DeviceResources(
            battery = getCurrentBatteryStatus(),
            storage = getCurrentStorageStatus(),
            network = getCurrentNetworkType()
        )

    /**
     * Start monitoring device resources.
     * Should be called from Application.onCreate()
     */
    fun startMonitoring() {
        Timber.d("DeviceResourceManager: Starting resource monitoring")

        // Initial update
        updateResources()

        // Monitor network changes via ConnectivityManager
        // (ConnectivityManager already has its own listener)

        Timber.i("DeviceResourceManager: Resource monitoring started")
    }

    /**
     * Update current resources and emit to flow
     */
    fun updateResources() {
        val resources = currentResources
        _deviceResourcesFlow.value = resources

        Timber.d(
            "DeviceResourceManager: Resources updated - " +
                    "Battery: ${resources.battery.level}% ${if (resources.battery.isCharging) "(charging)" else ""}, " +
                    "Storage: ${resources.storage.availableMB} MB free, " +
                    "Network: ${resources.network}"
        )
    }

    /**
     * Get current battery status
     */
    fun getCurrentBatteryStatus(): BatteryStatus {
        val batteryManager = context.getSystemService<BatteryManager>()

        val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

        val batteryStatus = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val temperature =
            batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)?.let { temp ->
                temp / 10f // Temperature is in tenths of degrees Celsius
            }

        return BatteryStatus(
            level = level,
            isCharging = isCharging,
            temperature = temperature
        )
    }

    /**
     * Get current storage status for app's storage directory
     */
    fun getCurrentStorageStatus(): StorageStatus {
        val storageDir = context.filesDir
        val statFs = StatFs(storageDir.absolutePath)

        val availableBytes = statFs.availableBytes
        val totalBytes = statFs.totalBytes

        return StorageStatus(
            availableBytes = availableBytes,
            totalBytes = totalBytes
        )
    }

    /**
     * Get current network type from ConnectivityManager
     */
    fun getCurrentNetworkType(): NetworkType {
        return connectivityManager.getCurrentNetworkType()
    }

    /**
     * Check if device has sufficient resources for full sync (including media)
     */
    fun hasResourcesForFullSync(): Boolean {
        return currentResources.isGoodForFullSync
    }

    /**
     * Check if device can at least sync text data (skip media)
     */
    fun canSyncTextOnly(): Boolean {
        return currentResources.canSyncTextOnly
    }

    /**
     * Get storage usage details
     */
    fun getStorageUsageDetails(): StorageUsageDetails {
        val storage = getCurrentStorageStatus()
        val usedBytes = storage.totalBytes - storage.availableBytes

        return StorageUsageDetails(
            totalBytes = storage.totalBytes,
            usedBytes = usedBytes,
            availableBytes = storage.availableBytes,
            usagePercentage = storage.usagePercentage,
            isAlmostFull = storage.isAlmostFull,
            isCritical = storage.isCritical
        )
    }

    /**
     * Detailed storage usage information
     */
    data class StorageUsageDetails(
        val totalBytes: Long,
        val usedBytes: Long,
        val availableBytes: Long,
        val usagePercentage: Float,
        val isAlmostFull: Boolean,
        val isCritical: Boolean
    ) {
        val totalMB: Long get() = totalBytes / (1024 * 1024)
        val usedMB: Long get() = usedBytes / (1024 * 1024)
        val availableMB: Long get() = availableBytes / (1024 * 1024)

        fun getFormattedSummary(): String {
            return "Storage: $usedMB MB / $totalMB MB used (${"%.1f".format(usagePercentage)}%) - $availableMB MB free"
        }
    }

    /**
     * Check if WiFi is available
     */
    fun isWiFiAvailable(): Boolean {
        return getCurrentNetworkType() == NetworkType.WIFI
    }

    /**
     * Check if battery is charging
     */
    fun isCharging(): Boolean {
        return getCurrentBatteryStatus().isCharging
    }

    /**
     * Get battery level percentage
     */
    fun getBatteryLevel(): Int {
        return getCurrentBatteryStatus().level
    }
}
