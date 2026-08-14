package com.bigsing.changer;

import com.bigsing.changer.config.XposedConfigBridge;

public class XposedActive {
    public static boolean isActive() {
        // 传统入口会 Hook 此方法；现代入口以框架服务连接作为“已识别”信号。
        return XposedConfigBridge.isFrameworkConnected();
    }
}
