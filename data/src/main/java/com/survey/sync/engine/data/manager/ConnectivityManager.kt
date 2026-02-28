package com.survey.sync.engine.data.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.survey.sync.engine.domain.model.NetworkType
import com.survey.sync.engine.domain.network.NetworkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        /**
         * 2G GSM ~14.4 Kbps
         * G GPRS ~26.8 Kbps
         * E EDGE ~108.8 Kbps
         * 3G UMTS ~128 Kbps
         * H HSPA ~3.6 Mbps
         * H+ HSPA+ ~14.4 Mbps-23.0 Mbps
         * 4G LTE ~50 Mbps
         * 4G LTE-A ~500 Mbps
         */
        const val WEAK_NETWORK_BANDWIDTH_KBPS_THRESHOLD = 14400
    }

    private var networkCallback: NetworkCallback? = null
    private var connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatusFlow = MutableStateFlow<NetworkStatus?>(null)
    val networkStatusFlow = _networkStatusFlow.asStateFlow()

    private val _networkTypeFlow = MutableStateFlow<NetworkType>(NetworkType.UNAVAILABLE)
    val networkTypeFlow = _networkTypeFlow.asStateFlow()

    fun startConnectivityListener() {
        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkStatusFlow.value = NetworkStatus.Available
                // Get initial network type when network becomes available
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.let { updateNetworkType(it) }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                // Update NetworkStatus based on bandwidth
                val isWeakNetwork =
                    (networkCapabilities.linkDownstreamBandwidthKbps <= WEAK_NETWORK_BANDWIDTH_KBPS_THRESHOLD)
                _networkStatusFlow.value =
                    if (isWeakNetwork) NetworkStatus.Weak else NetworkStatus.Available

                // Update NetworkType based on metered status and transport type
                updateNetworkType(networkCapabilities)
            }

            override fun onLost(network: Network) {
                _networkStatusFlow.value = NetworkStatus.Unavailable
                _networkTypeFlow.value = NetworkType.UNAVAILABLE
                Timber.d("ConnectivityManager: Network lost")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        }
    }

    /**
     * Update network type based on capabilities.
     * Determines if network is WiFi (unmetered) or Cellular (metered).
     */
    private fun updateNetworkType(capabilities: NetworkCapabilities) {
        val networkType = when {
            // Check bandwidth first - if weak, mark as WEAK regardless of type
            capabilities.linkDownstreamBandwidthKbps <= WEAK_NETWORK_BANDWIDTH_KBPS_THRESHOLD -> {
                NetworkType.WEAK
            }
            // Check if network is unmetered (WiFi, Ethernet, etc.)
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> {
                // Further check if it's specifically WiFi
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    NetworkType.WIFI
                } else {
                    // Unmetered but not WiFi (e.g., Ethernet) - treat as WiFi for sync purposes
                    NetworkType.WIFI
                }
            }
            // Metered network (cellular)
            else -> {
                NetworkType.CELLULAR
            }
        }

        // Only update and log if the type changed
        if (_networkTypeFlow.value != networkType) {
            _networkTypeFlow.value = networkType
            Timber.d("ConnectivityManager: Network type changed to $networkType")
        }
    }

    /**
     * Check if current network is WiFi (unmetered)
     */
    fun isWiFiAvailable(): Boolean {
        return _networkTypeFlow.value == NetworkType.WIFI
    }

    /**
     * Check if current network is metered (cellular)
     */
    fun isMeteredNetwork(): Boolean {
        return _networkTypeFlow.value == NetworkType.CELLULAR
    }

    /**
     * Get current network type synchronously
     */
    fun getCurrentNetworkType(): NetworkType {
        return _networkTypeFlow.value
    }
}
