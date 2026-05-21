package com.household.app.sync

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object WifiUtils {
    fun getLocalIp(context: Context): String? {
        // Try WifiManager first (fast path for normal home WiFi)
        try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wm?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) return intToIp(wifiIp)
        } catch (_: Exception) {}

        // Fallback: enumerate network interfaces (handles hotspot/bridge/VPN)
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
