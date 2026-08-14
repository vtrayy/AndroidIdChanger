package com.bigsing.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.bigsing.changer.App;
import com.bigsing.changer.config.ProfileCodec;
import com.bigsing.changer.config.ProfileSnapshot;
import com.bigsing.changer.config.ScopeMatcher;
import com.bigsing.changer.constant.Constant;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 设备替换配置的本地存储及严格导入导出。 */
public final class PhoneInfoUtils {
    private PhoneInfoUtils() {
    }

    private static void fillPhoneInfo(LinkedHashMap<String, String> map,
                                      SharedPreferences primary, String prefix,
                                      SharedPreferences fallback, Set<String> fields) {
        for (String key : fields) {
            String storageKey = prefix + key;
            if (primary.contains(storageKey)) {
                map.put(key, primary.getString(storageKey, ""));
            } else {
                map.put(key, fallback == null ? "" : fallback.getString(key, ""));
            }
        }
    }

    private static boolean containsKnownField(SharedPreferences preferences,
                                              String prefix, Set<String> fields) {
        for (String key : fields) {
            if (preferences.contains(prefix + key)) {
                return true;
            }
        }
        return false;
    }

    public static LinkedHashMap<String, String> loadPhoneInfoFromXml(
            Context context, String preferenceName) {
        try {
            Set<String> portableFields =
                    PhoneInfo.getPortableProfileFields(context).keySet();
            if (Constant.FILENAME_FAKEINFO.equals(preferenceName)) {
                SharedPreferences settings =
                        App.getSharedPreferences();
                SharedPreferences legacy = App.getFakeSharedPreferences();
                if (legacy == null) {
                    legacy = context.getSharedPreferences(
                            Constant.FILENAME_FAKEINFO, App.getPreferenceMode());
                }
                if (containsKnownField(settings, Constant.PREF_VALUE_PREFIX,
                        portableFields)) {
                    LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    fillPhoneInfo(values, settings, Constant.PREF_VALUE_PREFIX,
                            legacy, portableFields);
                    return values;
                }
                if (containsKnownField(legacy, "", portableFields)) {
                    LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    fillPhoneInfo(values, legacy, "", null, portableFields);
                    return values;
                }
                return null;
            }

            SharedPreferences preferences =
                    context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
            if (containsKnownField(preferences, "", portableFields)) {
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                fillPhoneInfo(values, preferences, "", null, portableFields);
                return values;
            }
        } catch (RuntimeException ignored) {
            // 损坏或类型不匹配的旧配置按“无配置”处理。
        }
        return null;
    }

    public static void savePhoneInfo(Context context, Map<String, String> phoneInfo) {
        savePhoneInfo(context, phoneInfo, null);
    }

    /** 原子保存字段值与可选的启用状态；保存自定义值时自动退出硬编码模式。 */
    public static void savePhoneInfo(Context context, Map<String, String> phoneInfo,
                                     Map<String, Boolean> enabledFields) {
        SharedPreferences preferences = App.getSharedPreferences();
        if (preferences == null) {
            preferences = context.getSharedPreferences(
                    Constant.TAG, App.getPreferenceMode());
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("use_hardcoded_value", false);
        Set<String> allowedFields =
                PhoneInfo.getPortableProfileFields(context).keySet();
        for (String key : PhoneInfo.getHardcodedPhoneInfo(context).keySet()) {
            if (!allowedFields.contains(key)) {
                editor.remove(Constant.PREF_VALUE_PREFIX + key);
            }
        }
        for (String key : allowedFields) {
            if (phoneInfo.containsKey(key)) {
                String value = phoneInfo.get(key);
                editor.putString(Constant.PREF_VALUE_PREFIX + key,
                        value == null ? "" : value);
            }
            if (enabledFields != null && enabledFields.containsKey(key)) {
                boolean enabled = Boolean.TRUE.equals(enabledFields.get(key));
                String value = phoneInfo.get(key);
                if (enabled && isBlank(value)) {
                    throw new IllegalArgumentException(
                            "enabled replacement field has no value: " + key);
                }
                editor.putBoolean(key, enabled);
            }
        }
        if (!editor.commit()) {
            throw new IllegalStateException("unable to save profile");
        }

        // API 17-23 的原版 Xposed 只能读取 world-readable preferences。
        if (Build.VERSION.SDK_INT < 24) {
            File file = new File(context.getApplicationInfo().dataDir,
                    "shared_prefs/" + Constant.TAG + ".xml");
            file.setReadable(true, false);
        }
    }

    /**
     * 导出 UTF-8、版本化、带严格字段白名单的配置；不包含本机真实设备信息。
     */
    public static void exportProfile(Context context, File destination) throws Exception {
        LinkedHashMap<String, String> registry =
                PhoneInfo.getPortableProfileFields(context);
        SharedPreferences settings = App.getSharedPreferences();
        boolean useHardcoded = settings.getBoolean("use_hardcoded_value", false);
        LinkedHashMap<String, String> values =
                loadPhoneInfoFromXml(context, Constant.FILENAME_FAKEINFO);
        if (values == null) {
            values = new LinkedHashMap<>();
        }
        if (useHardcoded) {
            // 导出实际硬编码来源，但保留已有自定义值，便于关闭硬编码后恢复。
            for (Map.Entry<String, String> entry : registry.entrySet()) {
                if (settings.getBoolean(entry.getKey(), true)
                        && isBlank(values.get(entry.getKey()))) {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
        }

        LinkedHashMap<String, Boolean> enabledFields = new LinkedHashMap<>();
        for (String key : registry.keySet()) {
            String value = values.get(key);
            enabledFields.put(key, isReplaceableByAnyVariant(key)
                    && !isBlank(value)
                    && settings.getBoolean(key, true));
        }
        Set<String> scope = new LinkedHashSet<>(
                ScopeMatcher.parse(settings.getString("packages", "")));
        ProfileSnapshot snapshot = new ProfileSnapshot(
                ProfileCodec.CURRENT_VERSION, values, enabledFields, scope,
                settings.getBoolean("hook_all_app", false),
                useHardcoded);
        ProfileCodec codec = ProfileCodec.fromFieldRegistry(registry);

        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            codec.encode(snapshot, output);
        }
    }

    /**
     * 完整校验后再替换当前配置。未知字段、重复字段、非法 UTF-8、超限输入均会失败。
     */
    public static void importProfile(Context context, InputStream source) throws Exception {
        LinkedHashMap<String, String> fullRegistry =
                PhoneInfo.getHardcodedPhoneInfo(context);
        LinkedHashMap<String, String> portableRegistry =
                PhoneInfo.getPortableProfileFields(context);
        // 使用完整旧注册表解码，以便导入历史配置；只把当前可替换字段写入本机配置。
        ProfileSnapshot snapshot = ProfileCodec.fromFieldRegistry(fullRegistry).decode(source);

        SharedPreferences settings = App.getSharedPreferences();
        SharedPreferences.Editor settingsEditor = settings.edit();
        settingsEditor.putBoolean("hook_all_app", snapshot.isScopeAll());
        settingsEditor.putBoolean("use_hardcoded_value", snapshot.isUseHardcoded());
        settingsEditor.putString("packages", snapshot.serializeScope());
        for (String key : fullRegistry.keySet()) {
            if (!portableRegistry.containsKey(key)) {
                settingsEditor.remove(Constant.PREF_VALUE_PREFIX + key);
                settingsEditor.remove(key);
            }
        }
        for (String key : portableRegistry.keySet()) {
            String value = snapshot.getValues().get(key);
            boolean enabled = isReplaceableByAnyVariant(key)
                    && Boolean.TRUE.equals(snapshot.getEnabledFields().get(key));
            if (enabled && isBlank(value)) {
                throw new IllegalArgumentException(
                        "enabled replacement field has no value: " + key);
            }
            settingsEditor.putString(Constant.PREF_VALUE_PREFIX + key,
                    value == null ? "" : value);
            settingsEditor.putBoolean(key, enabled);
        }

        // 所有值、字段开关与作用域通过同一个 SharedPreferences.Editor 原子提交。
        if (!settingsEditor.commit()) {
            throw new IllegalStateException("unable to activate imported profile");
        }

        if (Build.VERSION.SDK_INT < 24) {
            File file = new File(context.getApplicationInfo().dataDir,
                    "shared_prefs/" + Constant.TAG + ".xml");
            file.setReadable(true, false);
        }
    }

    private static boolean isReplaceableByAnyVariant(String key) {
        return PhoneInfo.isLegacySupported(key) || PhoneInfo.isModernSupported(key);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
