package com.bigsing.changer.config;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProfileCodecTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ProfileCodec codec;

    @Before
    public void setUp() {
        codec = new ProfileCodec(new LinkedHashSet<>(
                Arrays.asList("AndroidId", "IMEI", "WifiMac")));
    }

    @Test
    public void v2RoundTripPreservesUnicodeFlagsValueSourceAndScope() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("AndroidId", "abc=123\\value\n中文");
        values.put("IMEI", "004400001234565");
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        enabled.put("AndroidId", true);
        enabled.put("IMEI", false);
        Set<String> scope = new LinkedHashSet<>(
                Arrays.asList("com.example.alpha", "org.example.beta"));
        ProfileSnapshot original = new ProfileSnapshot(
                ProfileCodec.CURRENT_VERSION, values, enabled, scope, true, true);

        byte[] encoded = codec.encode(original);
        ProfileSnapshot decoded = codec.decode(new ByteArrayInputStream(encoded));

        assertEquals(ProfileCodec.CURRENT_VERSION, decoded.getVersion());
        assertEquals(values, decoded.getValues());
        assertEquals(enabled, decoded.getEnabledFields());
        assertEquals(scope, decoded.getScopedPackages());
        assertTrue(decoded.isScopeAll());
        assertTrue(decoded.isUseHardcoded());
        String text = new String(encoded, UTF_8);
        assertTrue(text.contains("profile.version=2\n"));
        assertTrue(text.contains("scope.all=true\n"));
        assertTrue(text.contains("profile.useHardcoded=true\n"));
        assertTrue(text.contains("中文"));
        assertFalse(text.contains("\\u4e2d"));
    }

    @Test
    public void registryFactoryUsesOnlyRegistryKeysAndOrder() {
        Map<String, Object> registry = new LinkedHashMap<>();
        registry.put("IMEI", new Object());
        registry.put("AndroidId", new Object());

        ProfileCodec fromRegistry = ProfileCodec.fromFieldRegistry(registry);

        assertEquals(Arrays.asList("IMEI", "AndroidId"),
                Arrays.asList(fromRegistry.getAllowedFields().toArray(new String[0])));
    }

    @Test
    public void snapshotDefensivelyCopiesAndExposesImmutableCollections() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("IMEI", "one");
        ProfileSnapshot snapshot = new ProfileSnapshot(ProfileCodec.CURRENT_VERSION, values,
                Collections.<String, Boolean>emptyMap(), Collections.<String>emptySet());
        values.put("IMEI", "changed");
        assertEquals("one", snapshot.getValues().get("IMEI"));
        assertFalse(snapshot.isScopeAll());
        assertFalse(snapshot.isUseHardcoded());

        try {
            snapshot.getValues().put("IMEI", "changed");
            fail("snapshot values must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void decodeRejectsUnknownProperty() throws Exception {
        assertDecodeRejected("profile.version=1\nextra.setting=true\n", "unknown profile property");
    }

    @Test
    public void decodeRejectsUnknownField() throws Exception {
        assertDecodeRejected("profile.version=1\nvalue.SerialNo=test\n", "unknown profile field");
    }

    @Test
    public void decodeRejectsDuplicateProperty() throws Exception {
        assertDecodeRejected("profile.version=1\nvalue.IMEI=one\nvalue.IMEI=two\n",
                "duplicate profile property");
        assertDecodeRejected("profile.version=2\nscope.all=false\nscope.all=true\n"
                        + "profile.useHardcoded=false\n",
                "duplicate profile property");
    }

    @Test
    public void decodeRejectsNonCanonicalBoolean() throws Exception {
        assertDecodeRejected("profile.version=1\nenabled.IMEI=TRUE\n", "invalid boolean");
        assertDecodeRejected("profile.version=1\nenabled.IMEI=1\n", "invalid boolean");
        assertDecodeRejected("profile.version=2\nscope.all=TRUE\n"
                        + "profile.useHardcoded=false\n",
                "invalid boolean for profile property");
        assertDecodeRejected("profile.version=2\nscope.all=false\n"
                        + "profile.useHardcoded=1\n",
                "invalid boolean for profile property");
    }

    @Test
    public void decodeRejectsUnsupportedOrMissingVersion() throws Exception {
        assertDecodeRejected("profile.version=3\n", "unsupported or missing");
        assertDecodeRejected("value.IMEI=test\n", "unsupported or missing");
    }

    @Test
    public void decodeV1MigratesMissingV2FlagsToFalse() throws Exception {
        String v1 = "profile.version=1\n"
                + "scope.packages=com.example.alpha\n"
                + "value.AndroidId=test-id\n"
                + "enabled.AndroidId=true\n"
                + "value.IMEI=\n"
                + "enabled.IMEI=true\n"
                + "enabled.WifiMac=true\n";

        ProfileSnapshot migrated = codec.decode(
                new ByteArrayInputStream(v1.getBytes(UTF_8)));

        assertEquals(ProfileCodec.CURRENT_VERSION, migrated.getVersion());
        assertEquals("test-id", migrated.getValues().get("AndroidId"));
        assertEquals(Boolean.TRUE, migrated.getEnabledFields().get("AndroidId"));
        assertEquals(Boolean.FALSE, migrated.getEnabledFields().get("IMEI"));
        assertEquals(Boolean.FALSE, migrated.getEnabledFields().get("WifiMac"));
        assertEquals(Collections.singleton("com.example.alpha"),
                migrated.getScopedPackages());
        assertFalse(migrated.isScopeAll());
        assertFalse(migrated.isUseHardcoded());

        String upgraded = new String(codec.encode(migrated), UTF_8);
        assertTrue(upgraded.contains("profile.version=2\n"));
        assertTrue(upgraded.contains("scope.all=false\n"));
        assertTrue(upgraded.contains("profile.useHardcoded=false\n"));
    }

    @Test
    public void decodeV1RejectsV2OnlyProperties() throws Exception {
        assertDecodeRejected("profile.version=1\nscope.all=false\n",
                "not allowed in version 1");
        assertDecodeRejected("profile.version=1\nprofile.useHardcoded=false\n",
                "not allowed in version 1");
    }

    @Test
    public void decodeV2RequiresBothExplicitFlags() throws Exception {
        assertDecodeRejected("profile.version=2\n", "missing required profile property");
        assertDecodeRejected("profile.version=2\nscope.all=false\n",
                "missing required profile property: profile.useHardcoded");
        assertDecodeRejected("profile.version=2\nprofile.useHardcoded=false\n",
                "missing required profile property: scope.all");
    }

    @Test
    public void decodeRejectsInvalidScopePackage() throws Exception {
        assertDecodeRejected("profile.version=1\nscope.packages=com.example.good,not-a-package\n",
                "invalid scoped package");
    }

    @Test
    public void encodeRejectsUnknownFieldAndOversizedValue() throws Exception {
        Map<String, String> unknown = Collections.singletonMap("SerialNo", "value");
        assertEncodeRejected(new ProfileSnapshot(ProfileCodec.CURRENT_VERSION, unknown,
                Collections.<String, Boolean>emptyMap(), Collections.<String>emptySet()),
                "unknown profile field");

        char[] characters = new char[ProfileCodec.MAX_VALUE_LENGTH + 1];
        Arrays.fill(characters, 'x');
        Map<String, String> tooLong = Collections.singletonMap("IMEI", new String(characters));
        assertEncodeRejected(new ProfileSnapshot(ProfileCodec.CURRENT_VERSION, tooLong,
                Collections.<String, Boolean>emptyMap(), Collections.<String>emptySet()),
                "profile value is too long");
    }

    @Test
    public void decodeRejectsOversizedDocumentBeforeParsing() throws Exception {
        byte[] oversized = new byte[ProfileCodec.MAX_PROFILE_BYTES + 1];
        try {
            codec.decode(new ByteArrayInputStream(oversized));
            fail("oversized profile must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("profile is too large"));
        }
    }

    @Test
    public void decodeRejectsMalformedUtf8() throws Exception {
        byte[] malformed = new byte[]{(byte) 0xc3, (byte) 0x28};
        try {
            codec.decode(new ByteArrayInputStream(malformed));
            fail("malformed UTF-8 must be rejected");
        } catch (IOException expected) {
            // Expected from the strict UTF-8 decoder.
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsInvalidWhitelistKey() {
        new ProfileCodec(Collections.singleton("value.bad"));
    }

    private void assertDecodeRejected(String profile, String expectedMessage) throws Exception {
        try {
            codec.decode(new ByteArrayInputStream(profile.getBytes(UTF_8)));
            fail("invalid profile must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private void assertEncodeRejected(ProfileSnapshot snapshot, String expectedMessage)
            throws IOException {
        try {
            codec.encode(snapshot);
            fail("invalid snapshot must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }
}
