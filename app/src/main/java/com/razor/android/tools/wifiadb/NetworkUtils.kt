package com.razor.android.tools.wifiadb

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * 获取局域网 IPv4 地址：优先 wlan/eth 网卡，其次任意站点本地地址（192.168.x.x / 10.x.x.x）。
     * 使用 NetworkInterface 而非 WifiManager，避免位置权限要求。
     */
    fun getWifiIpv4(): String? {
        return try {
            var fallback: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                val name = ni.name.lowercase()
                for (address in ni.inetAddresses) {
                    if (address !is Inet4Address) continue
                    if (address.isLoopbackAddress || address.isAnyLocalAddress) continue
                    val host = address.hostAddress ?: continue
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("ap")) {
                        return host
                    }
                    if (fallback == null && address.isSiteLocalAddress) {
                        fallback = host
                    }
                }
            }
            fallback
        } catch (e: Exception) {
            null
        }
    }
}
