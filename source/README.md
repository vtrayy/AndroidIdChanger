# 源码构建说明

## 工具链

- JDK 17
- Android SDK Platform 37 与 Build Tools 37
- Android Gradle Plugin 9.2.1
- Gradle Wrapper 9.4.1
- Java source/target 11

项目不使用 Kotlin、AndroidX、NDK、CMake 或 native 库。依赖仓库优先使用国内镜像，Google Maven 与 Maven Central 作为回退。不要提交 `local.properties`、SDK 绝对路径、IDE 配置、构建缓存或个人签名。

## 源码分层

```text
app/src/main/      普通应用 UI、设备采集、配置核心
app/src/legacy/    Xposed API 53 入口与传统配置快照
app/src/modern/    libxposed API 102 入口、服务桥与 META-INF 元数据
app/src/test/      纯 Java 配置、作用域和标识符测试
```

`legacy` 的 `minSdk` 为 17，`modern` 的 `minSdk` 为 26。libxposed service 只进入 modern 依赖图，避免把 legacy 的最低系统抬高。

## 常用命令

Windows PowerShell：

```powershell
./gradlew.bat clean
./gradlew.bat testLegacyDebugUnitTest testModernDebugUnitTest
./gradlew.bat lintLegacyRelease lintModernRelease
./gradlew.bat assembleLegacyRelease assembleModernRelease
```

Linux/macOS：

```bash
./gradlew clean
./gradlew testLegacyDebugUnitTest testModernDebugUnitTest
./gradlew lintLegacyRelease lintModernRelease
./gradlew assembleLegacyRelease assembleModernRelease
```

APK 输出：

```text
app/build/outputs/apk/legacy/release/AndroidIdChanger-legacy-release-2.2.1.apk
app/build/outputs/apk/modern/release/AndroidIdChanger-modern-release-2.2.1.apk
```

APK 为纯 Java 通用包，不含 ABI 目录。测试阶段无需分别生成 arm64/x86 产物；同一个 APK 可用于 arm32 与 arm64。

## 公开测试签名

`keystore/androididchanger-public.jks` 与 `keystore/public-signing.properties` 是仓库公开、可复现构建用的测试签名，口令不是秘密。任何人都能用它签名，因此不得把该证书当作生产身份或安全信任根。正式发布者必须使用自己的私密密钥，并通过本地未提交的配置覆盖签名。

legacy release 明确启用 v1 与 v2 签名，以兼容 Android 4.2；modern 同样使用该公开测试证书。

## API 兼容规则

- 主路径必须能在 API 17 加载；新 API 先判断 `Build.VERSION.SDK_INT`。
- 方法签名若直接包含新系统类型，应放入对应 API 隔离类，避免旧 Dalvik 类校验失败。
- Xposed API 依赖必须使用 `compileOnly`，不得打入 APK。
- legacy 与 modern 入口不能互相调用或混用两套 Hook API。
- Hook 热路径只读不可变内存快照，不执行文件、Binder 或配置解析 I/O。
- 不修改 `SDK_INT`、USB 调试状态或 Android 17 禁止可靠写入的现代静态 final 字段。

## 配置测试

`ProfileCodec` v2 对输入大小、严格 UTF-8、版本、字段白名单、重复属性、类型、长度、控制字符、包名、全局作用域和值来源进行校验，并兼容迁移 v1。`IdentifierGenerator` 测试 Luhn、格式、测试号段、MAC 位属性、各发行变体的完整随机覆盖、首配不泄漏真实标识符及可移植字段过滤。新增字段或配置属性时必须同步扩展测试，且不能放入真实设备标识符作为夹具。

## 发布检查

1. 执行两个变体的测试、lint 和 release 构建。
2. 用 `aapt dump badging` 检查 min/target SDK，用 `apksigner verify` 检查签名。
3. 检查 legacy APK 有 `assets/xposed_init` 和传统 manifest metadata。
4. 检查 modern APK 有 `META-INF/xposed/java_init.list`、`module.prop`、`scope.list`，且没有 `native_init.list`。
5. 在至少一个最低版本、一个主流版本和最新版本设备上回归；未实测组合写入兼容矩阵。
6. 扫描源码、文档、配置与产物，禁止机器名、本机路径、账号、个人姓名、电话、邮箱、访问令牌和私有密钥进入提交。
