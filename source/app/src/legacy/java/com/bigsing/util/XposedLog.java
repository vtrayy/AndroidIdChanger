package com.bigsing.util;

import android.util.Log;

import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.legacy.LegacyConfig;

import de.robv.android.xposed.XposedBridge;

public class XposedLog {
    private static boolean isDebugMode = false;

    public synchronized static void logd(String msg) {
        if (isDebugMode) {
            Log.d("Xposed", Constant.TAG + ": " + sanitize(msg));
            XposedBridge.log(Constant.TAG + ": " + sanitize(msg));
        }
    }

    public static void configure() {
        isDebugMode = LegacyConfig.isDebugMode();
    }

    public synchronized static void log(String msg) {
        logd(msg);
    }

    public synchronized static void loge(String msg) {
        Log.e("Xposed", Constant.TAG + ": " + sanitize(msg));
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        int valuePosition = message.indexOf(" with ");
        if (valuePosition >= 0) {
            return message.substring(0, valuePosition) + " configured";
        }
        if (message.startsWith("App Hook List:")) {
            return "App hook list loaded";
        }
        return message;
    }
}
