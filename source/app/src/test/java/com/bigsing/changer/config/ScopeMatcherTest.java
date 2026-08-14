package com.bigsing.changer.config;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScopeMatcherTest {
    @Test
    public void parseAcceptsSupportedSeparatorsAndPreservesFirstOccurrenceOrder() {
        Set<String> parsed = ScopeMatcher.parse(
                " com.example.one,org.example.two;com.example.one\nnet.example.three ");

        assertEquals(Arrays.asList(
                        "com.example.one", "org.example.two", "net.example.three"),
                Arrays.asList(parsed.toArray(new String[0])));
    }

    @Test
    public void containsRequiresExactPackageName() {
        String scope = "com.example.application\norg.example.target";

        assertTrue(ScopeMatcher.contains(scope, "com.example.application"));
        assertFalse(ScopeMatcher.contains(scope, "com.example"));
        assertFalse(ScopeMatcher.contains(scope, "com.example.application.child"));
        assertFalse(ScopeMatcher.contains(scope, null));
    }

    @Test
    public void serializeAndParseRoundTripWithoutDuplicates() {
        Set<String> original = new LinkedHashSet<>(
                Arrays.asList("com.example.first", "com.example.second"));

        String serialized = ScopeMatcher.serialize(original);

        assertEquals("com.example.first\ncom.example.second", serialized);
        assertEquals(original, ScopeMatcher.parse(serialized));
    }

    @Test
    public void nullAndBlankConfigurationProduceEmptyScope() {
        assertTrue(ScopeMatcher.parse(null).isEmpty());
        assertTrue(ScopeMatcher.parse("  \n, ; ").isEmpty());
        assertFalse(ScopeMatcher.contains(null, "com.example.app"));
    }
}
