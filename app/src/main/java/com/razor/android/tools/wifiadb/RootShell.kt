package com.razor.android.tools.wifiadb

import java.util.Timer
import kotlin.concurrent.timerTask

/**
 * 以 root 身份执行 shell 命令（通过 `su`）。
 * 所有命令一次性写入 stdin，靠最后的 `exit` 结束会话；带看门狗防止 su 授权弹窗导致永久阻塞。
 */
object RootShell {

    private const val TIMEOUT_MS = 8000L

    /** 成功返回合并后的输出，失败（无 su / 授权被拒绝 / 超时）返回 null */
    fun run(vararg commands: String): String? {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val watchdog = Timer(true)
            watchdog.schedule(timerTask { process.destroy() }, TIMEOUT_MS)
            process.outputStream.use { output ->
                output.write((commands.joinToString("\n") + "\nexit\n").toByteArray())
                output.flush()
            }
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            watchdog.cancel()
            text.takeIf { exitCode == 0 }
        } catch (e: Exception) {
            null
        }
    }
}
