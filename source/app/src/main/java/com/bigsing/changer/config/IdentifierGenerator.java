package com.bigsing.changer.config;

import java.security.SecureRandom;

/**
 * 生成仅用于测试和配置演示的合成标识符。
 *
 * <p>电话号码使用 NANP 明确保留的 555-0100 至 555-0199 虚构号段，移动网络
 * 使用 001 测试 MCC。本类不会读取或复用设备上的真实标识符。</p>
 */
public final class IdentifierGenerator {
    private static final char[] LOWER_HEX = "0123456789abcdef".toCharArray();
    private static final String TEST_TAC = "00440000";
    private static final String TEST_MCC = "001";
    private static final String TEST_MNC = "01";
    private static final String TEST_ICCID_PREFIX = "8900101";
    private static final long TEST_BUILD_EPOCH_MILLIS = 1767225600000L;

    private final SecureRandom secureRandom;

    public IdentifierGenerator() {
        this(new SecureRandom());
    }

    public IdentifierGenerator(SecureRandom secureRandom) {
        if (secureRandom == null) {
            throw new IllegalArgumentException("secureRandom must not be null");
        }
        this.secureRandom = secureRandom;
    }

    /** 返回 15 位、通过 Luhn 校验且使用测试 TAC 的合成 IMEI。 */
    public String generateImei() {
        String payload = TEST_TAC + randomDigits(6);
        return payload + calculateLuhnCheckDigit(payload);
    }

    /** 返回 16 位小写十六进制合成 Android ID。 */
    public String generateAndroidId() {
        return randomHex(16, false);
    }

    /** 返回 14 位大写十六进制、带明显 BEEF 测试标记的合成 MEID。 */
    public String generateTestMeid() {
        return "A00000BEEF" + randomHex(4, true);
    }

    /** 返回 16 位、以 TEST 开头的合成设备序列号。 */
    public String generateTestSerialNumber() {
        return "TEST" + randomHex(12, true);
    }

    /** 返回 2026 年内的合成 Build.TIME 毫秒时间戳。 */
    public String generateTestBuildTimestamp() {
        long dayOffset = secureRandom.nextInt(365);
        long minuteOffset = secureRandom.nextInt(24 * 60);
        return Long.toString(TEST_BUILD_EPOCH_MILLIS
                + dayOffset * 24L * 60L * 60L * 1000L
                + minuteOffset * 60L * 1000L);
    }

    /** 返回本地管理、单播的 48 位合成 MAC 地址。 */
    public String generateMacAddress() {
        byte[] address = new byte[6];
        secureRandom.nextBytes(address);
        address[0] = (byte) ((address[0] & 0xfc) | 0x02);

        StringBuilder result = new StringBuilder(17);
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                result.append(':');
            }
            int value = address[i] & 0xff;
            result.append(LOWER_HEX[value >>> 4]);
            result.append(LOWER_HEX[value & 0x0f]);
        }
        return result.toString();
    }

    /** 返回 +1 202-555-0100 至 +1 202-555-0199 范围内的虚构电话号码。 */
    public String generateTestPhoneNumber() {
        int suffix = secureRandom.nextInt(100);
        return "+120255501" + twoDigits(suffix);
    }

    public String getTestMcc() {
        return TEST_MCC;
    }

    public String getTestMnc() {
        return TEST_MNC;
    }

    public String getTestOperatorNumeric() {
        return TEST_MCC + TEST_MNC;
    }

    /** 返回以 001/01 测试网络开头的 15 位合成 IMSI。 */
    public String generateTestImsi() {
        return getTestOperatorNumeric() + randomDigits(10);
    }

    /** 返回 19 位、通过 Luhn 校验且带有测试网络前缀的合成 ICCID。 */
    public String generateTestIccid() {
        String payload = TEST_ICCID_PREFIX + randomDigits(11);
        return payload + calculateLuhnCheckDigit(payload);
    }

    private String randomDigits(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append((char) ('0' + secureRandom.nextInt(10)));
        }
        return result.toString();
    }

    private String randomHex(int length, boolean uppercase) {
        char[] result = new char[length];
        for (int i = 0; i < result.length; i++) {
            char value = LOWER_HEX[secureRandom.nextInt(LOWER_HEX.length)];
            result[i] = uppercase ? Character.toUpperCase(value) : value;
        }
        return new String(result);
    }

    private static char calculateLuhnCheckDigit(String payload) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = payload.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (char) ('0' + ((10 - (sum % 10)) % 10));
    }

    private static String twoDigits(int value) {
        return new String(new char[]{
                (char) ('0' + value / 10),
                (char) ('0' + value % 10)
        });
    }
}
