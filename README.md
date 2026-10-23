# EON

EON 是一个面向 Root Android 设备的本地可执行 `.so` 启动器。它可以扫描 `/data/adb`、安全保存命名密钥、通过 stdin 把密钥发送给目标进程，并显示实时运行日志。

## 下载

[EON v1.0 正式版](https://github.com/Lucifnil/EON/releases/tag/v1.0) 提供安装 APK 和源码 ZIP。

## 功能

- 文件、密钥、日志三页横向拖动分页，悬浮底栏与手势连续联动。
- 白色 iOS 风格界面，统一使用 `#007AFF` 主题色。
- Android 12+ 使用 Backdrop 实时背景采样和模糊；Android 13+ 增加 lens 折射；旧系统使用 Haze 降级。
- Android 原生预测性返回：设置页跟手返回，根页面保留系统 back-to-home。
- 通过 `su` 仅扫描 `/data/adb` 当前层级，不进入任何子目录。
- 显示可执行状态；EON 不会自动 `chmod` 或修改目标文件。
- 启动参数使用受控 token 化和逐参数 Shell 转义。
- 密钥仅通过目标进程 stdin 传入，不加入命令行参数；日志中出现完整密钥时会遮盖。
- 密钥使用 Android Keystore 中不可导出的 AES-256 密钥，以 AES-GCM 加密保存。
- 手动输入的运行密钥会以本机时间自动命名保存；相同密钥不会重复记录，每个 `.so` 会记住上次使用的密钥。
- stdout/stderr 并行读取，日志限制为最近 500 行，每行最多保留 4096 个字符。
- 同时管理一个前台运行任务；停止操作会取消准备或运行中的任务。
- 无网络权限。

## 系统要求

- Android 8.0（API 26）或更高版本。
- 设备提供可交互授权的 `su`，例如 Magisk。
- 目标 `.so` 必须是具备入口点和执行权限的可执行 ELF。普通共享动态库不能单独启动。

首次扫描时请在 Root 管理器中授权 EON。授权被拒绝或设备未 Root 时，文件扫描和启动功能不可用，但密钥库界面仍可打开。

## 安全模型

- App Manifest 未声明网络权限。
- `allowBackup=false`，并排除 SharedPreferences 的云备份和设备迁移。
- 每次保存密钥时 AES-GCM 都生成新的随机 IV。
- 密钥不加入 argv、剪贴板或保存状态，目标输出的完整密钥会在日志进入界面前遮盖。
- 路径和参数分别进行验证与 Shell 单引号转义。
- 扫描和启动均被限制在 `/data/adb` 当前目录，子目录内文件不会被列出或启动。

Root 本身拥有设备最高权限。只应运行来源可信的 ELF 文件；EON 无法限制目标进程获得 Root 后的行为。

## 构建

使用 JDK 17 和 Android SDK 36：

```bash
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK 输出于 `app/build/outputs/apk/debug/app-debug.apk`。

正式版固定为 `versionCode=1`、`versionName=1.0`。使用本机签名文件重新构建第一版：

```bash
./gradlew clean testDebugUnitTest lintRelease assembleRelease
bash scripts/package-release.sh
```

交付文件为 `dist/EON-1.0.apk` 与 `dist/EON-1.0-source.zip`，同名文件会被替换。
`signing/eon-release.jks` 与 `signing/RELEASE_KEY.txt` 仅由发布者私下保管，不包含在源码包中。
其他人可编译调试版，或使用自己的签名文件发布独立构建。

## 设计资料

- `design/EON_APP_GENERATION_PROMPT.md`
- `design/eon-ui-reference.png`
- `design/eon-icon-reference.png`

## 第三方组件

玻璃效果使用 AndroidLiquidGlass / Backdrop 与 Haze。相关出处和已改编代码声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 已知限制

- 无 Root 设备无法读取 `/data/adb`。
- 不同 Root 管理器的授权 UI 和超时行为可能不同。
- Android 8–11 不支持系统 RuntimeShader，因此不会显示 Android 13+ 的 lens 折射，只显示兼容背景模糊/高可读性材质。
- 进程在 EON 被系统强制终止后不保证继续运行；当前版本不是后台守护进程管理器。
- 停止管理的是 EON 启动并持有的进程；目标自行创建的后台服务或独立子进程不保证同时退出。
- 日志遮盖匹配完整密钥，不承诺识别目标刻意拆分、变形或编码后的密钥。
