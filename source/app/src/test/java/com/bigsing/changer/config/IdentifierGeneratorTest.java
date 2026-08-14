package com.bigsing.changer.config;

import com.bigsing.changer.BuildConfig;
import com.bigsing.changer.RandomInfo;
import com.bigsing.changer.hook.PhoneKey;
import com.bigsing.util.PhoneInfo;

import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IdentifierGeneratorTest {
    private final IdentifierGenerator generator = new IdentifierGenerator();

    @Test
    public void imeiHasTestTacAndValidLuhnDigit() {
        for (int i = 0; i < 200; i++) {
            String imei = generator.generateImei();
            assertTrue(imei.matches("00440000\\d{7}"));
            assertTrue(hasValidLuhnCheckDigit(imei));
        }
    }

    @Test
    public void androidIdUsesExactlySixteenLowercaseHexDigits() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String androidId = generator.generateAndroidId();
            assertTrue(androidId.matches("[0-9a-f]{16}"));
            generated.add(androidId);
        }
        assertTrue(generated.size() > 90);
    }

    @Test
    public void macAddressIsLocallyAdministeredUnicast() {
        for (int i = 0; i < 200; i++) {
            String mac = generator.generateMacAddress();
            assertTrue(mac.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}"));
            int firstOctet = Integer.parseInt(mac.substring(0, 2), 16);
            assertEquals(0x02, firstOctet & 0x03);
        }
    }

    @Test
    public void phoneNumberStaysInsideReservedFictionalRange() {
        for (int i = 0; i < 200; i++) {
            assertTrue(generator.generateTestPhoneNumber().matches("\\+120255501\\d{2}"));
        }
    }

    @Test
    public void mobileIdentifiersUseTestNetworkAndValidLengths() {
        assertEquals("001", generator.getTestMcc());
        assertEquals("01", generator.getTestMnc());
        assertEquals("00101", generator.getTestOperatorNumeric());

        for (int i = 0; i < 200; i++) {
            String imsi = generator.generateTestImsi();
            assertTrue(imsi.matches("00101\\d{10}"));

            String iccid = generator.generateTestIccid();
            assertTrue(iccid.matches("8900101\\d{12}"));
            assertTrue(hasValidLuhnCheckDigit(iccid));
        }
    }

    @Test
    public void meidSerialAndBuildTimeCarryExplicitTestSemantics() {
        long startOf2026 = 1767225600000L;
        long startOf2027 = 1798761600000L;
        for (int i = 0; i < 200; i++) {
            assertTrue(generator.generateTestMeid().matches("A00000BEEF[0-9A-F]{4}"));
            assertTrue(generator.generateTestSerialNumber().matches("TEST[0-9A-F]{12}"));

            long buildTime = Long.parseLong(generator.generateTestBuildTimestamp());
            assertTrue(buildTime >= startOf2026);
            assertTrue(buildTime < startOf2027);
        }
    }

    @Test
    public void hookCapabilitiesMatchLegacyAndModernImplementations() {
        PhoneInfo.init(null);

        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.FingerPrint));
        assertFalse(PhoneInfo.isModernSupported(PhoneKey.FingerPrint));
        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.SimState));
        assertTrue(PhoneInfo.isModernSupported(PhoneKey.SimState));
        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.RadioVersion));
        assertTrue(PhoneInfo.isModernSupported(PhoneKey.RadioVersion));

        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.AndroidId));
        assertTrue(PhoneInfo.isModernSupported(PhoneKey.AndroidId));
        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.NetworkOperatorName));
        assertTrue(PhoneInfo.isModernSupported(PhoneKey.NetworkOperatorName));
        assertTrue(PhoneInfo.isLegacySupported(PhoneKey.NetworkOperator));
        assertTrue(PhoneInfo.isModernSupported(PhoneKey.NetworkCountryIso));

        assertEquals(!"modern".equals(BuildConfig.XPOSED_VARIANT),
                PhoneInfo.isSupported(PhoneKey.FingerPrint));

        assertFalse(PhoneInfo.isSupported(PhoneKey.SDK));
        assertFalse(PhoneInfo.isSupported(PhoneKey.SecurityPatch));
    }

    @Test
    public void randomProfileCoversOnlyReplaceableFieldsWithValidValues() {
        for (int i = 0; i < 50; i++) {
            LinkedHashMap<String, String> profile = RandomInfo.randomPhoneInfo();
            assertEquals(PhoneInfo.getReplaceableKeys(), profile.keySet());

            for (Map.Entry<String, String> entry : profile.entrySet()) {
                assertTrue(entry.getKey(), entry.getValue() != null);
                assertFalse(entry.getKey(), entry.getValue().trim().isEmpty());
            }

            assertTrue(hasValidLuhnCheckDigit(profile.get(PhoneKey.IMEI)));
            assertTrue(profile.get(PhoneKey.IMEI).matches("00440000\\d{7}"));
            assertTrue(profile.get(PhoneKey.MEID).matches("A00000BEEF[0-9A-F]{4}"));
            assertTrue(profile.get(PhoneKey.AndroidId).matches("[0-9a-f]{16}"));
            assertLocallyAdministeredUnicast(profile.get(PhoneKey.WifiMac));
            assertLocallyAdministeredUnicast(profile.get(PhoneKey.WifiBssid));
            assertLocallyAdministeredUnicast(profile.get(PhoneKey.BluetoothMac));
            assertTrue(profile.get(PhoneKey.SerialNo).matches("TEST[0-9A-F]{12}"));
            assertTrue(profile.get(PhoneKey.Wifissid).length() <= 32);

            assertTrue(profile.get(PhoneKey.PhoneNumber).matches("\\+120255501\\d{2}"));
            assertTrue(profile.get(PhoneKey.SubscriberId).matches("00101\\d{10}"));
            assertTrue(profile.get(PhoneKey.SimSerialNo).matches("8900101\\d{12}"));
            assertTrue(hasValidLuhnCheckDigit(profile.get(PhoneKey.SimSerialNo)));
            assertEquals("00101", profile.get(PhoneKey.SimOperator));
            assertEquals("zz", profile.get(PhoneKey.SimCountryIso));
            assertEquals("00101", profile.get(PhoneKey.NetworkOperator));
            assertEquals("zz", profile.get(PhoneKey.NetworkCountryIso));

            if ("modern".equals(BuildConfig.XPOSED_VARIANT)) {
                assertEquals(19, profile.size());
                assertFalse(profile.containsKey(PhoneKey.FingerPrint));
            } else {
                assertEquals(40, profile.size());
                String expectedFingerprint = "example/" + profile.get(PhoneKey.Product)
                        + "/" + profile.get(PhoneKey.Device)
                        + ":" + profile.get(PhoneKey.ReleaseOS)
                        + "/" + profile.get(PhoneKey.BuildID)
                        + "/" + profile.get(PhoneKey.Incremental)
                        + ":" + profile.get(PhoneKey.TYPE)
                        + "/" + profile.get(PhoneKey.TAGS);
                assertEquals(expectedFingerprint, profile.get(PhoneKey.FingerPrint));
            }

            assertFalse(profile.containsKey(PhoneKey.SDK));
            assertFalse(profile.containsKey(PhoneKey.SecurityPatch));
            assertFalse(profile.containsKey(PhoneKey.BatteryLevel));
        }
    }

    @Test
    public void displayMergePreservesEmptyReplacementWithoutLeakingOriginalIdentifier() {
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        original.put(PhoneKey.AndroidId, "real-device-identifier");
        original.put(PhoneKey.SecurityPatch, "2026-08-01");
        LinkedHashMap<String, String> replacement = new LinkedHashMap<>();
        replacement.put(PhoneKey.AndroidId, "");
        replacement.put(PhoneKey.SecurityPatch, "");

        LinkedHashMap<String, String> displayed =
                PhoneInfo.mergeForDisplay(original, replacement);

        assertEquals("", displayed.get(PhoneKey.AndroidId));
        assertEquals("2026-08-01", displayed.get(PhoneKey.SecurityPatch));
    }

    @Test
    public void missingProfileNeverCopiesRealIdentifierIntoReplacementField() {
        LinkedHashMap<String, String> original = new LinkedHashMap<>();
        original.put(PhoneKey.AndroidId, "real-device-identifier");
        original.put(PhoneKey.SecurityPatch, "2026-08-01");

        LinkedHashMap<String, String> displayed =
                PhoneInfo.mergeForDisplay(original, null);

        assertEquals("", displayed.get(PhoneKey.AndroidId));
        assertEquals("2026-08-01", displayed.get(PhoneKey.SecurityPatch));
    }

    @Test
    public void portableProfileRegistryExcludesReadOnlyDeviceInformation() {
        LinkedHashMap<String, String> portable =
                PhoneInfo.getPortableProfileFields(null);

        assertTrue(portable.containsKey(PhoneKey.AndroidId));
        assertTrue(portable.containsKey(PhoneKey.NetworkOperatorName));
        assertFalse(portable.containsKey(PhoneKey.SDK));
        assertFalse(portable.containsKey(PhoneKey.IP));
        assertFalse(portable.containsKey(PhoneKey.SignatureSha256));
        for (Map.Entry<String, String> entry : portable.entrySet()) {
            assertTrue(entry.getKey(), entry.getValue() != null);
            assertFalse(entry.getKey(), entry.getValue().trim().isEmpty());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullSecureRandomIsRejected() {
        new IdentifierGenerator(null);
    }

    private static boolean hasValidLuhnCheckDigit(String value) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = value.length() - 1; i >= 0; i--) {
            int digit = value.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static void assertLocallyAdministeredUnicast(String mac) {
        assertTrue(mac.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}"));
        int firstOctet = Integer.parseInt(mac.substring(0, 2), 16);
        assertEquals(0x02, firstOctet & 0x03);
    }
}
