package com.bigsing.changer.legacy;

import com.bigsing.changer.constant.Constant;
import com.bigsing.util.PhoneInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;

/**
 * 传统 Xposed 的只读配置快照。每个目标进程启动时加载一次，Hook 热路径不读盘。
 */
public final class LegacyConfig {
    private static volatile Map<String, String> values = Collections.emptyMap();
    private static volatile Map<String, Boolean> enabledFields = Collections.emptyMap();
    private static volatile boolean hookAllApps;
    private static volatile boolean useHardcodedValue;
    private static volatile boolean debugMode;
    private static volatile String packages = "";

    private LegacyConfig() {
    }

    public static synchronized void reload() {
        XSharedPreferences settings = new XSharedPreferences(Constant.PACKAGE_THIS_TOOL, Constant.TAG);
        settings.reload();
        hookAllApps = settings.getBoolean("hook_all_app", false);
        useHardcodedValue = settings.getBoolean("use_hardcoded_value", false);
        debugMode = settings.getBoolean("debug_mode", false);
        packages = settings.getString("packages", "");

        XSharedPreferences profile = new XSharedPreferences(
                Constant.PACKAGE_THIS_TOOL, Constant.FILENAME_FAKEINFO);
        profile.reload();
        LinkedHashMap<String, String> snapshot = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> enabledSnapshot = new LinkedHashMap<>();
        for (String key : PhoneInfo.getHardcodedPhoneInfo(null).keySet()) {
            String storageKey = Constant.PREF_VALUE_PREFIX + key;
            snapshot.put(key, settings.contains(storageKey)
                    ? settings.getString(storageKey, "")
                    : profile.getString(key, ""));
            enabledSnapshot.put(key, settings.getBoolean(key, true));
        }
        values = Collections.unmodifiableMap(snapshot);
        enabledFields = Collections.unmodifiableMap(enabledSnapshot);
    }

    public static String getValue(String key) {
        String value = values.get(key);
        return value == null ? "" : value;
    }

    public static boolean isHookAllApps() {
        return hookAllApps;
    }

    public static boolean isUseHardcodedValue() {
        return useHardcodedValue;
    }

    public static String getPackages() {
        return packages;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static boolean isFieldEnabled(String key) {
        return Boolean.TRUE.equals(enabledFields.get(key));
    }
}
