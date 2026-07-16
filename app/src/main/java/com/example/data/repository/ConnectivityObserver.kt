package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

interface ConnectivityObserver {
    enum class Status {
        Online, Offline, Syncing
    }
    fun observe(): Flow<Status>
    fun setSyncing(isSyncing: Boolean)
}

class NetworkConnectivityObserver(context: Context) : ConnectivityObserver {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val isSyncingFlow = MutableStateFlow(false)

    override fun setSyncing(isSyncing: Boolean) {
        isSyncingFlow.value = isSyncing
    }

    override fun observe(): Flow<ConnectivityObserver.Status> {
        val networkFlow = callbackFlow {
            val isCurrentlyConnected = isConnected()
            trySend(isCurrentlyConnected)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    trySend(false)
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(request, callback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()

        return combine(networkFlow, isSyncingFlow) { isConnected, isSyncing ->
            when {
                !isConnected -> ConnectivityObserver.Status.Offline
                isSyncing -> ConnectivityObserver.Status.Syncing
                else -> ConnectivityObserver.Status.Online
            }
        }.distinctUntilChanged()
    }

    private fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
