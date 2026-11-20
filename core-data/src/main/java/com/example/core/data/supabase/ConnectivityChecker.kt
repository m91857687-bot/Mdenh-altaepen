package com.example.core.data.supabase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface ConnectivityChecker {
    fun isOnline(): Boolean
}

object AlwaysOnlineChecker : ConnectivityChecker {
    override fun isOnline(): Boolean = true
}

class AndroidConnectivityChecker(context: Context) : ConnectivityChecker {
    private val appContext = context.applicationContext

    override fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
