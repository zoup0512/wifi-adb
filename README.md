# WiFi ADB

方便 Android 设备通过 WiFi 与 PC 进行 ADB 调试的工具应用。

- 包名：`com.razor.android.tools.wifiadb`
- 语言：Kotlin，最低支持 Android 5.0（API 21）

## 功能

- 自动检测并展示手机局域网 IP，一键复制 `adb connect <ip>:5555` 命令
- 每 2 秒探测 5555 端口监听状态，实时显示网络 ADB 是否已开启
- 已 Root 设备：一键开启 / 关闭网络 ADB（通过 `setprop service.adb.tcp.port` + 重启 adbd）
- 未 Root 设备：弹窗引导 USB 方式（`adb tcpip 5555`），可复制命令、直达开发者选项
- Android 11+：提供「无线调试」设置页直达入口，并显示其开关状态
- **调试主机管理**：保存多台常用 PC 的 IP（名称 + 地址），点选标签一键切换当前主机；
  自动检测手机与当前主机的连通性（ping + 445/139/135 端口探测），切换后提示应执行的连接命令
- 可选常驻通知，随时查看连接命令
- 显示设备型号、系统版本、Root / USB 调试 / 无线调试状态

> 连通性检测说明：Windows 在「公用网络」防火墙配置下会拦截 ICMP 和 SMB 端口入站，
> 此时显示「不可达」不一定影响实际连接（adb connect 由 PC 主动发起）。

## 构建

环境要求：JDK 17、Android SDK（含 `platforms;android-35`）。

在 `local.properties` 中配置 SDK 路径（Android Studio 会自动生成），或设置 `ANDROID_HOME` 环境变量，例如：

```properties
sdk.dir=/path/to/Android/Sdk
```

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。也可用 Android Studio 直接打开本目录运行。

## 使用说明

1. **已 Root**：打开应用点击「一键开启」，状态灯变绿后在电脑执行 `adb connect 手机IP:5555`。
2. **无 Root**：先用数据线连接手机与电脑（USB 调试已开启），在电脑执行 `adb tcpip 5555`，
   成功后拔掉数据线，再执行 `adb connect 手机IP:5555`。
3. **Android 11+**：开发者选项 → 无线调试 → 与电脑配对（`adb pair ip:port`，端口随机）。

调试完成后点击「一键关闭」，或在电脑执行 `adb usb` 恢复 USB 模式。

## 注意

- WiFi ADB 传输未加密（Android 11 之前），请勿在公共网络环境中开启。
- `tools/gen_icons.py` 用于重新生成启动图标（需要 Pillow）。
