package com.nightread.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Проверка сетевого соединения.
 */
class SyncNetworkChecker(private val context: Context) {
    companion object {
        private const val TAG = "SYNC_NETWORK"
    }

    fun isConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!isConnected) Log.w(TAG, "No internet connection detected")
        return isConnected
    }
}
