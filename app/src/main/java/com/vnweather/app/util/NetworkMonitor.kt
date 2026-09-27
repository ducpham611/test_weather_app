package com.vnweather.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

/** Minimal connectivity check that also works on API 21. */
object NetworkMonitor {

    @Suppress("DEPRECATION")
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            cm.activeNetworkInfo?.isConnected == true
        }
    }
}
