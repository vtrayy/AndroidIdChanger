package com.bigsing.changer;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.bigsing.changer.hook.PhoneKey;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 仅使用公开 Android API 的设备信息采集器。
 *
 * <p>受权限或系统隐私策略保护的值会返回明确状态，不会用其他标识符冒充。</p>
 */
public final class DeviceInfo {
    private static final String PERMISSION_DENIED = "[权限未授予]";
    private static final String REDACTED = "[系统已脱敏]";
    private static final String RESTRICTED = "[系统已限制]";
    private static final String UNSUPPORTED = "[系统或硬件不支持]";
    private static final String UNAVAILABLE = "[不可用]";

    private LinkedHashMap<String, String> values;

    public static boolean hasSDCard() {
        try {
            return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public LinkedHashMap<String, String> getValues(Context sourceContext) {
        if (values == null) {
            collect(sourceContext.getApplicationContext());
        }
        return new LinkedHashMap<>(values);
    }

    private void collect(Context context) {
        values = new LinkedHashMap<>();
        collectBuild();
        collectDisplay(context);
        collectMemoryAndStorage(context);
        collectBattery(context);
        collectNetwork(context);
        collectSensitiveIdentifiers(context);
        collectHardwareInventory(context);
        collectAppEnvironment(context);
    }

    private void collectBuild() {
        put(PhoneKey.ReleaseOS, Build.VERSION.RELEASE);
        put(PhoneKey.SDK, String.valueOf(Build.VERSION.SDK_INT));
        put(PhoneKey.CODENAME, Build.VERSION.CODENAME);
        put(PhoneKey.FingerPrint, Build.FINGERPRINT);
        put(PhoneKey.Incremental, Build.VERSION.INCREMENTAL);
        put(PhoneKey.RadioVersion, Build.getRadioVersion());
        put(PhoneKey.Model, Build.MODEL);
        put(PhoneKey.Manufacturer, Build.MANUFACTURER);
        put(PhoneKey.Brand, Build.BRAND);
        put(PhoneKey.Hardware, Build.HARDWARE);
        put(PhoneKey.USER, Build.USER);
        put(PhoneKey.BuildID, Build.ID);
        put(PhoneKey.BootLoader, Build.BOOTLOADER);
        put(PhoneKey.TAGS, Build.TAGS);
        put(PhoneKey.TIME, String.valueOf(Build.TIME));
        put(PhoneKey.TYPE, Build.TYPE);
        put(PhoneKey.Board, Build.BOARD);
        put(PhoneKey.Device, Build.DEVICE);
        put(PhoneKey.Product, Build.PRODUCT);
        put(PhoneKey.Host, Build.HOST);
        put(PhoneKey.Display, Build.DISPLAY);

        if (Build.VERSION.SDK_INT >= 21) {
            put(PhoneKey.SupportedABIs, Arrays.toString(Build.SUPPORTED_ABIS));
            put(PhoneKey.CPU_ABI, firstOrUnavailable(Build.SUPPORTED_ABIS));
            put(PhoneKey.CPU_ABI2, secondOrUnavailable(Build.SUPPORTED_ABIS));
        } else {
            put(PhoneKey.SupportedABIs, Build.CPU_ABI + ", " + Build.CPU_ABI2);
            put(PhoneKey.CPU_ABI, Build.CPU_ABI);
            put(PhoneKey.CPU_ABI2, Build.CPU_ABI2);
        }

        if (Build.VERSION.SDK_INT >= 23) {
            put(PhoneKey.SecurityPatch, Build.VERSION.SECURITY_PATCH);
            put(PhoneKey.BaseOS, Build.VERSION.BASE_OS);
        } else {
            put(PhoneKey.SecurityPatch, UNSUPPORTED);
            put(PhoneKey.BaseOS, UNSUPPORTED);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            put(PhoneKey.SocManufacturer, Build.SOC_MANUFACTURER);
            put(PhoneKey.SocModel, Build.SOC_MODEL);
            put(PhoneKey.SKU, Build.SKU);
            put(PhoneKey.OdmSKU, Build.ODM_SKU);
        } else {
            put(PhoneKey.SocManufacturer, UNSUPPORTED);
            put(PhoneKey.SocModel, UNSUPPORTED);
            put(PhoneKey.SKU, UNSUPPORTED);
            put(PhoneKey.OdmSKU, UNSUPPORTED);
        }
    }

    private void collectDisplay(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        put(PhoneKey.Resolution, metrics.widthPixels + " x " + metrics.heightPixels);
        put(PhoneKey.DensityDpi, String.valueOf(metrics.densityDpi));

        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager == null ? null : manager.getDefaultDisplay();
        put(PhoneKey.ScreenRefreshRate,
                display == null ? UNAVAILABLE : String.format(Locale.US, "%.2f Hz", display.getRefreshRate()));
    }

    private void collectMemoryAndStorage(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (manager != null) {
            manager.getMemoryInfo(memoryInfo);
            put(PhoneKey.TotalMemory, formatBytes(memoryInfo.totalMem));
            put(PhoneKey.AvailableMemory, formatBytes(memoryInfo.availMem));
        } else {
            put(PhoneKey.TotalMemory, UNAVAILABLE);
            put(PhoneKey.AvailableMemory, UNAVAILABLE);
        }

        StatFs statFs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long blockSize;
        long totalBlocks;
        long availableBlocks;
        if (Build.VERSION.SDK_INT >= 18) {
            blockSize = statFs.getBlockSizeLong();
            totalBlocks = statFs.getBlockCountLong();
            availableBlocks = statFs.getAvailableBlocksLong();
        } else {
            blockSize = statFs.getBlockSize();
            totalBlocks = statFs.getBlockCount();
            availableBlocks = statFs.getAvailableBlocks();
        }
        put(PhoneKey.InternalStorageTotal, formatBytes(blockSize * totalBlocks));
        put(PhoneKey.InternalStorageAvailable, formatBytes(blockSize * availableBlocks));
        put(PhoneKey.CPU_NAME, readCpuName());
        put(PhoneKey.CPU_FREQ, Runtime.getRuntime().availableProcessors() + " cores");
        put(PhoneKey.Uptime, formatDuration(SystemClock.elapsedRealtime()));
    }

    private void collectBattery(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            put(PhoneKey.BatteryLevel, UNAVAILABLE);
            put(PhoneKey.BatteryStatus, UNAVAILABLE);
            put(PhoneKey.BatteryHealth, UNAVAILABLE);
            put(PhoneKey.BatteryTemperature, UNAVAILABLE);
            put(PhoneKey.BatteryVoltage, UNAVAILABLE);
            return;
        }

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int percent = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
        put(PhoneKey.BatteryLevel, percent < 0 ? UNAVAILABLE : percent + "%");
        put(PhoneKey.BatteryStatus, batteryStatus(battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)));
        put(PhoneKey.BatteryHealth, batteryHealth(battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)));

        int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        put(PhoneKey.BatteryTemperature, temperature == Integer.MIN_VALUE
                ? UNAVAILABLE : String.format(Locale.US, "%.1f °C", temperature / 10f));
        int voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        put(PhoneKey.BatteryVoltage, voltage < 0 ? UNAVAILABLE : voltage + " mV");
    }

    @SuppressLint("MissingPermission")
    private void collectNetwork(Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo active = connectivity == null ? null : connectivity.getActiveNetworkInfo();
        put(PhoneKey.NetType, active == null ? "none" : active.getTypeName());
        put(PhoneKey.NetworkMetered,
                connectivity == null ? UNAVAILABLE : String.valueOf(connectivity.isActiveNetworkMetered()));
        put(PhoneKey.VpnActive, isVpnActive(connectivity));

        Context applicationContext = context.getApplicationContext();
        WifiManager wifi = (WifiManager) (applicationContext == null ? context : applicationContext)
                .getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = null;
        try {
            info = wifi == null ? null : wifi.getConnectionInfo();
        } catch (SecurityException ignored) {
            // Android 会根据定位与附近设备权限限制 Wi-Fi 信息。
        }
        if (info == null) {
            put(PhoneKey.WifiMac, PERMISSION_DENIED);
            put(PhoneKey.Wifissid, PERMISSION_DENIED);
            put(PhoneKey.WifiBssid, PERMISSION_DENIED);
            put(PhoneKey.IP, UNAVAILABLE);
        } else {
            put(PhoneKey.WifiMac, redactedMac(info.getMacAddress()));
            put(PhoneKey.Wifissid, redactedSsid(info.getSSID()));
            put(PhoneKey.WifiBssid, redactedMac(info.getBSSID()));
            put(PhoneKey.IP, formatIpv4(info.getIpAddress()));
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            put(PhoneKey.BluetoothMac, UNSUPPORTED);
        } else {
            try {
                put(PhoneKey.BluetoothMac, redactedMac(adapter.getAddress()));
            } catch (SecurityException ignored) {
                put(PhoneKey.BluetoothMac, PERMISSION_DENIED);
            }
        }
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private void collectSensitiveIdentifiers(Context context) {
        put(PhoneKey.AndroidId,
                valueOrUnavailable(Settings.Secure.getString(
                        context.getContentResolver(), Settings.Secure.ANDROID_ID)));
        // ADB_ENABLED 对普通第三方应用固定返回 0，不能把受限结果误报为“已关闭”。
        put(PhoneKey.USBDebugMode, RESTRICTED);

        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephony == null) {
            putTelephonyUnavailable();
            return;
        }

        try {
            String identifier = Build.VERSION.SDK_INT >= 26
                    ? telephony.getImei() : telephony.getDeviceId();
            put(PhoneKey.IMEI, restrictedIdentifier(identifier));
        } catch (SecurityException ignored) {
            put(PhoneKey.IMEI, PERMISSION_DENIED);
        } catch (RuntimeException ignored) {
            put(PhoneKey.IMEI, UNSUPPORTED);
        }

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                put(PhoneKey.MEID, restrictedIdentifier(telephony.getMeid()));
            } catch (SecurityException ignored) {
                put(PhoneKey.MEID, PERMISSION_DENIED);
            } catch (RuntimeException ignored) {
                put(PhoneKey.MEID, UNSUPPORTED);
            }
        } else {
            put(PhoneKey.MEID, UNSUPPORTED);
        }

        try {
            String serial = Build.VERSION.SDK_INT >= 26 ? Build.getSerial() : Build.SERIAL;
            put(PhoneKey.SerialNo, restrictedIdentifier(serial));
        } catch (SecurityException ignored) {
            put(PhoneKey.SerialNo, PERMISSION_DENIED);
        } catch (RuntimeException ignored) {
            put(PhoneKey.SerialNo, UNSUPPORTED);
        }

        putSensitive(PhoneKey.SubscriberId, new SensitiveReader() {
            @Override
            public String read() {
                return telephony.getSubscriberId();
            }
        });
        putSensitive(PhoneKey.PhoneNumber, new SensitiveReader() {
            @Override
            public String read() {
                return telephony.getLine1Number();
            }
        });
        putSensitive(PhoneKey.SimSerialNo, new SensitiveReader() {
            @Override
            public String read() {
                return telephony.getSimSerialNumber();
            }
        });

        put(PhoneKey.SimState, String.valueOf(telephony.getSimState()));
        put(PhoneKey.SimOperator, valueOrUnavailable(telephony.getSimOperator()));
        put(PhoneKey.SimOperatorName, valueOrUnavailable(telephony.getSimOperatorName()));
        put(PhoneKey.SimCountryIso, valueOrUnavailable(telephony.getSimCountryIso()));
        put(PhoneKey.NetworkOperator, valueOrUnavailable(telephony.getNetworkOperator()));
        put(PhoneKey.NetworkOperatorName, valueOrUnavailable(telephony.getNetworkOperatorName()));
        put(PhoneKey.NetworkCountryIso, valueOrUnavailable(telephony.getNetworkCountryIso()));
    }

    private void collectHardwareInventory(Context context) {
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> allSensors = sensors == null ? null : sensors.getSensorList(Sensor.TYPE_ALL);
        put(PhoneKey.SensorCount, allSensors == null ? UNAVAILABLE : String.valueOf(allSensors.size()));

        try {
            put(PhoneKey.CameraCount, String.valueOf(Camera.getNumberOfCameras()));
        } catch (RuntimeException ignored) {
            put(PhoneKey.CameraCount, UNAVAILABLE);
        }
        FeatureInfo[] features = context.getPackageManager().getSystemAvailableFeatures();
        put(PhoneKey.SystemFeatureCount, String.valueOf(features == null ? 0 : features.length));
    }

    @SuppressWarnings("deprecation")
    private void collectAppEnvironment(Context context) {
        put(PhoneKey.Locale, Locale.getDefault().toString());
        put(PhoneKey.TimeZone, TimeZone.getDefault().getID());
        put(PhoneKey.AppVersion, BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        PackageManager manager = context.getPackageManager();
        try {
            String installer = manager.getInstallerPackageName(context.getPackageName());
            put(PhoneKey.InstallerPackage, valueOrUnavailable(installer));
        } catch (RuntimeException ignored) {
            put(PhoneKey.InstallerPackage, UNAVAILABLE);
        }

        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            Signature[] signatures = info.signatures;
            put(PhoneKey.SignatureSha256,
                    signatures == null || signatures.length == 0
                            ? UNAVAILABLE : sha256(signatures[0].toByteArray()));
        } catch (Exception ignored) {
            put(PhoneKey.SignatureSha256, UNAVAILABLE);
        }
    }

    private void putTelephonyUnavailable() {
        put(PhoneKey.IMEI, UNSUPPORTED);
        put(PhoneKey.MEID, UNSUPPORTED);
        put(PhoneKey.SerialNo, UNSUPPORTED);
        put(PhoneKey.SubscriberId, UNSUPPORTED);
        put(PhoneKey.PhoneNumber, UNSUPPORTED);
        put(PhoneKey.SimSerialNo, UNSUPPORTED);
        put(PhoneKey.SimState, UNSUPPORTED);
        put(PhoneKey.SimOperator, UNSUPPORTED);
        put(PhoneKey.SimOperatorName, UNSUPPORTED);
        put(PhoneKey.SimCountryIso, UNSUPPORTED);
        put(PhoneKey.NetworkOperator, UNSUPPORTED);
        put(PhoneKey.NetworkOperatorName, UNSUPPORTED);
        put(PhoneKey.NetworkCountryIso, UNSUPPORTED);
    }

    private void putSensitive(String key, SensitiveReader reader) {
        try {
            put(key, restrictedIdentifier(reader.read()));
        } catch (SecurityException ignored) {
            put(key, PERMISSION_DENIED);
        } catch (RuntimeException ignored) {
            put(key, UNSUPPORTED);
        }
    }

    private void put(String key, String value) {
        values.put(key, valueOrUnavailable(value));
    }

    private static String valueOrUnavailable(String value) {
        return TextUtils.isEmpty(value) ? UNAVAILABLE : value;
    }

    private static String restrictedIdentifier(String value) {
        if (TextUtils.isEmpty(value) || Build.UNKNOWN.equalsIgnoreCase(value)) {
            return REDACTED;
        }
        return value;
    }

    private static String redactedMac(String value) {
        if (TextUtils.isEmpty(value) || "02:00:00:00:00:00".equalsIgnoreCase(value)) {
            return REDACTED;
        }
        return value.toUpperCase(Locale.US);
    }

    private static String redactedSsid(String value) {
        if (TextUtils.isEmpty(value) || "<unknown ssid>".equalsIgnoreCase(value)) {
            return REDACTED;
        }
        return value;
    }

    private static String formatIpv4(int address) {
        if (address == 0) {
            return UNAVAILABLE;
        }
        return (address & 0xff) + "." + ((address >>> 8) & 0xff) + "."
                + ((address >>> 16) & 0xff) + "." + ((address >>> 24) & 0xff);
    }

    private static String isVpnActive(ConnectivityManager manager) {
        if (manager == null) {
            return UNAVAILABLE;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                for (Network network : manager.getAllNetworks()) {
                    NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                    if (capabilities != null
                            && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return "true";
                    }
                }
                return "false";
            } catch (SecurityException ignored) {
                return PERMISSION_DENIED;
            }
        }
        NetworkInfo vpn = manager.getNetworkInfo(17);
        return String.valueOf(vpn != null && vpn.isConnected());
    }

    private static String readCpuName() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf(':');
                if (separator > 0 && (line.startsWith("Hardware")
                        || line.startsWith("model name") || line.startsWith("Processor"))) {
                    return line.substring(separator + 1).trim();
                }
            }
        } catch (Exception ignored) {
            // 某些设备会限制 /proc/cpuinfo。
        }
        return UNAVAILABLE;
    }

    private static String firstOrUnavailable(String[] values) {
        return values.length == 0 ? UNAVAILABLE : values[0];
    }

    private static String secondOrUnavailable(String[] values) {
        return values.length < 2 ? UNAVAILABLE : values[1];
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return UNAVAILABLE;
        }
        double gib = bytes / (1024d * 1024d * 1024d);
        if (gib >= 1d) {
            return String.format(Locale.US, "%.2f GiB", gib);
        }
        return String.format(Locale.US, "%.2f MiB", bytes / (1024d * 1024d));
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        return days + "d " + hours + "h " + minutes + "m";
    }

    private static String batteryStatus(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "not_charging";
            default:
                return UNAVAILABLE;
        }
    }

    private static String batteryHealth(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "good";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "cold";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "dead";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "overheat";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "failure";
            default:
                return UNAVAILABLE;
        }
    }

    private static String sha256(byte[] input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
        StringBuilder output = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                output.append(':');
            }
            output.append(String.format(Locale.US, "%02X", digest[i] & 0xff));
        }
        return output.toString();
    }

    private interface SensitiveReader {
        String read();
    }
}
