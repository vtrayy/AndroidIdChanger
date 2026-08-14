# Xposed 入口由框架按名称加载，不能被 R8 删除或改名。
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.bigsing.changer.XposedActive { *; }
-keep class com.bigsing.changer.MainHook { public *; }
-dontwarn io.github.libxposed.annotation.**
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-keep class com.bigsing.changer.modern.ModernServiceBridge { public static *; }

# META-INF/xposed/java_init.list 中保存了现代入口的类名。
-adaptresourcefilecontents META-INF/xposed/java_init.list
