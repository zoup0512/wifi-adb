package com.razor.android.tools.wifiadb

import java.net.InetSocketAddress
import java.net.Socket

/**
 * 检测手机到 PC 主机的连通性。
 * 依次尝试：ICMP ping（跨平台最通用）→ Windows 常开的 TCP 端口（445/139/135）。
 * 部分系统防火墙会拦截 ICMP，端口探测作为兜底；均为尽力而为的诊断参考。
 */
object HostChecker {

    val IPV4_PATTERN = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )

    private val FALLBACK_PORTS = intArrayOf(445, 139, 135)

    fun isReachable(ip: String): Boolean {
        if (!IPV4_PATTERN.matches(ip)) return false
        return pingCheck(ip) || FALLBACK_PORTS.any { tcpCheck(ip, it) }
    }

    private fun pingCheck(ip: String): Boolean = try {
        // 2 个包减少偶发丢包造成的误判
        val process = ProcessBuilder("ping", "-c", "2", "-W", "1", ip)
            .redirectErrorStream(true)
            .start()
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }

    private fun tcpCheck(ip: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(ip, port), 700)
        }
        true
    } catch (e: Exception) {
        false
    }
}
