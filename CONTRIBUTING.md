# 贡献指南

## 基本要求

- 只向 `main` 提交，不维护长期多分支版本线。
- 代码只使用 Java，不引入 Kotlin、AndroidX 或不必要的 native 依赖。
- 保持 `legacy(minSdk 17)` 与 `modern(minSdk 26)` 分层，不在公共代码中引用任一 Xposed API。
- 新 API 必须有版本保护；新增 Hook 必须在方法不存在时安全降级。
- 不提交真实设备标识符、账号、个人信息、本机绝对路径、私有密钥、日志、IDE 文件或构建产物。
- Markdown 使用中文与 LF 换行；代码注释解释原因和边界，避免记录个人署名信息。

## 提交前检查

```powershell
cd source
./gradlew.bat testLegacyDebugUnitTest testModernDebugUnitTest
./gradlew.bat lintLegacyRelease lintModernRelease
./gradlew.bat assembleLegacyRelease assembleModernRelease
```

涉及配置格式、标识符生成或作用域时必须增加纯 Java 单元测试。涉及 Android 版本或 Xposed 框架兼容性时，在 `doc/兼容性.md` 中写明“实测”“构建验证”或“未知”，不得把推测写成保证。

## 安全边界

不接受以下功能：隐藏模块/框架、绕过硬件证明或完整性服务、root 删除第三方数据、写入第三方沙箱、世界可写文件、采集或上报真实敏感标识符、未经授权的全局 Hook。API 17～23 为兼容原版 Xposed 所需的只读共享配置属于已记录的遗留例外，不得扩展到新系统。
