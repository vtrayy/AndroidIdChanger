package com.bigsing.changer.modern;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.bigsing.changer.config.ScopeMatcher;
import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.hook.PhoneKey;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** libxposed API 102 入口。只 Hook 公共方法，不修改 Android 17 禁止写入的静态 final 字段。 */
public final class ModernModule extends XposedModule {
    private static final String TAG = "AndroidIdChanger";
    private static final String PREFERENCE_GROUP = "device_profile";

    private final AtomicBoolean installed = new AtomicBoolean(false);
    private volatile Profile profile = Profile.EMPTY;
    private volatile String activePackage = "";
    private SharedPreferences remotePreferences;

    // RemotePreferences 对监听器使用弱引用，因此必须由模块实例强引用。
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
                    profile = Profile.from(preferences);
                }
            };

    public ModernModule() {
        // 框架状态要等 attach 完成后才能访问。
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "modern module loaded, API=" + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage()
                || Constant.PACKAGE_THIS_TOOL.equals(param.getPackageName())
                || !installed.compareAndSet(false, true)) {
            return;
        }
        activePackage = param.getPackageName();
        initializePreferences();
        installHooks();
    }

    private void initializePreferences() {
        if ((getFrameworkProperties() & PROP_CAP_REMOTE) == 0) {
            return;
        }
        try {
            remotePreferences = getRemotePreferences(PREFERENCE_GROUP);
            profile = Profile.from(remotePreferences);
            remotePreferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        } catch (UnsupportedOperationException exception) {
            log(Log.WARN, TAG, "remote preferences are unavailable", exception);
        }
    }

    private void installHooks() {
        hookAndroidId();
        hookString(TelephonyManager.class, "getDeviceId", PhoneKey.IMEI);
        hookString(TelephonyManager.class, "getDeviceId", PhoneKey.IMEI, int.class);
        hookString(TelephonyManager.class, "getImei", PhoneKey.IMEI);
        hookString(TelephonyManager.class, "getImei", PhoneKey.IMEI, int.class);
        hookString(TelephonyManager.class, "getMeid", PhoneKey.MEID);
        hookString(TelephonyManager.class, "getMeid", PhoneKey.MEID, int.class);
        hookString(TelephonyManager.class, "getLine1Number", PhoneKey.PhoneNumber);
        hookString(TelephonyManager.class, "getSimSerialNumber", PhoneKey.SimSerialNo);
        hookString(TelephonyManager.class, "getSubscriberId", PhoneKey.SubscriberId);
        hookString(TelephonyManager.class, "getSimOperator", PhoneKey.SimOperator);
        hookString(TelephonyManager.class, "getNetworkOperator", PhoneKey.NetworkOperator);
        hookString(TelephonyManager.class, "getSimOperatorName", PhoneKey.SimOperatorName);
        hookString(TelephonyManager.class, "getSimCountryIso", PhoneKey.SimCountryIso);
        hookString(TelephonyManager.class, "getNetworkCountryIso", PhoneKey.NetworkCountryIso);
        hookString(TelephonyManager.class, "getNetworkOperatorName", PhoneKey.NetworkOperatorName);
        hookInteger(TelephonyManager.class, "getSimState", PhoneKey.SimState);
        hookInteger(TelephonyManager.class, "getSimState", PhoneKey.SimState, int.class);
        hookString(WifiInfo.class, "getMacAddress", PhoneKey.WifiMac);
        hookString(WifiInfo.class, "getSSID", PhoneKey.Wifissid);
        hookString(WifiInfo.class, "getBSSID", PhoneKey.WifiBssid);
        hookString(BluetoothAdapter.class, "getAddress", PhoneKey.BluetoothMac);
        hookString(Build.class, "getSerial", PhoneKey.SerialNo);
        hookString(Build.class, "getRadioVersion", PhoneKey.RadioVersion);
    }

    private void hookAndroidId() {
        try {
            Method method = Settings.Secure.class.getDeclaredMethod(
                    "getString", ContentResolver.class, String.class);
            hook(method)
                    .setId("settings.secure.android_id")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            if (!Settings.Secure.ANDROID_ID.equals(chain.getArg(1))) {
                                return chain.proceed();
                            }
                            String replacement = profile.get(PhoneKey.AndroidId, activePackage);
                            return replacement == null ? chain.proceed() : replacement;
                        }
                    });
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "cannot hook Settings.Secure#getString", throwable);
        }
    }

    private void hookString(Class<?> owner, String name, final String key, Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            hook(method)
                    .setId(owner.getName() + "#" + name + parameterSignature(parameters))
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            String replacement = profile.get(key, activePackage);
                            return replacement == null ? chain.proceed() : replacement;
                        }
                    });
        } catch (NoSuchMethodException ignored) {
            // 当前系统没有该 API 或重载时按能力降级。
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "cannot hook " + owner.getName() + "#" + name, throwable);
        }
    }

    private void hookInteger(Class<?> owner, String name, final String key,
                             Class<?>... parameters) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            hook(method)
                    .setId(owner.getName() + "#" + name + parameterSignature(parameters))
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            String replacement = profile.get(key, activePackage);
                            if (replacement == null) {
                                return chain.proceed();
                            }
                            try {
                                return Integer.valueOf(replacement);
                            } catch (NumberFormatException ignored) {
                                return chain.proceed();
                            }
                        }
                    });
        } catch (NoSuchMethodException ignored) {
            // 当前系统没有该 API 或重载时按能力降级。
        } catch (Throwable throwable) {
            log(Log.WARN, TAG, "cannot hook " + owner.getName() + "#" + name, throwable);
        }
    }

    private static String parameterSignature(Class<?>[] parameters) {
        StringBuilder signature = new StringBuilder("(");
        for (Class<?> parameter : parameters) {
            if (signature.length() > 1) {
                signature.append(',');
            }
            signature.append(parameter.getName());
        }
        return signature.append(')').toString();
    }

    private static final class Profile {
        private static final Profile EMPTY = new Profile(
                false, false, Collections.<String>emptySet(), Collections.<String, String>emptyMap(),
                Collections.<String, Boolean>emptyMap());

        private final boolean enabled;
        private final boolean allPackages;
        private final Set<String> packages;
        private final Map<String, String> values;
        private final Map<String, Boolean> enabledFields;

        private Profile(boolean enabled, boolean allPackages, Set<String> packages,
                        Map<String, String> values, Map<String, Boolean> enabledFields) {
            this.enabled = enabled;
            this.allPackages = allPackages;
            this.packages = Collections.unmodifiableSet(new LinkedHashSet<>(packages));
            this.values = Collections.unmodifiableMap(values);
            this.enabledFields = Collections.unmodifiableMap(enabledFields);
        }

        private static Profile from(SharedPreferences preferences) {
            try {
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                LinkedHashMap<String, Boolean> enabledFields = new LinkedHashMap<>();
                for (String key : REPLACEABLE_KEYS) {
                    values.put(key, preferences.getString("value." + key, ""));
                    enabledFields.put(key, preferences.getBoolean("enabled." + key, false));
                }
                return new Profile(
                        preferences.getBoolean("profile.enabled", false),
                        preferences.getBoolean("scope.all", false),
                        ScopeMatcher.parse(preferences.getString("scope.packages", "")),
                        values,
                        enabledFields);
            } catch (ClassCastException ignored) {
                return EMPTY;
            }
        }

        private String get(String key, String packageName) {
            if (!enabled || (!allPackages && !packages.contains(packageName))
                    || !Boolean.TRUE.equals(enabledFields.get(key))) {
                return null;
            }
            String value = values.get(key);
            return value == null || value.trim().length() == 0 ? null : value;
        }
    }

    private static final String[] REPLACEABLE_KEYS = {
            PhoneKey.AndroidId, PhoneKey.IMEI, PhoneKey.MEID, PhoneKey.SerialNo,
            PhoneKey.PhoneNumber, PhoneKey.SimSerialNo, PhoneKey.SubscriberId,
            PhoneKey.SimState, PhoneKey.SimOperator, PhoneKey.SimOperatorName,
            PhoneKey.SimCountryIso, PhoneKey.NetworkOperator, PhoneKey.NetworkOperatorName,
            PhoneKey.NetworkCountryIso, PhoneKey.RadioVersion,
            PhoneKey.WifiMac, PhoneKey.Wifissid,
            PhoneKey.WifiBssid, PhoneKey.BluetoothMac
    };
}
