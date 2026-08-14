package com.bigsing.changer.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AndroidIdChanger 可移植 properties 配置的严格编解码核心。
 *
 * <p>调用方可将设备字段注册表传给
 * {@link #fromFieldRegistry(Map)}。所有未注册字段、未知属性、重复属性、非法布尔值和
 * 超限输入都会被拒绝。</p>
 */
public final class ProfileCodec {
    public static final int CURRENT_VERSION = 2;
    public static final int MAX_PROFILE_BYTES = 256 * 1024;
    public static final int MAX_VALUE_LENGTH = 4096;
    public static final int MAX_VALUE_UTF8_BYTES = 16 * 1024;
    public static final int MAX_FIELDS = 256;
    public static final int MAX_SCOPE_PACKAGES = 512;
    public static final int MAX_PACKAGE_LENGTH = 255;

    private static final String VERSION_PROPERTY = "profile.version";
    private static final String SCOPE_PROPERTY = "scope.packages";
    private static final String SCOPE_ALL_PROPERTY = "scope.all";
    private static final String USE_HARDCODED_PROPERTY = "profile.useHardcoded";
    private static final String VALUE_PREFIX = "value.";
    private static final String ENABLED_PREFIX = "enabled.";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Pattern FIELD_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,127}");
    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");

    private final Set<String> allowedFields;

    public ProfileCodec(Set<String> allowedFields) {
        if (allowedFields == null || allowedFields.isEmpty()) {
            throw new IllegalArgumentException("allowedFields must not be empty");
        }
        if (allowedFields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException("too many allowed fields");
        }
        LinkedHashSet<String> checkedFields = new LinkedHashSet<>();
        for (String field : allowedFields) {
            validateFieldName(field);
            if (!checkedFields.add(field)) {
                throw new IllegalArgumentException("duplicate allowed field: " + field);
            }
        }
        this.allowedFields = Collections.unmodifiableSet(checkedFields);
    }

    /** 直接适配现有 PhoneInfo 字段注册表，并保留注册表顺序。 */
    public static ProfileCodec fromFieldRegistry(Map<String, ?> fieldRegistry) {
        if (fieldRegistry == null) {
            throw new IllegalArgumentException("fieldRegistry must not be null");
        }
        return new ProfileCodec(fieldRegistry.keySet());
    }

    public Set<String> getAllowedFields() {
        return allowedFields;
    }

    public void encode(ProfileSnapshot snapshot, OutputStream destination) throws IOException {
        if (snapshot == null || destination == null) {
            throw new IllegalArgumentException("snapshot and destination must not be null");
        }
        validateSnapshot(snapshot);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(buffer, UTF_8);
        writer.write("# AndroidIdChanger portable profile\n");
        writeProperty(writer, VERSION_PROPERTY, String.valueOf(snapshot.getVersion()));
        writeProperty(writer, SCOPE_ALL_PROPERTY, String.valueOf(snapshot.isScopeAll()));
        writeProperty(writer, USE_HARDCODED_PROPERTY,
                String.valueOf(snapshot.isUseHardcoded()));
        if (!snapshot.getScopedPackages().isEmpty()) {
            writeProperty(writer, SCOPE_PROPERTY, snapshot.serializeScope());
        }
        for (String field : allowedFields) {
            if (snapshot.getValues().containsKey(field)) {
                writeProperty(writer, VALUE_PREFIX + field, snapshot.getValues().get(field));
            }
            if (snapshot.getEnabledFields().containsKey(field)) {
                writeProperty(writer, ENABLED_PREFIX + field,
                        String.valueOf(snapshot.getEnabledFields().get(field)));
            }
        }
        writer.flush();
        if (buffer.size() > MAX_PROFILE_BYTES) {
            throw new IllegalArgumentException("encoded profile is too large");
        }
        buffer.writeTo(destination);
        destination.flush();
    }

    public byte[] encode(ProfileSnapshot snapshot) throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        encode(snapshot, destination);
        return destination.toByteArray();
    }

    public ProfileSnapshot decode(InputStream source) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        byte[] data = readLimited(source);
        CharsetDecoder decoder = UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        Reader reader = new InputStreamReader(new ByteArrayInputStream(data), decoder);
        StrictProperties properties = new StrictProperties();
        properties.load(reader);

        int sourceVersion = parseVersion(properties.getProperty(VERSION_PROPERTY));
        boolean scopeAll = false;
        boolean useHardcoded = false;
        if (sourceVersion == CURRENT_VERSION) {
            scopeAll = readRequiredBoolean(properties, SCOPE_ALL_PROPERTY);
            useHardcoded = readRequiredBoolean(properties, USE_HARDCODED_PROPERTY);
        }

        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> enabledFields = new LinkedHashMap<>();
        Set<String> scopedPackages = Collections.emptySet();
        for (String propertyName : properties.stringPropertyNames()) {
            if (VERSION_PROPERTY.equals(propertyName)) {
                continue;
            }
            if (SCOPE_PROPERTY.equals(propertyName)) {
                scopedPackages = parseAndValidateScope(properties.getProperty(propertyName));
                continue;
            }
            if (SCOPE_ALL_PROPERTY.equals(propertyName)
                    || USE_HARDCODED_PROPERTY.equals(propertyName)) {
                if (sourceVersion != CURRENT_VERSION) {
                    throw new IllegalArgumentException(
                            "profile property is not allowed in version " + sourceVersion
                                    + ": " + propertyName);
                }
                continue;
            }
            if (propertyName.startsWith(VALUE_PREFIX)) {
                String field = propertyName.substring(VALUE_PREFIX.length());
                requireAllowedField(field);
                String value = properties.getProperty(propertyName);
                validateValue(field, value);
                values.put(field, value);
                continue;
            }
            if (propertyName.startsWith(ENABLED_PREFIX)) {
                String field = propertyName.substring(ENABLED_PREFIX.length());
                requireAllowedField(field);
                String value = properties.getProperty(propertyName);
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("invalid boolean for field: " + field);
                }
                enabledFields.put(field, Boolean.valueOf(value));
                continue;
            }
            throw new IllegalArgumentException("unknown profile property: " + propertyName);
        }
        if (sourceVersion == 1) {
            // v1 没有强制“启用项必须有值”；迁移时安全关闭空值项，避免旧配置失效。
            for (Map.Entry<String, Boolean> entry : enabledFields.entrySet()) {
                String value = values.get(entry.getKey());
                if (Boolean.TRUE.equals(entry.getValue())
                        && (value == null || value.trim().length() == 0)) {
                    entry.setValue(false);
                }
            }
        }
        return new ProfileSnapshot(CURRENT_VERSION, values, enabledFields, scopedPackages,
                scopeAll, useHardcoded);
    }

    private static int parseVersion(String versionText) {
        if ("1".equals(versionText)) {
            return 1;
        }
        if (String.valueOf(CURRENT_VERSION).equals(versionText)) {
            return CURRENT_VERSION;
        }
        throw new IllegalArgumentException("unsupported or missing profile version");
    }

    private static boolean readRequiredBoolean(Properties properties, String propertyName) {
        String value = properties.getProperty(propertyName);
        if (value == null) {
            throw new IllegalArgumentException(
                    "missing required profile property: " + propertyName);
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(
                    "invalid boolean for profile property: " + propertyName);
        }
        return Boolean.parseBoolean(value);
    }

    private void validateSnapshot(ProfileSnapshot snapshot) {
        if (snapshot.getVersion() != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported profile version");
        }
        for (Map.Entry<String, String> entry : snapshot.getValues().entrySet()) {
            requireAllowedField(entry.getKey());
            validateValue(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Boolean> entry : snapshot.getEnabledFields().entrySet()) {
            requireAllowedField(entry.getKey());
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("null enabled flag: " + entry.getKey());
            }
        }
        validateScope(snapshot.getScopedPackages());
    }

    private Set<String> parseAndValidateScope(String serializedScope) {
        Set<String> packages = ScopeMatcher.parse(serializedScope);
        validateScope(packages);
        return packages;
    }

    private static void validateScope(Set<String> packages) {
        if (packages.size() > MAX_SCOPE_PACKAGES) {
            throw new IllegalArgumentException("too many scoped packages");
        }
        for (String packageName : packages) {
            if (packageName == null || packageName.length() > MAX_PACKAGE_LENGTH
                    || !PACKAGE_NAME.matcher(packageName).matches()) {
                throw new IllegalArgumentException("invalid scoped package: " + packageName);
            }
        }
    }

    private void requireAllowedField(String field) {
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("unknown profile field: " + field);
        }
    }

    private static void validateFieldName(String field) {
        if (field == null || !FIELD_KEY.matcher(field).matches()) {
            throw new IllegalArgumentException("invalid field name: " + field);
        }
    }

    private static void validateValue(String field, String value) {
        if (value == null) {
            throw new IllegalArgumentException("null profile value: " + field);
        }
        if (value.length() > MAX_VALUE_LENGTH
                || value.getBytes(UTF_8).length > MAX_VALUE_UTF8_BYTES) {
            throw new IllegalArgumentException("profile value is too long: " + field);
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character < 0x20 && character != '\n' && character != '\r' && character != '\t')
                    || character == 0x7f) {
                throw new IllegalArgumentException("profile value contains a control character: " + field);
            }
        }
    }

    private static byte[] readLimited(InputStream source) throws IOException {
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = source.read(buffer)) != -1) {
            total += count;
            if (total > MAX_PROFILE_BYTES) {
                throw new IllegalArgumentException("profile is too large");
            }
            destination.write(buffer, 0, count);
        }
        return destination.toByteArray();
    }

    private static void writeProperty(Writer writer, String key, String value) throws IOException {
        writer.write(escape(key, true));
        writer.write('=');
        writer.write(escape(value, false));
        writer.write('\n');
    }

    private static String escape(String value, boolean key) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\':
                    result.append("\\\\");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                case '=':
                case ':':
                    if (key) {
                        result.append('\\');
                    }
                    result.append(character);
                    break;
                case '#':
                case '!':
                    if (key || i == 0) {
                        result.append('\\');
                    }
                    result.append(character);
                    break;
                case ' ':
                    if (key || i == 0) {
                        result.append('\\');
                    }
                    result.append(character);
                    break;
                default:
                    result.append(character);
                    break;
            }
        }
        return result.toString();
    }

    /** 利用 Properties 的解析器，同时拒绝其默认允许的重复属性覆盖行为。 */
    private static final class StrictProperties extends Properties {
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException("duplicate profile property: " + key);
            }
            return super.put(key, value);
        }
    }
}
