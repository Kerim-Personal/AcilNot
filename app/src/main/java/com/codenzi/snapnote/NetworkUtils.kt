package com.codenzi.snapnote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network connectivity utility functions for backup/restore operations
 */
object NetworkUtils {
    
    /**
     * Checks if the device has an active internet connection
     * @param context Application context
     * @return true if connected to internet, false otherwise
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Checks if the device has a good internet connection (not metered/limited)
     * @param context Application context
     * @return true if has good connection, false otherwise
     */
    fun hasGoodInternetConnection(context: Context): Boolean {
        if (!isInternetAvailable(context)) return false
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            // Prefer WiFi or unmetered connections for backup/restore
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && 
             !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        } else {
            isInternetAvailable(context)
        }
    }
}