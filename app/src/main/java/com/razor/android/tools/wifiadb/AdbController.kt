package com.razor.android.tools.wifiadb

import android.content.Context
import android.provider.Settings
import java.net.InetSocketAddress
import java.net.Socket

object AdbController {

    const val DEFAULT_PORT = 5555

    fun isRootAvailable(): Boolean {
        val output = RootShell.run("id") ?: return false
        return output.contains("uid=0")
    }

    /** Root 方式开启 adbd 的 TCP 监听 */
    fun enableTcpAdb(port: Int = DEFAULT_PORT): Boolean = RootShell.run(
        "setprop service.adb.tcp.port $port",
        "stop adbd",
        "start adbd"
    ) != null

    /** Root 方式关闭 TCP 监听并恢复默认 */
    fun disableTcpAdb(): Boolean = RootShell.run(
        "setprop service.adb.tcp.port -1",
        "stop adbd",
        "start adbd"
    ) != null

    /** 通过本机回环探测 adbd 是否正在监听 TCP 端口，无需任何权限 */
    fun isTcpListening(port: Int = DEFAULT_PORT): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
        }
        true
    } catch (e: Exception) {
        false
    }

    fun isUsbAdbEnabled(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    } catch (e: Exception) {
        false
    }

    /** Android 11+ 的「无线调试」开关状态 */
    fun isWirelessDebugEnabled(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
    } catch (e: Exception) {
        false
    }
}
