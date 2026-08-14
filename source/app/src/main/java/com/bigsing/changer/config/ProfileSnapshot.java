package com.bigsing.changer.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 不依赖 Android API 的不可变配置快照。 */
public final class ProfileSnapshot {
    private final int version;
    private final Map<String, String> values;
    private final Map<String, Boolean> enabledFields;
    private final Set<String> scopedPackages;
    private final boolean scopeAll;
    private final boolean useHardcoded;

    /** 保留 v1 调用方式；新增的全局作用域和值来源均采用安全默认值 false。 */
    public ProfileSnapshot(int version, Map<String, String> values,
                           Map<String, Boolean> enabledFields,
                           Set<String> scopedPackages) {
        this(version, values, enabledFields, scopedPackages, false, false);
    }

    public ProfileSnapshot(int version, Map<String, String> values,
                           Map<String, Boolean> enabledFields,
                           Set<String> scopedPackages,
                           boolean scopeAll,
                           boolean useHardcoded) {
        if (values == null || enabledFields == null || scopedPackages == null) {
            throw new IllegalArgumentException("profile collections must not be null");
        }
        this.version = version;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.enabledFields = Collections.unmodifiableMap(new LinkedHashMap<>(enabledFields));
        this.scopedPackages = Collections.unmodifiableSet(new LinkedHashSet<>(scopedPackages));
        this.scopeAll = scopeAll;
        this.useHardcoded = useHardcoded;
    }

    public int getVersion() {
        return version;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public Map<String, Boolean> getEnabledFields() {
        return enabledFields;
    }

    public Set<String> getScopedPackages() {
        return scopedPackages;
    }

    public boolean isScopeAll() {
        return scopeAll;
    }

    public boolean isUseHardcoded() {
        return useHardcoded;
    }

    public String serializeScope() {
        return ScopeMatcher.serialize(scopedPackages);
    }
}
