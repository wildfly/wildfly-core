/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers;

import static org.jboss.as.controller.client.helpers.JBossModulesNameUtil.canonicalModuleIdentifier;
import static org.jboss.as.controller.client.helpers.JBossModulesNameUtil.parseCanonicalModuleIdentifier;
import static org.jboss.as.controller.client.helpers.JBossModulesNameUtil.parseModuleIdentifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

/**
 * Unit tests of {@link JBossModulesNameUtil}.
 */
public class JBossModulesNameUtilUnitTestCase {

    @Test
    public void testParsingCanonicalization() {
        assertEquals("org.jboss.foo", parseCanonicalModuleIdentifier("org.jboss.foo"));
        assertEquals("org.jboss.foo", parseCanonicalModuleIdentifier("org.jboss.foo:main"));
        assertEquals("org.jboss.foo:bar", parseCanonicalModuleIdentifier("org.jboss.foo:bar"));
        // TODO these next two seem wrong, but it's what ModuleIdentifier.fromString(...).toString() does
        assertEquals("org.jboss\\\\\\:foo", parseCanonicalModuleIdentifier("org.jboss\\:foo"));
        assertEquals("org.jboss\\\\\\:foo:bar", parseCanonicalModuleIdentifier("org.jboss\\:foo:bar"));
    }

    @Test
    public void testAppendingCanonicalization() {
        assertEquals("org.jboss.foo", canonicalModuleIdentifier("org.jboss.foo", null));
        assertEquals("org.jboss.foo", canonicalModuleIdentifier("org.jboss.foo", "main"));
        assertEquals("org.jboss.foo:bar", canonicalModuleIdentifier("org.jboss.foo", "bar"));
        // TODO these next two seem wrong, but it's what ModuleIdentifier.create(...).toString() does
        assertEquals("org.jboss\\\\\\:foo", canonicalModuleIdentifier("org.jboss\\:foo", null));
        assertEquals("org.jboss\\\\\\:foo:bar", canonicalModuleIdentifier("org.jboss\\:foo", "bar"));
    }

    @Test
    public void testParsingToFunction() {
        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo", JBossModulesNameUtilUnitTestCase::biFunction),
                null);

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo:main", JBossModulesNameUtilUnitTestCase::biFunction),
                "main");

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo:main", JBossModulesNameUtilUnitTestCase::biFunction, false),
                "main");

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo:main", JBossModulesNameUtilUnitTestCase::biFunction, true),
                null);

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo:main", JBossModulesNameUtilUnitTestCase::biFunction, false, "bar"),
                "main");

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo:main", JBossModulesNameUtilUnitTestCase::biFunction, true, "bar"),
                "bar");

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo", JBossModulesNameUtilUnitTestCase::biFunction, false, "bar"),
                "bar");

        validateFunctionResult(
                parseModuleIdentifier("org.jboss.foo", JBossModulesNameUtilUnitTestCase::biFunction, true, "bar"),
                "bar");
    }

    private static void validateFunctionResult(Map<String, String> result, String expectedSlot) {
        assertNotNull(result.toString(), result);
        assertEquals(result.toString(), 1, result.size());
        assertTrue(result.toString(), result.containsKey("org.jboss.foo"));
        assertEquals(result.toString(), expectedSlot == null ? "placeholder" : expectedSlot, result.get("org.jboss.foo"));
    }

    private static Map<String, String> biFunction(String name, String slot) {
        return Map.of(name, slot == null ? "placeholder" : slot);
    }
}
