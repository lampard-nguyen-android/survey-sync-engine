package com.survey.sync.engine.data.manager

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.survey.sync.engine.domain.network.NetworkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun startConnectivityListener() {
        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkStatusFlow.value = NetworkStatus.Available
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val isWeakNetwork =
                    (networkCapabilities.linkDownstreamBandwidthKbps <= WEAK_NETWORK_BANDWIDTH_KBPS_THRESHOLD)
                _networkStatusFlow.value =
                    if (isWeakNetwork) NetworkStatus.Weak else NetworkStatus.Available
            }

            override fun onLost(network: Network) {
                _networkStatusFlow.value = NetworkStatus.Unavailable
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
}
