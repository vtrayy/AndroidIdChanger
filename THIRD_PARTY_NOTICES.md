# 第三方组件说明

AndroidIdChanger 的工程代码以 Apache License 2.0 发布。构建还引用以下第三方组件；实际许可证以各项目随附文本为准。

| 组件 | 用途 | 版本 | 许可证 |
| --- | --- | ---: | --- |
| Android Support Library | API 17 兼容 UI | 28.0.0 | Apache-2.0 |
| Xposed Framework API | legacy 编译期接口，不打入 APK | 53 | 以原项目发布条款为准 |
| libxposed API | modern 编译期 Hook 接口 | 102.0.0 | Apache-2.0 |
| libxposed service | modern RemotePreferences 服务接口 | 102.0.0 | Apache-2.0 |
| JUnit | 单元测试 | 4.13.2 | EPL-1.0 |

项目没有复制这些依赖的源码。Xposed 与 libxposed API 均按 `compileOnly` 使用；框架在运行时提供 Hook 实现。libxposed service 只进入 modern APK。

来源：[Android Support Library](https://developer.android.com/topic/libraries/support-library)、[XposedBridge](https://github.com/rovo89/XposedBridge)、[libxposed API](https://github.com/libxposed/api)、[libxposed service](https://github.com/libxposed/service)、[JUnit 4](https://github.com/junit-team/junit4)。
