package com.bigsing.changer.hook;

import android.os.Build;
import android.provider.Settings;

import com.bigsing.changer.legacy.LegacyConfig;
import com.bigsing.util.PhoneInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 传统 Xposed 入口使用的设备信息 Hook。
 *
 * <p>配置只在目标进程启动时读取。安装后的回调仅使用已经捕获的常量，避免在
 * 被 Hook 方法的热路径中访问磁盘。</p>
 */
public final class PhoneHooker {
    private static final int API_N = 24;
    private static final int API_O = 26;

    private static final PhoneInfo HARDCODED_PHONE_INFO = new PhoneInfo();

    private PhoneHooker() {
    }

    public static PhoneInfo getHardcodedPhoneInfo() {
        PhoneInfo.getHardcodedPhoneInfo(null);
        return HARDCODED_PHONE_INFO;
    }

    private static String getHookValue(String key, boolean useHardcodedValues) {
        if (!LegacyConfig.isFieldEnabled(key)) {
            return "";
        }
        String value = useHardcodedValues
                ? HARDCODED_PHONE_INFO.getValue(key)
                : LegacyConfig.getValue(key);
        return value == null ? "" : value;
    }

    public static void hook(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        final boolean useHardcodedValues = LegacyConfig.isUseHardcodedValue();
        if (useHardcodedValues) {
            getHardcodedPhoneInfo();
        }

        final ClassLoader classLoader = loadPackageParam.classLoader;

        hookTelephonyIdentifiers(classLoader, useHardcodedValues);
        hookNetworkAndSim(classLoader, useHardcodedValues);
        hookWirelessIdentifiers(classLoader, useHardcodedValues);
        hookAndroidId(classLoader, getHookValue(PhoneKey.AndroidId, useHardcodedValues));
        hookBuildInformation(classLoader, useHardcodedValues);
    }

    private static void hookTelephonyIdentifiers(
            ClassLoader classLoader, boolean useHardcodedValues) {
        String imei = getHookValue(PhoneKey.IMEI, useHardcodedValues);
        if (!isEmpty(imei)) {
            hookStringMethodsIfPresent(
                    classLoader, "android.telephony.TelephonyManager", "getDeviceId", imei);

            // API 17-era implementations sometimes expose the identifier through internal
            // telephony classes. Each class and method is optional on vendor builds.
            hookStringMethodsIfPresent(
                    classLoader, "com.android.internal.telephony.gsm.GSMPhone", "getDeviceId", imei);
            hookStringMethodsIfPresent(
                    classLoader, "com.android.internal.telephony.cdma.CDMAPhone", "getDeviceId", imei);
            hookStringMethodsIfPresent(
                    classLoader, "com.android.internal.telephony.PhoneSubInfo", "getDeviceId", imei);
            hookStringMethodsIfPresent(
                    classLoader, "com.android.internal.telephony.PhoneProxy", "getDeviceId", imei);

            // The reflection-based installer also picks up the slot overload where it exists,
            // without linking that newer signature while this class loads on API 17.

            // getImei()/getImei(int) were introduced in API 26.
            if (Build.VERSION.SDK_INT >= API_O) {
                hookStringMethodsIfPresent(
                        classLoader, "android.telephony.TelephonyManager", "getImei", imei);
            }
        }

        String meid = getHookValue(PhoneKey.MEID, useHardcodedValues);
        if (!isEmpty(meid) && Build.VERSION.SDK_INT >= API_O) {
            // getMeid()/getMeid(int) were introduced in API 26.
            hookStringMethodsIfPresent(
                    classLoader, "android.telephony.TelephonyManager", "getMeid", meid);
        }

        String serial = getHookValue(PhoneKey.SerialNo, useHardcodedValues);
        if (!isEmpty(serial) && Build.VERSION.SDK_INT >= API_O) {
            // Build.getSerial() was introduced in API 26. Looking it up by name keeps this
            // class loadable on API 17 and also tolerates vendor-removed implementations.
            hookStringMethodsIfPresent(classLoader, "android.os.Build", "getSerial", serial);
        }
    }

    private static void hookNetworkAndSim(
            ClassLoader classLoader, boolean useHardcodedValues) {
        hookTelephonyStringValue(
                classLoader,
                "getLine1Number",
                getHookValue(PhoneKey.PhoneNumber, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getSimSerialNumber",
                getHookValue(PhoneKey.SimSerialNo, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getSubscriberId",
                getHookValue(PhoneKey.SubscriberId, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getSimOperator",
                getHookValue(PhoneKey.SimOperator, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getSimOperatorName",
                getHookValue(PhoneKey.SimOperatorName, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getSimCountryIso",
                getHookValue(PhoneKey.SimCountryIso, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getNetworkOperator",
                getHookValue(PhoneKey.NetworkOperator, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getNetworkOperatorName",
                getHookValue(PhoneKey.NetworkOperatorName, useHardcodedValues));
        hookTelephonyStringValue(
                classLoader,
                "getNetworkCountryIso",
                getHookValue(PhoneKey.NetworkCountryIso, useHardcodedValues));

        Integer simState = parseSimState(
                getHookValue(PhoneKey.SimState, useHardcodedValues));
        if (simState != null) {
            hookIntMethodsIfPresent(
                    classLoader, "android.telephony.TelephonyManager", "getSimState", simState);
        }
    }

    private static void hookTelephonyStringValue(
            ClassLoader classLoader, String methodName, String value) {
        if (!isEmpty(value)) {
            hookStringMethodsIfPresent(
                    classLoader, "android.telephony.TelephonyManager", methodName, value);
        }
    }

    private static void hookWirelessIdentifiers(
            ClassLoader classLoader, boolean useHardcodedValues) {
        String wifiMac = getHookValue(PhoneKey.WifiMac, useHardcodedValues);
        if (!isEmpty(wifiMac)) {
            hookStringMethodsIfPresent(
                    classLoader, "android.net.wifi.WifiInfo", "getMacAddress", wifiMac);

            if (Build.VERSION.SDK_INT >= API_N) {
                // DevicePolicyManager.getWifiMacAddress(ComponentName) was added in API 24.
                // Discovering the real method replaces the former nested Object[] argument bug.
                hookStringMethodsIfPresent(
                        classLoader,
                        "android.app.admin.DevicePolicyManager",
                        "getWifiMacAddress",
                        wifiMac);
            }
        }

        String bluetoothMac = getHookValue(PhoneKey.BluetoothMac, useHardcodedValues);
        if (!isEmpty(bluetoothMac)) {
            hookStringMethodsIfPresent(
                    classLoader,
                    "android.bluetooth.BluetoothAdapter",
                    "getAddress",
                    bluetoothMac);
        }

        String ssid = getHookValue(PhoneKey.Wifissid, useHardcodedValues);
        if (!isEmpty(ssid)) {
            hookStringMethodsIfPresent(classLoader, "android.net.wifi.WifiInfo", "getSSID", ssid);
        }

        String bssid = getHookValue(PhoneKey.WifiBssid, useHardcodedValues);
        if (!isEmpty(bssid)) {
            hookStringMethodsIfPresent(classLoader, "android.net.wifi.WifiInfo", "getBSSID", bssid);
        }
    }

    private static void hookAndroidId(ClassLoader classLoader, final String androidId) {
        if (isEmpty(androidId)) {
            return;
        }

        XC_MethodHook replacement = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args != null
                        && param.args.length > 1
                        && Settings.Secure.ANDROID_ID.equals(param.args[1])) {
                    // Setting the result before invocation skips the original provider lookup.
                    param.setResult(androidId);
                }
            }
        };

        hookMethodsIfPresent(
                classLoader,
                "android.provider.Settings$Secure",
                "getString",
                String.class,
                replacement);
        hookMethodsIfPresent(
                classLoader,
                "android.provider.Settings$Secure",
                "getStringForUser",
                String.class,
                replacement);
    }

    private static void hookBuildInformation(
            ClassLoader classLoader, boolean useHardcodedValues) {
        setStaticFieldBestEffort(
                Build.VERSION.class,
                "RELEASE",
                getHookValue(PhoneKey.ReleaseOS, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.VERSION.class,
                "INCREMENTAL",
                getHookValue(PhoneKey.Incremental, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.VERSION.class,
                "CODENAME",
                getHookValue(PhoneKey.CODENAME, useHardcodedValues));

        String serial = getHookValue(PhoneKey.SerialNo, useHardcodedValues);
        setStaticFieldBestEffort(Build.class, "SERIAL", serial);
        setStaticFieldBestEffort(
                Build.class, "MODEL", getHookValue(PhoneKey.Model, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class,
                "MANUFACTURER",
                getHookValue(PhoneKey.Manufacturer, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "HARDWARE", getHookValue(PhoneKey.Hardware, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "BRAND", getHookValue(PhoneKey.Brand, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "ID", getHookValue(PhoneKey.BuildID, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class,
                "BOOTLOADER",
                getHookValue(PhoneKey.BootLoader, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "TAGS", getHookValue(PhoneKey.TAGS, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "TYPE", getHookValue(PhoneKey.TYPE, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "USER", getHookValue(PhoneKey.USER, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "CPU_ABI", getHookValue(PhoneKey.CPU_ABI, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "CPU_ABI2", getHookValue(PhoneKey.CPU_ABI2, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "BOARD", getHookValue(PhoneKey.Board, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "DEVICE", getHookValue(PhoneKey.Device, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "PRODUCT", getHookValue(PhoneKey.Product, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "HOST", getHookValue(PhoneKey.Host, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class, "DISPLAY", getHookValue(PhoneKey.Display, useHardcodedValues));
        setStaticFieldBestEffort(
                Build.class,
                "FINGERPRINT",
                getHookValue(PhoneKey.FingerPrint, useHardcodedValues));

        Long buildTime = parseLong(getHookValue(PhoneKey.TIME, useHardcodedValues));
        if (buildTime != null) {
            setStaticLongFieldBestEffort(Build.class, "TIME", buildTime);
        }

        String radioVersion = getHookValue(PhoneKey.RadioVersion, useHardcodedValues);
        if (!isEmpty(radioVersion)) {
            hookStringMethodsIfPresent(
                    classLoader, "android.os.Build", "getRadioVersion", radioVersion);
        }
    }

    private static void hookStringMethodsIfPresent(
            ClassLoader classLoader, String className, String methodName, String value) {
        if (isEmpty(value)) {
            return;
        }
        hookMethodsIfPresent(
                classLoader,
                className,
                methodName,
                String.class,
                XC_MethodReplacement.returnConstant(value));
    }

    private static void hookIntMethodsIfPresent(
            ClassLoader classLoader, String className, String methodName, Integer value) {
        hookMethodsIfPresent(
                classLoader,
                className,
                methodName,
                Integer.TYPE,
                XC_MethodReplacement.returnConstant(value));
    }

    /**
     * Hooks every concrete overload with the requested return type. Reflection is deliberate:
     * newer method symbols must never be linked while this class is loaded on Android 4.2.
     */
    private static void hookMethodsIfPresent(
            ClassLoader classLoader,
            String className,
            String methodName,
            Class<?> returnType,
            XC_MethodHook callback) {
        Class<?> targetClass = findClassIfPresent(className, classLoader);
        if (targetClass == null) {
            return;
        }

        Set<Method> installedMethods = new HashSet<>();
        for (Class<?> currentClass = targetClass;
                currentClass != null && currentClass != Object.class;
                currentClass = getSuperclassIfPresent(currentClass)) {
            Method[] methods;
            try {
                methods = currentClass.getDeclaredMethods();
            } catch (Throwable ignored) {
                continue;
            }

            for (Method method : methods) {
                if (!methodName.equals(method.getName())
                        || !returnType.equals(method.getReturnType())
                        || Modifier.isAbstract(method.getModifiers())
                        || !installedMethods.add(method)) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(method, callback);
                } catch (Throwable ignored) {
                    // Missing, hidden or vendor-altered methods are optional capabilities.
                }
            }
        }
    }

    private static Class<?> findClassIfPresent(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable ignored) {
            try {
                return Class.forName(className);
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static Class<?> getSuperclassIfPresent(Class<?> type) {
        try {
            return type.getSuperclass();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Build fields are public static final constants and may be inlined, VM-cached or immutable
     * on newer Android releases. Each write is therefore isolated as best-effort only; failure
     * must not prevent method-based hooks from being installed.
     */
    private static void setStaticFieldBestEffort(Class<?> owner, String fieldName, Object value) {
        if (value == null || (value instanceof String && isEmpty((String) value))) {
            return;
        }
        try {
            XposedHelpers.setStaticObjectField(owner, fieldName, value);
        } catch (Throwable ignored) {
            // Expected on releases that prevent mutation of static final fields.
        }
    }

    private static void setStaticLongFieldBestEffort(
            Class<?> owner, String fieldName, long value) {
        try {
            XposedHelpers.setStaticLongField(owner, fieldName, value);
        } catch (Throwable ignored) {
            // Expected on releases that prevent mutation of static final fields.
        }
    }

    private static Integer parseSimState(String value) {
        if (isEmpty(value)) {
            return null;
        }

        String candidate = value.trim();
        Integer parsed = parseInteger(candidate);
        if (parsed != null) {
            return parsed;
        }

        // Accept legacy display values such as "READY(5)" without coupling to a locale.
        int leftParenthesis = candidate.lastIndexOf('(');
        int rightParenthesis = candidate.lastIndexOf(')');
        if (leftParenthesis >= 0 && rightParenthesis > leftParenthesis + 1) {
            return parseInteger(candidate.substring(leftParenthesis + 1, rightParenthesis).trim());
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseLong(String value) {
        if (isEmpty(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
