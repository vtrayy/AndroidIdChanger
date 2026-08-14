package com.bigsing.changer.config;

import android.app.Application;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 让公共应用代码在不依赖 libxposed 的前提下调用 modern flavor 服务桥。
 */
public final class XposedConfigBridge {
    private static final String BRIDGE_CLASS = "com.bigsing.changer.modern.ModernServiceBridge";

    private XposedConfigBridge() {
    }

    public static void initialize(Application application) {
        invoke("initialize", new Class<?>[]{Application.class}, new Object[]{application});
    }

    public static boolean sync() {
        Object result = invoke("sync", new Class<?>[0], new Object[0]);
        return Boolean.TRUE.equals(result);
    }

    public static boolean isFrameworkConnected() {
        Object result = invoke("isFrameworkConnected", new Class<?>[0], new Object[0]);
        return Boolean.TRUE.equals(result);
    }

    /** modern flavor 用于把异步服务连接状态通知给普通应用界面。 */
    public static void setFrameworkStateListener(Runnable listener) {
        invoke("setFrameworkStateListener", new Class<?>[]{Runnable.class},
                new Object[]{listener});
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object[] arguments) {
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            Method method = bridge.getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException | LinkageError ignored) {
            return null;
        }
    }
}
