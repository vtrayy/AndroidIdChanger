package com.bigsing.changer;

import com.bigsing.changer.config.IdentifierGenerator;
import com.bigsing.changer.hook.PhoneKey;
import com.bigsing.util.PhoneInfo;

import java.util.LinkedHashMap;
import java.util.Locale;

/** 生成明确属于测试用途、格式有效且不复用真实设备标识的配置。 */
public final class RandomInfo {
    private RandomInfo() {
    }

    public static LinkedHashMap<String, String> randomPhoneInfo() {
        IdentifierGenerator generator = new IdentifierGenerator();
        LinkedHashMap<String, String> profile =
                new LinkedHashMap<>(PhoneInfo.getReplaceablePhoneInfo(null));

        String androidId = generator.generateAndroidId();
        String shortToken = androidId.substring(0, 6);
        String upperToken = shortToken.toUpperCase(Locale.US);
        String model = "Test Device " + upperToken;
        String buildId = "TEST." + androidId.substring(4, 10).toUpperCase(Locale.US);
        String incremental = "20260101." + androidId.substring(10).toUpperCase(Locale.US);
        String device = "test-" + shortToken;
        String product = "test-product-" + shortToken;

        profile.put(PhoneKey.ReleaseOS, "13");
        profile.put(PhoneKey.IMEI, generator.generateImei());
        profile.put(PhoneKey.MEID, generator.generateTestMeid());
        profile.put(PhoneKey.AndroidId, androidId);
        profile.put(PhoneKey.WifiMac, generator.generateMacAddress());
        profile.put(PhoneKey.WifiBssid, generator.generateMacAddress());
        profile.put(PhoneKey.BluetoothMac, generator.generateMacAddress());
        profile.put(PhoneKey.Wifissid, "AndroidIdChanger-Test-" + shortToken);
        profile.put(PhoneKey.SerialNo, generator.generateTestSerialNumber());

        profile.put(PhoneKey.Model, model);
        profile.put(PhoneKey.Manufacturer, "Example");
        profile.put(PhoneKey.Brand, "Example");
        profile.put(PhoneKey.Hardware, "test-hw-" + shortToken);
        profile.put(PhoneKey.CPU_ABI, "arm64-v8a");
        profile.put(PhoneKey.CPU_ABI2, "armeabi-v7a");
        profile.put(PhoneKey.USER, "test-builder");
        profile.put(PhoneKey.BuildID, buildId);
        profile.put(PhoneKey.BootLoader, "TEST-BL-" + upperToken);
        profile.put(PhoneKey.TAGS, "release-keys");
        profile.put(PhoneKey.TIME, generator.generateTestBuildTimestamp());
        profile.put(PhoneKey.TYPE, "user");
        profile.put(PhoneKey.CODENAME, "REL");
        profile.put(PhoneKey.Board, "test-board-" + shortToken);
        profile.put(PhoneKey.Device, device);
        profile.put(PhoneKey.Product, product);
        profile.put(PhoneKey.Host, "test-build-host");
        profile.put(PhoneKey.Display, buildId);
        profile.put(PhoneKey.Incremental, incremental);
        profile.put(PhoneKey.RadioVersion, "TEST-RADIO-" + upperToken);
        profile.put(PhoneKey.FingerPrint,
                "example/" + product + "/" + device + ":13/" + buildId + "/" + incremental
                        + ":user/release-keys");

        profile.put(PhoneKey.PhoneNumber, generator.generateTestPhoneNumber());
        profile.put(PhoneKey.SimSerialNo, generator.generateTestIccid());
        profile.put(PhoneKey.SubscriberId, generator.generateTestImsi());
        profile.put(PhoneKey.SimState, "5");
        profile.put(PhoneKey.SimOperator, generator.getTestOperatorNumeric());
        profile.put(PhoneKey.SimOperatorName, "AndroidIdChanger Test Network");
        profile.put(PhoneKey.SimCountryIso, "zz");
        profile.put(PhoneKey.NetworkOperator, generator.getTestOperatorNumeric());
        profile.put(PhoneKey.NetworkOperatorName, "AndroidIdChanger Test Network");
        profile.put(PhoneKey.NetworkCountryIso, "zz");
        // 上面的赋值同时构造两种入口需要的数据，返回前按当前 APK 能力收口。
        profile.keySet().retainAll(PhoneInfo.getReplaceableKeys());
        return profile;
    }
}
