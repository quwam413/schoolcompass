package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _connectionLabel = MutableStateFlow(resolveLabel())
    val connectionLabel: StateFlow<String> = _connectionLabel.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshState()
        }

        override fun onLost(network: Network) {
            refreshState()
        }

        override fun onUnavailable() {
            _isOnline.value = false
            _connectionLabel.value = "Offline"
        }
    }

    init {
        runCatching {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, callback)
        }
    }

    fun refreshState() {
        _isOnline.value = checkCurrentlyOnline()
        _connectionLabel.value = resolveLabel()
    }

    private fun checkCurrentlyOnline(): Boolean = runCatching {
        val cm = connectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private fun resolveLabel(): String = runCatching {
        val cm = connectivityManager ?: return "Online"
        val active = cm.activeNetwork ?: return "Offline"
        val caps = cm.getNetworkCapabilities(active) ?: return "Offline"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "Offline"
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data Connected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet Connected"
            else -> "Internet Connected"
        }
    }.getOrDefault("Online")

    fun unregister() {
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
