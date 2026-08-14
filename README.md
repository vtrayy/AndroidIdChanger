# AndroidIdChanger

AndroidIdChanger 是一个 Java-only Android 项目，既可作为普通设备信息查看器，也可作为按应用作用域工作的 Xposed 模块。当前开源版本为 `2.2.1`，源码统一位于 [`source`](source/) 子目录，仓库原有的 `doc`、`release`、`screenshot` 目录结构保持不变。

本项目面向开发、兼容性验证和隐私研究。使用 Xposed 会改变目标进程运行时行为，请仅在自己拥有或明确获准测试的设备和应用中使用。

## 当前能力

- 查看系统与构建、Android ID、电话/SIM、Wi-Fi/蓝牙、CPU/ABI、内存、存储、屏幕、电池、网络、传感器、摄像头、应用签名和安装来源等信息。
- 对受权限或系统隐私策略限制的值显示“权限未授予”“系统已脱敏”“不支持”或“不可用”，不再用其他标识符冒充缺失值。
- 按发行变体生成非空、格式有效且明确属于测试用途的替换值；只读维度保留本机展示值，不写入替换配置。
- 通过严格校验的 UTF-8 properties 配置导入、导出和整体替换；配置包含字段开关、值来源与精确包名作用域。
- 提供传统 Xposed 与现代 libxposed 两个互斥发行变体，共享同一套普通应用界面和配置核心。
- 无 Bugly、广告 SDK 或自定义遥测；不申请联网权限。设备信息导出时默认遮盖敏感标识符。

## 选择 APK

| 发行变体 | 最低系统 | Xposed 接口 | 适用场景 |
| --- | ---: | --- | --- |
| `legacy` | Android 4.2 / API 17 | 原版 Xposed API 53 | 经典 Xposed，以及提供原版 API 兼容层的框架 |
| `modern` | Android 8.0 / API 26 | libxposed API 102 | 支持现代 libxposed 模块格式与 RemotePreferences 的框架 |

仓库内已提供 [legacy release 2.2.1](release/AndroidIdChanger-legacy-release-2.2.1.apk) 和 [modern release 2.2.1](release/AndroidIdChanger-modern-release-2.2.1.apk)。

两个 APK 使用相同包名和签名，不能同时安装；切换变体时使用覆盖安装。只作为普通应用查看设备信息时，Android 4.2～7.1 选择 `legacy`，Android 8.0 及以上可选择任一变体。

框架支持范围不等于 Android 系统支持范围。未经实际验证的 Xposed 变种均标记为实验性，详见 [`doc/兼容性.md`](doc/兼容性.md)。

## 快速使用

1. 安装与系统、框架匹配的 release APK。
2. 仅查看本机信息：直接打开应用，点“环境检测”；拒绝敏感权限也能查看非敏感维度。
3. 使用 Xposed：先在框架管理器中启用模块并只勾选需要测试的目标应用。
4. 在应用内点“一键随机”或手工编辑字段，勾选需要替换的字段；灰色字段仅展示，不参与替换。
5. 在“Hook App”中选择完全匹配的目标包名，然后保存。
6. 传统入口需强制停止并重启目标应用；现代入口可实时收到配置变化，但目标应用若已缓存结果仍需重启其进程。

完整步骤、权限用途、配置格式和故障排查见 [`doc/使用帮助.md`](doc/使用帮助.md)。

## 重要边界

- Android 8 起，`ANDROID_ID` 按应用签名、用户和设备划分作用域；它不是永久硬件 ID。
- Android 10 起，IMEI、MEID、序列号、IMSI、ICCID 等不可重置标识符通常不再向普通应用开放。
- Android 17 对静态 `final` 字段修改施加了更严格限制。因此现代入口只 Hook 公共方法；传统入口对 `Build.*` 字段仅作尽力而为，字段直读、native 读取和内联常量可能仍是真值。
- 本项目不修改 KeyMint、硬件证明、Play Integrity 或其他硬件信任链，也不承诺对抗完整性检测。
- 默认作用域为空且“Hook 全部应用”关闭。应用内作用域和框架管理器作用域必须同时允许，替换才会发生。

相关平台行为以 [Android 17 行为变更](https://developer.android.com/about/versions/17/behavior-changes-17)、[Android 标识符最佳实践](https://developer.android.com/identity/user-data-ids) 和 [libxposed API](https://github.com/libxposed/api) 为准。

## 构建

项目使用 AGP 9.2.1、Gradle 9.4.1、JDK 17、`compileSdk/targetSdk 37`、Java 11、Android Support Library 28；不使用 Kotlin、AndroidX、NDK、CMake 或任何 `.so`。纯 Java APK 与 CPU ABI 无关，可运行于 arm32 和 arm64 设备。

```powershell
cd source
./gradlew.bat testLegacyDebugUnitTest testModernDebugUnitTest
./gradlew.bat lintLegacyRelease lintModernRelease
./gradlew.bat assembleLegacyRelease assembleModernRelease
```

Gradle 与 Maven 国内镜像已写入工程，官方仓库只作回退。完整工具链、源码分层和签名说明见 [`source/README.md`](source/README.md)。

## 隐私与安全

- 配置权威副本保存在单一 SharedPreferences 文件中并批量提交；普通应用环境使用私有模式。legacy 入口因原版 Xposed 的跨进程读取限制，需要在框架明确放行时使用 Xposed 可读模式，否则自动安全回退为私有模式。
- 应用不会持久化本机真实设备信息快照；可移植配置只包含可替换字段，旧配置中的只读设备字段在导入时会被丢弃。
- 配置导入限制为 256 KiB，采用字段白名单、严格 UTF-8、类型、长度、重复项、包名和版本校验；完整解析成功后才提交。
- 日志不记录目标包名或替换后的 IMEI、IMSI、ICCID、序列号等值。
- 旧私有工程中的遥测、root 删除、目标沙箱复制、`chmod 777`、模块隐藏、native 环境探测和真实样式标识符数据集均未进入新源码。

安全报告与贡献规则见 [`SECURITY.md`](SECURITY.md) 和 [`CONTRIBUTING.md`](CONTRIBUTING.md)。

## 许可证

本项目以 [Apache License 2.0](LICENSE) 开源。第三方组件及其许可证见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。使用前请同时阅读 [`免责声明.md`](免责声明.md)。
