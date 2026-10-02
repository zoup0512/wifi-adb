package com.razor.android.tools.wifiadb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.razor.android.tools.wifiadb.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var hostsRepository: HostsRepository
    private val statusExecutor = Executors.newSingleThreadExecutor()
    private val rootExecutor = Executors.newSingleThreadExecutor()
    private val hostExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** null = 检测中，true/false = 检测结果 */
    private var rootAvailable: Boolean? = null
    private var lastIp: String? = null
    private var lastListening = false
    private var notifiedKey: String? = null

    // 主机（PC）管理状态
    private var hosts: MutableList<HostEntry> = mutableListOf()
    private var activeHostId: Long? = null
    private var hostReachable: Boolean? = null
    private var hostCheckGeneration = 0
    private var hostCheckTick = 0

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showConnectNotification()
            } else {
                binding.switchNotify.isChecked = false
                toast(getString(R.string.notify_permission_denied))
            }
        }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshStatus()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvModel.text = "${Build.MANUFACTURER} ${Build.MODEL}"
        binding.tvVersion.text =
            getString(R.string.value_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT)

        binding.btnCopy.setOnClickListener { copyConnectCommand() }
        binding.btnToggle.setOnClickListener { onToggleClicked() }
        binding.btnWirelessDebug.setOnClickListener { openWirelessDebugSettings() }
        binding.btnDevSettings.setOnClickListener { openDeveloperSettings() }
        binding.switchNotify.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                requestNotificationPermissionIfNeeded()
            } else {
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
                notifiedKey = null
            }
        }

        binding.btnAddHost.setOnClickListener { showHostDialog(null) }
        binding.btnEditHost.setOnClickListener { activeHost()?.let { showHostDialog(it) } }
        binding.btnDeleteHost.setOnClickListener { confirmDeleteHost() }

        hostsRepository = HostsRepository(this)
        hosts = hostsRepository.load()
        activeHostId = hostsRepository.loadActiveId()
        if (activeHostId == null || hosts.none { it.id == activeHostId }) {
            activeHostId = hosts.firstOrNull()?.id
        }
        renderHosts()

        checkRootInBackground()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(refreshRunnable)
    }

    private fun checkRootInBackground() {
        rootExecutor.execute {
            val hasRoot = AdbController.isRootAvailable()
            mainHandler.post {
                rootAvailable = hasRoot
                updateToggleUi()
            }
        }
    }

    private fun refreshStatus() {
        statusExecutor.execute {
            val ip = NetworkUtils.getWifiIpv4()
            val listening = AdbController.isTcpListening(AdbController.DEFAULT_PORT)
            val usbAdb = AdbController.isUsbAdbEnabled(this)
            val wireless = AdbController.isWirelessDebugEnabled(this)
            mainHandler.post { renderStatus(ip, listening, usbAdb, wireless) }
        }
    }

    private fun renderStatus(ip: String?, listening: Boolean, usbAdb: Boolean, wireless: Boolean) {
        lastIp = ip
        lastListening = listening

        binding.tvAddress.text = ip?.let {
            getString(R.string.address_value, it, AdbController.DEFAULT_PORT)
        } ?: getString(R.string.address_unknown)

        binding.tvStatus.setText(if (listening) R.string.status_on else R.string.status_off)
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                this,
                if (listening) R.color.status_on else R.color.status_off
            )
        )

        binding.tvUsbChip.setText(if (usbAdb) R.string.usb_adb_on else R.string.usb_adb_off)
        binding.tvUsbChip.backgroundTintList =
            ColorStateList.valueOf(if (usbAdb) CHIP_BG_ON else CHIP_BG_OFF)
        binding.tvUsbChip.setTextColor(if (usbAdb) CHIP_FG_ON else CHIP_FG_OFF)

        binding.tvUsbAdb.setText(if (usbAdb) R.string.value_on else R.string.value_off)
        if (Build.VERSION.SDK_INT >= 30) {
            binding.tvWireless.setText(if (wireless) R.string.value_on else R.string.value_off)
        } else {
            binding.tvWireless.setText(R.string.value_unsupported)
        }

        updateToggleUi()
        renderHostCard()
        maybeCheckHostReachability()
        if (binding.switchNotify.isChecked) {
            showConnectNotification()
        }
    }

    private fun updateToggleUi() {
        binding.btnToggle.text = when (val root = rootAvailable) {
            null -> getString(R.string.toggle_checking)
            true -> getString(if (lastListening) R.string.toggle_disable else R.string.toggle_enable)
            false -> getString(R.string.toggle_needs_root)
        }
        binding.tvRoot.setText(
            when (rootAvailable) {
                null -> R.string.root_checking
                true -> R.string.root_yes
                false -> R.string.root_no
            }
        )
    }

    private fun onToggleClicked() {
        when (rootAvailable) {
            null -> toast(getString(R.string.toggle_checking))
            false -> showNoRootDialog()
            true -> toggleTcpAdb()
        }
    }

    private fun toggleTcpAdb() {
        val enable = !lastListening
        binding.btnToggle.isEnabled = false
        rootExecutor.execute {
            val ok = if (enable) AdbController.enableTcpAdb() else AdbController.disableTcpAdb()
            mainHandler.post {
                binding.btnToggle.isEnabled = true
                if (ok) {
                    toast(getString(if (enable) R.string.toggle_sent_on else R.string.toggle_sent_off))
                    refreshStatus()
                } else {
                    toast(getString(R.string.toggle_failed))
                }
            }
        }
    }

    private fun showNoRootDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.no_root_dialog_title)
            .setMessage(R.string.no_root_dialog_message)
            .setNeutralButton(R.string.no_root_dialog_copy) { _, _ ->
                copyText("adb tcpip ${AdbController.DEFAULT_PORT}")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.no_root_dialog_open_dev) { _, _ -> openDeveloperSettings() }
            .show()
    }

    private fun copyConnectCommand() {
        val cmd = lastIp?.let { "adb connect $it:${AdbController.DEFAULT_PORT}" }
            ?: "adb connect <手机IP>:${AdbController.DEFAULT_PORT}"
        copyText(cmd)
    }

    private fun copyText(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb", text))
        toast(getString(R.string.copied, text))
    }

    private fun openWirelessDebugSettings() {
        try {
            // 该 action 未作为常量公开，只能用字符串；旧系统不识别时回退到开发者选项
            startActivity(Intent("android.settings.WIRELESS_DEBUG_SETTINGS"))
        } catch (e: Exception) {
            openDeveloperSettings()
            toast(getString(R.string.wireless_settings_unavailable))
        }
    }

    private fun openDeveloperSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            toast(getString(R.string.dev_settings_unavailable))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showConnectNotification()
        }
    }

    private fun showConnectNotification() {
        if (!binding.switchNotify.isChecked) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val title =
            getString(if (lastListening) R.string.notify_title_on else R.string.notify_title_off)
        val text = lastIp?.let { "adb connect $it:${AdbController.DEFAULT_PORT}" }
            ?: getString(R.string.notify_waiting_ip)
        val key = "$title|$text"
        if (key == notifiedKey) return

        ensureNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            notifiedKey = key
        } catch (e: SecurityException) {
            // 通知权限在弹窗期间被收回，忽略
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.notify_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = getString(R.string.notify_subtitle)
                        setShowBadge(false)
                    }
                )
            }
        }
    }

    // ==================== 主机（PC）管理 ====================

    private fun activeHost(): HostEntry? = hosts.firstOrNull { it.id == activeHostId }

    /** 每 N 次状态刷新（约 10 秒）重新检测一次连通性，避免频繁 ping */
    private fun maybeCheckHostReachability() {
        if (hostCheckTick++ % 5 == 0) checkHostReachability()
    }

    private fun checkHostReachability() {
        val host = activeHost()
        if (host == null) {
            hostReachable = null
            renderHostCard()
            return
        }
        hostReachable = null
        renderHostCard()
        val generation = ++hostCheckGeneration
        hostExecutor.execute {
            val reachable = HostChecker.isReachable(host.ip)
            if (generation != hostCheckGeneration) return@execute
            mainHandler.post {
                if (generation == hostCheckGeneration) {
                    hostReachable = reachable
                    renderHostCard()
                }
            }
        }
    }

    private fun renderHosts() {
        binding.chipGroupHosts.removeAllViews()
        hosts.forEach { host ->
            val chip = Chip(this)
            chip.text = host.label
            chip.isCheckable = true
            chip.isChecked = host.id == activeHostId
            chip.setOnClickListener { setActiveHost(host.id) }
            chip.setOnLongClickListener {
                showHostDialog(host)
                true
            }
            binding.chipGroupHosts.addView(chip)
        }
        binding.scrollHostChips.visibility = if (hosts.isEmpty()) View.GONE else View.VISIBLE
        renderHostCard()
    }

    private fun renderHostCard() {
        val host = activeHost()
        binding.tvActiveHost.text = host?.let { "${it.label}（${it.ip}）" }
            ?: getString(R.string.host_none)

        if (host == null) {
            binding.tvHostState.visibility = View.GONE
            binding.tvHostHint.setText(R.string.host_empty_hint)
        } else {
            binding.tvHostState.visibility = View.VISIBLE
            val (textRes, bg, fg) = when (hostReachable) {
                true -> Triple(R.string.host_reachable, HOST_BG_ON, HOST_FG_ON)
                false -> Triple(R.string.host_unreachable, HOST_BG_OFF, HOST_FG_OFF)
                null -> Triple(R.string.host_checking, CHIP_BG_OFF, CHIP_FG_OFF)
            }
            binding.tvHostState.setText(textRes)
            binding.tvHostState.backgroundTintList = ColorStateList.valueOf(bg)
            binding.tvHostState.setTextColor(fg)

            binding.tvHostHint.text = when (val phoneIp = lastIp) {
                null -> getString(R.string.host_hint_no_ip, host.label)
                else -> getString(
                    R.string.host_hint_cmd, host.label, "$phoneIp:${AdbController.DEFAULT_PORT}"
                )
            }.let { cmdHint ->
                // 探测被防火墙拦截时会误显示不可达，需要向用户说明这不一定影响实际连接
                if (hostReachable == false) "$cmdHint\n${getString(R.string.host_unreachable_note)}"
                else cmdHint
            }
        }

        binding.btnEditHost.isEnabled = host != null
        binding.btnDeleteHost.isEnabled = host != null
    }

    private fun setActiveHost(id: Long) {
        if (activeHostId == id) return
        activeHostId = id
        hostsRepository.save(hosts, activeHostId)
        hostReachable = null
        renderHosts()
        activeHost()?.let { toast(getString(R.string.host_switched, it.label)) }
        checkHostReachability()
    }

    private fun showHostDialog(entry: HostEntry?) {
        val view = layoutInflater.inflate(R.layout.dialog_host, null)
        val etLabel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHostLabel)
        val etIp = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etHostIp)
        if (entry != null) {
            etLabel.setText(entry.label)
            etIp.setText(entry.ip)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (entry == null) R.string.host_dialog_add_title else R.string.host_dialog_edit_title)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        // 覆盖按钮点击，校验失败时不关闭对话框
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val ip = etIp.text?.toString()?.trim().orEmpty()
            if (!HostChecker.IPV4_PATTERN.matches(ip)) {
                toast(getString(R.string.host_ip_invalid))
                return@setOnClickListener
            }
            var label = etLabel.text?.toString()?.trim().orEmpty()
            if (label.isEmpty()) label = "PC-${hosts.size + 1}"
            if (entry == null) {
                val nextId = (hosts.maxOfOrNull { it.id } ?: 0L) + 1
                val host = HostEntry(nextId, label, ip)
                hosts.add(host)
                if (activeHostId == null) activeHostId = host.id
            } else {
                entry.label = label
                entry.ip = ip
            }
            hostsRepository.save(hosts, activeHostId)
            renderHosts()
            checkHostReachability()
            toast(getString(R.string.host_saved, label))
            dialog.dismiss()
        }
    }

    private fun confirmDeleteHost() {
        val host = activeHost() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.host_delete_title)
            .setMessage(getString(R.string.host_delete_confirm, host.label))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.host_delete) { _, _ ->
                hosts.remove(host)
                if (activeHostId == host.id) {
                    activeHostId = hosts.firstOrNull()?.id
                }
                hostsRepository.save(hosts, activeHostId)
                renderHosts()
                checkHostReachability()
            }
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
        private const val CHANNEL_ID = "wifi_adb_connect_cmd"
        private const val NOTIFICATION_ID = 1001

        private const val CHIP_BG_ON = 0x1F2E9E5B
        private const val CHIP_BG_OFF = 0x1F9AA0A6
        private const val CHIP_FG_ON = 0xFF2E9E5B.toInt()
        private const val CHIP_FG_OFF = 0xFF5F6368.toInt()
        private const val HOST_BG_ON = 0x1F2E9E5B
        private const val HOST_BG_OFF = 0x1FD93025
        private const val HOST_FG_ON = 0xFF2E9E5B.toInt()
        private const val HOST_FG_OFF = 0xFFD93025.toInt()
    }
}
