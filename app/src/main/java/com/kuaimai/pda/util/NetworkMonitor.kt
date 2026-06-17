package com.kuaimai.pda.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络状态监控器
 * 通过ConnectivityManager监听网络变化
 */
@Singleton
class NetworkMonitor @Inject constructor(
    private val context: Context
) {

    enum class Status {
        ONLINE, OFFLINE, WEAK
    }

    private val _networkStatus = MutableStateFlow(Status.OFFLINE)
    val networkStatus: StateFlow<Status> = _networkStatus.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _networkStatus.value = Status.ONLINE
        }

        override fun onLost(network: Network) {
            _networkStatus.value = Status.OFFLINE
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
            val validated = networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            )

            _networkStatus.value = when {
                validated -> Status.ONLINE
                hasInternet -> Status.WEAK
                else -> Status.OFFLINE
            }
        }
    }

    /**
     * 注册网络监听
     */
    fun register() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        // 初始状态检查
        checkCurrentStatus()
    }

    /**
     * 注销网络监听
     */
    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // 注销时可能未注册，忽略但记录日志
            Log.d("NetworkMonitor", "注销网络监听异常: ${e.message}")
        }
    }

    /**
     * 检查当前网络状态
     */
    private fun checkCurrentStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        _networkStatus.value = when {
            capabilities == null -> Status.OFFLINE
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> Status.ONLINE
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> Status.WEAK
            else -> Status.OFFLINE
        }
    }
}
