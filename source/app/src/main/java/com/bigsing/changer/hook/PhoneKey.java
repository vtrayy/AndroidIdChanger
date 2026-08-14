package com.bigsing.changer.hook;

/** 设备信息和替换配置使用的稳定字段名。 */
public final class PhoneKey {
    public static final String IMEI = "IMEI";
    public static final String MEID = "MEID";
    public static final String AndroidId = "AndroidId";
    public static final String WifiMac = "WifiMac";
    public static final String SerialNo = "SerialNo";
    public static final String Wifissid = "Wifissid";
    public static final String WifiBssid = "WifiBssid";
    public static final String BluetoothMac = "BluetoothMac";
    public static final String NetType = "NetType";
    public static final String FingerPrint = "FingerPrint";
    public static final String Incremental = "Incremental";
    public static final String RadioVersion = "RadioVersion";
    public static final String Model = "Model";
    public static final String Manufacturer = "Manufacturer";
    public static final String Brand = "Brand";
    public static final String Hardware = "Hardware";
    public static final String ReleaseOS = "ReleaseOS";
    public static final String SDK = "SDK";
    public static final String CPU_ABI = "CPU_ABI";
    public static final String CPU_ABI2 = "CPU_ABI2";
    public static final String USER = "USER";
    public static final String BuildID = "BuildID";
    public static final String BootLoader = "BootLoader";
    public static final String TAGS = "TAGS";
    public static final String TIME = "TIME";
    public static final String TYPE = "TYPE";
    public static final String CODENAME = "CODENAME";
    public static final String Board = "Board";
    public static final String Device = "Device";
    public static final String Product = "Product";
    public static final String USBDebugMode = "USBDebugMode";
    public static final String Host = "Host";
    public static final String Display = "Display";
    public static final String Resolution = "Resolution";
    public static final String CPU_NAME = "CpuName";
    public static final String CPU_FREQ = "CpuFreq";
    public static final String IP = "IP";
    public static final String DensityDpi = "DensityDpi";
    public static final String PhoneNumber = "PhoneNumber";
    public static final String SimSerialNo = "SimSerialNo";
    public static final String SubscriberId = "SubscriberId";
    public static final String SimState = "SimState";
    public static final String SimOperator = "SimOperator";
    public static final String SimOperatorName = "SimOperatorName";
    public static final String SimCountryIso = "SimCountryIso";
    public static final String NetworkOperator = "NetworkOperator";
    public static final String NetworkOperatorName = "NetworkOperatorName";
    public static final String NetworkCountryIso = "NetworkCountryIso";

    // 以下字段只用于普通应用查看，不参与 Xposed 替换。
    public static final String SecurityPatch = "SecurityPatch";
    public static final String BaseOS = "BaseOS";
    public static final String SupportedABIs = "SupportedABIs";
    public static final String SocManufacturer = "SocManufacturer";
    public static final String SocModel = "SocModel";
    public static final String SKU = "SKU";
    public static final String OdmSKU = "OdmSKU";
    public static final String TotalMemory = "TotalMemory";
    public static final String AvailableMemory = "AvailableMemory";
    public static final String InternalStorageTotal = "InternalStorageTotal";
    public static final String InternalStorageAvailable = "InternalStorageAvailable";
    public static final String ScreenRefreshRate = "ScreenRefreshRate";
    public static final String BatteryLevel = "BatteryLevel";
    public static final String BatteryStatus = "BatteryStatus";
    public static final String BatteryHealth = "BatteryHealth";
    public static final String BatteryTemperature = "BatteryTemperature";
    public static final String BatteryVoltage = "BatteryVoltage";
    public static final String NetworkMetered = "NetworkMetered";
    public static final String VpnActive = "VpnActive";
    public static final String SensorCount = "SensorCount";
    public static final String CameraCount = "CameraCount";
    public static final String SystemFeatureCount = "SystemFeatureCount";
    public static final String Locale = "Locale";
    public static final String TimeZone = "TimeZone";
    public static final String Uptime = "Uptime";
    public static final String AppVersion = "AppVersion";
    public static final String InstallerPackage = "InstallerPackage";
    public static final String SignatureSha256 = "SignatureSha256";

    private PhoneKey() {
    }
}
