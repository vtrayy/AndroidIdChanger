package com.bigsing.changer.config;

import java.util.LinkedHashSet;
import java.util.Set;

/** 解析模块作用域；使用完整包名匹配，避免 contains 导致的前缀误命中。 */
public final class ScopeMatcher {
    private ScopeMatcher() {
    }

    public static Set<String> parse(String configuredPackages) {
        Set<String> result = new LinkedHashSet<>();
        if (configuredPackages == null) {
            return result;
        }
        for (String item : configuredPackages.trim().split("[\\s,;]+")) {
            if (item.length() > 0) {
                result.add(item);
            }
        }
        return result;
    }

    public static boolean contains(String configuredPackages, String packageName) {
        return packageName != null && parse(configuredPackages).contains(packageName);
    }

    public static String serialize(Set<String> packages) {
        StringBuilder result = new StringBuilder();
        for (String packageName : packages) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(packageName);
        }
        return result.toString();
    }
}
