package com.bigsing.util;

import android.content.Context;
import android.support.annotation.StringRes;

import com.bigsing.changer.BuildConfig;
import com.bigsing.changer.R;
import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.hook.HardValue;
import com.bigsing.changer.hook.PhoneKey;
import com.bigsing.changer.hook.ValueItem;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 设备字段、界面标签与示例替换值的注册表。 */

public class PhoneInfo {
    private static final int HOOK_NONE = 0;
    private static final int HOOK_LEGACY = 1;
    private static final int HOOK_MODERN = 1 << 1;
    private static final int HOOK_BOTH = HOOK_LEGACY | HOOK_MODERN;

    /**
     * 注册表用单个不可变引用发布，避免后台读取时观察到“清空后尚未填完”的中间状态。
     * ValueItem 只作为注册元数据使用，不会在此处修改。
     */
    private static volatile Registry registry = new Registry();

    private static void add(Registry target, String key, String uiName, String hardValue,
                            int hookSupport) {
        target.items.put(key, new ValueItem(key, uiName, "", hardValue,
                supportsCurrentVariant(hookSupport), false, Constant.COLOR_ORIGINAL));
        target.hookSupport.put(key, hookSupport);
    }

    private static String getUiName(Context context, @StringRes int resId) {
        return context != null ? context.getString(resId) : "";
    }

    /**
     * 是否支持某个设备信息的修改
     *
     * @param key
     * @return
     */
    public static boolean isSupported(String key) {
        ensureInitialized();
        ValueItem item = registry.items.get(key);
        if (item != null) {
            return item.isSupported();
        }

        return false;
    }

    /** 是否能由传统 Xposed 入口替换。 */
    public static boolean isLegacySupported(String key) {
        ensureInitialized();
        Integer support = registry.hookSupport.get(key);
        return support != null && (support & HOOK_LEGACY) != 0;
    }

    /** 是否能由 libxposed API 102 入口替换。 */
    public static boolean isModernSupported(String key) {
        ensureInitialized();
        Integer support = registry.hookSupport.get(key);
        return support != null && (support & HOOK_MODERN) != 0;
    }

    /** 返回当前发行变体可替换的字段，保持界面注册顺序。 */
    public static Set<String> getReplaceableKeys() {
        ensureInitialized();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : registry.hookSupport.entrySet()) {
            if (supportsCurrentVariant(entry.getValue())) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * 初始化字段标签和能力。
     *
     * <p>无 Context 的调用来自随机配置和 Hook 进程，只能执行首次初始化；若 Activity
     * 已经加载过本地化标签，绝不能再用空标签覆盖。非空 Context 可在语言变化后重建标签。
     * 新注册表在完全构建后一次性发布，保证并发读取的一致性。</p>
     */
    public static synchronized void init(Context context) {
        if (context == null && !registry.items.isEmpty()) {
            return;
        }

        Registry next = new Registry();
        add(next, PhoneKey.ReleaseOS, getUiName(context, R.string.ReleaseOS), HardValue.Release,
                HOOK_LEGACY);
        // 修改 SDK_INT 会破坏目标应用自身的版本分支，因此只展示、不替换。
        add(next, PhoneKey.SDK, getUiName(context, R.string.SDK), HardValue.SDK, HOOK_NONE);
        add(next, PhoneKey.IMEI, getUiName(context, R.string.IMEI), HardValue.IMEI, HOOK_BOTH);
        add(next, PhoneKey.MEID, getUiName(context, R.string.MEID), HardValue.MEID, HOOK_BOTH);
        add(next, PhoneKey.AndroidId, getUiName(context, R.string.AndroidId), HardValue.AndroidId, HOOK_BOTH);
        add(next, PhoneKey.WifiMac, getUiName(context, R.string.WifiMac), HardValue.WifiMac, HOOK_BOTH);
        add(next, PhoneKey.SerialNo, getUiName(context, R.string.serialnum), HardValue.SerialNo, HOOK_BOTH);
        add(next, PhoneKey.Wifissid, getUiName(context, R.string.Wifissid), HardValue.Wifissid, HOOK_BOTH);
        add(next, PhoneKey.WifiBssid, getUiName(context, R.string.WifiBssid), HardValue.WifiBssid, HOOK_BOTH);
        add(next, PhoneKey.BluetoothMac, getUiName(context, R.string.BluetoothMac), HardValue.BluetoothMac, HOOK_BOTH);
        add(next, PhoneKey.NetType, getUiName(context, R.string.NetType), HardValue.NetType, HOOK_NONE);
        add(next, PhoneKey.FingerPrint, getUiName(context, R.string.FingerPrint), HardValue.FingerPrint, HOOK_LEGACY);
        add(next, PhoneKey.Incremental, getUiName(context, R.string.Incremental), HardValue.Incremental, HOOK_LEGACY);
        add(next, PhoneKey.RadioVersion, getUiName(context, R.string.RadioVersion),
                HardValue.RadioVersion, HOOK_BOTH);
        add(next, PhoneKey.Model, getUiName(context, R.string.Model), HardValue.Model, HOOK_LEGACY);
        add(next, PhoneKey.Manufacturer, getUiName(context, R.string.manufacture), HardValue.Manufacturer, HOOK_LEGACY);
        add(next, PhoneKey.Brand, getUiName(context, R.string.Brand), HardValue.Brand, HOOK_LEGACY);
        add(next, PhoneKey.Hardware, getUiName(context, R.string.Hardware), HardValue.Hardware, HOOK_LEGACY);
        add(next, PhoneKey.CPU_ABI, getUiName(context, R.string.cpuabi), HardValue.CPU_ABI, HOOK_LEGACY);
        add(next, PhoneKey.CPU_ABI2, getUiName(context, R.string.cpuabi2), HardValue.CPU_ABI2, HOOK_LEGACY);
        add(next, PhoneKey.USER, getUiName(context, R.string.USER), HardValue.USER, HOOK_LEGACY);
        add(next, PhoneKey.BuildID, getUiName(context, R.string.BuildID), HardValue.BuildID, HOOK_LEGACY);
        add(next, PhoneKey.BootLoader, getUiName(context, R.string.BootLoader), HardValue.BootLoader, HOOK_LEGACY);
        add(next, PhoneKey.TAGS, getUiName(context, R.string.TAGS), HardValue.TAGS, HOOK_LEGACY);
        add(next, PhoneKey.TIME, getUiName(context, R.string.TIME), HardValue.TIME, HOOK_LEGACY);
        add(next, PhoneKey.TYPE, getUiName(context, R.string.TYPE), HardValue.TYPE, HOOK_LEGACY);
        add(next, PhoneKey.CODENAME, getUiName(context, R.string.CODENAME), HardValue.CODENAME, HOOK_LEGACY);
        add(next, PhoneKey.Board, getUiName(context, R.string.Board), HardValue.Board, HOOK_LEGACY);
        add(next, PhoneKey.Device, getUiName(context, R.string.Device), HardValue.Device, HOOK_LEGACY);
        add(next, PhoneKey.Product, getUiName(context, R.string.Product), HardValue.Product, HOOK_LEGACY);
        add(next, PhoneKey.USBDebugMode, getUiName(context, R.string.USBDebugMode), HardValue.USBDebugMode, HOOK_NONE);
        add(next, PhoneKey.Host, getUiName(context, R.string.Host), HardValue.Host, HOOK_LEGACY);
        add(next, PhoneKey.Display, getUiName(context, R.string.Display), HardValue.Display, HOOK_LEGACY);
        add(next, PhoneKey.Resolution, getUiName(context, R.string.Resolution), HardValue.Resolution, HOOK_NONE);
        add(next, PhoneKey.CPU_NAME, getUiName(context, R.string.CPU_NAME), HardValue.CPU_NAME, HOOK_NONE);
        add(next, PhoneKey.CPU_FREQ, getUiName(context, R.string.CPU_FREQ), HardValue.CPU_FREQ, HOOK_NONE);
        add(next, PhoneKey.IP, getUiName(context, R.string.IP), HardValue.IP, HOOK_NONE);
        add(next, PhoneKey.DensityDpi, getUiName(context, R.string.DensityDpi), HardValue.DensityDpi, HOOK_NONE);
        add(next, PhoneKey.PhoneNumber, getUiName(context, R.string.PhoneNumber), HardValue.PhoneNumber, HOOK_BOTH);
        add(next, PhoneKey.SimSerialNo, getUiName(context, R.string.SimSerialNo), HardValue.SimSerialNo, HOOK_BOTH);

        add(next, PhoneKey.SubscriberId, getUiName(context, R.string.imsi), HardValue.SubscriberId, HOOK_BOTH);
        add(next, PhoneKey.SimState, getUiName(context, R.string.SimState),
                HardValue.SimState, HOOK_BOTH);
        add(next, PhoneKey.SimOperator, getUiName(context, R.string.SimOperator), HardValue.SimOperator, HOOK_BOTH);
        add(next, PhoneKey.SimOperatorName, getUiName(context, R.string.SimOperatorName), HardValue.SimOperatorName, HOOK_BOTH);
        add(next, PhoneKey.SimCountryIso, getUiName(context, R.string.SimCountryIso), HardValue.SimCountryIso, HOOK_BOTH);
        add(next, PhoneKey.NetworkOperator, getUiName(context, R.string.NetworkOperator),
                HardValue.NetworkOperator, HOOK_BOTH);
        add(next, PhoneKey.NetworkOperatorName, getUiName(context, R.string.NetworkOperatorName),
                HardValue.NetworkOperatorName, HOOK_BOTH);
        add(next, PhoneKey.NetworkCountryIso, getUiName(context, R.string.NetworkCountryIso),
                HardValue.NetworkCountryIso, HOOK_BOTH);

        addViewOnly(next, PhoneKey.SecurityPatch, getUiName(context, R.string.SecurityPatch));
        addViewOnly(next, PhoneKey.BaseOS, getUiName(context, R.string.BaseOS));
        addViewOnly(next, PhoneKey.SupportedABIs, getUiName(context, R.string.SupportedABIs));
        addViewOnly(next, PhoneKey.SocManufacturer, getUiName(context, R.string.SocManufacturer));
        addViewOnly(next, PhoneKey.SocModel, getUiName(context, R.string.SocModel));
        addViewOnly(next, PhoneKey.SKU, getUiName(context, R.string.SKU));
        addViewOnly(next, PhoneKey.OdmSKU, getUiName(context, R.string.OdmSKU));
        addViewOnly(next, PhoneKey.TotalMemory, getUiName(context, R.string.TotalMemory));
        addViewOnly(next, PhoneKey.AvailableMemory, getUiName(context, R.string.AvailableMemory));
        addViewOnly(next, PhoneKey.InternalStorageTotal, getUiName(context, R.string.InternalStorageTotal));
        addViewOnly(next, PhoneKey.InternalStorageAvailable, getUiName(context, R.string.InternalStorageAvailable));
        addViewOnly(next, PhoneKey.ScreenRefreshRate, getUiName(context, R.string.ScreenRefreshRate));
        addViewOnly(next, PhoneKey.BatteryLevel, getUiName(context, R.string.BatteryLevel));
        addViewOnly(next, PhoneKey.BatteryStatus, getUiName(context, R.string.BatteryStatus));
        addViewOnly(next, PhoneKey.BatteryHealth, getUiName(context, R.string.BatteryHealth));
        addViewOnly(next, PhoneKey.BatteryTemperature, getUiName(context, R.string.BatteryTemperature));
        addViewOnly(next, PhoneKey.BatteryVoltage, getUiName(context, R.string.BatteryVoltage));
        addViewOnly(next, PhoneKey.NetworkMetered, getUiName(context, R.string.NetworkMetered));
        addViewOnly(next, PhoneKey.VpnActive, getUiName(context, R.string.VpnActive));
        addViewOnly(next, PhoneKey.SensorCount, getUiName(context, R.string.SensorCount));
        addViewOnly(next, PhoneKey.CameraCount, getUiName(context, R.string.CameraCount));
        addViewOnly(next, PhoneKey.SystemFeatureCount, getUiName(context, R.string.SystemFeatureCount));
        addViewOnly(next, PhoneKey.Locale, getUiName(context, R.string.Locale));
        addViewOnly(next, PhoneKey.TimeZone, getUiName(context, R.string.TimeZone));
        addViewOnly(next, PhoneKey.Uptime, getUiName(context, R.string.Uptime));
        addViewOnly(next, PhoneKey.AppVersion, getUiName(context, R.string.AppVersion));
        addViewOnly(next, PhoneKey.InstallerPackage, getUiName(context, R.string.InstallerPackage));
        addViewOnly(next, PhoneKey.SignatureSha256, getUiName(context, R.string.SignatureSha256));

        registry = next;
    }

    private static void addViewOnly(Registry target, String key, String uiName) {
        add(target, key, uiName, "", HOOK_NONE);
    }

    public static LinkedHashMap<String, String> getHardcodedPhoneInfo(Context context) {
        init(context);

        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        for (Map.Entry<String, ValueItem> entry : registry.items.entrySet()) {
            info.put(entry.getKey(), entry.getValue().getHardValue());
        }

        return info;
    }

    /** 返回可替换字段及其安全示例值，不包含只读展示字段。 */
    public static LinkedHashMap<String, String> getReplaceablePhoneInfo(Context context) {
        init(context);

        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        Registry current = registry;
        for (Map.Entry<String, ValueItem> entry : current.items.entrySet()) {
            Integer support = current.hookSupport.get(entry.getKey());
            if (support != null && supportsCurrentVariant(support)) {
                info.put(entry.getKey(), entry.getValue().getHardValue());
            }
        }
        return info;
    }

    /**
     * 返回任一发行变体可以替换的字段，供持久化和可移植配置使用。
     *
     * <p>这里刻意排除只读设备信息，避免旧版本遗留值进入配置文件或被导出。</p>
     */
    public static LinkedHashMap<String, String> getPortableProfileFields(Context context) {
        init(context);

        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        Registry current = registry;
        for (Map.Entry<String, ValueItem> entry : current.items.entrySet()) {
            Integer support = current.hookSupport.get(entry.getKey());
            if (support != null && support != HOOK_NONE) {
                info.put(entry.getKey(), entry.getValue().getHardValue());
            }
        }
        return info;
    }

    /**
     * 合成界面快照：可替换字段严格保留配置值（包括空串），只读字段始终取本机值。
     */
    public static LinkedHashMap<String, String> mergeForDisplay(
            Map<String, String> originalValues, Map<String, String> replacementValues) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (originalValues != null) {
            merged.putAll(originalValues);
        }
        // 首次配置或旧配置缺字段时，替换项必须显示为空；绝不能拿本机真实标识补位。
        for (String key : getReplaceableKeys()) {
            merged.put(key, "");
        }
        if (replacementValues != null) {
            for (Map.Entry<String, String> entry : replacementValues.entrySet()) {
                if (isSupported(entry.getKey()) || !merged.containsKey(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
                }
            }
        }
        return merged;
    }

    public String getUIName(String key) {
        ensureInitialized();
        ValueItem item = registry.items.get(key);
        if (item != null) {
            return item.getUiName();
        }

        return "";
    }

    public String getValue(String key) {
        ensureInitialized();
        ValueItem item = registry.items.get(key);
        if (item != null) {
            return item.getHardValue();
        }

        return "";
    }

    private static void ensureInitialized() {
        if (registry.items.isEmpty()) {
            init(null);
        }
    }

    private static boolean supportsCurrentVariant(int support) {
        int required = "modern".equals(BuildConfig.XPOSED_VARIANT)
                ? HOOK_MODERN : HOOK_LEGACY;
        return (support & required) != 0;
    }

    private static final class Registry {
        private final LinkedHashMap<String, ValueItem> items = new LinkedHashMap<>();
        private final LinkedHashMap<String, Integer> hookSupport = new LinkedHashMap<>();
    }
}
