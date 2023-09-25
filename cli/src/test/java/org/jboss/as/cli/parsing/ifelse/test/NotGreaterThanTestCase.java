/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.ifelse.test;


import org.jboss.dmr.ModelNode;
import org.junit.Test;


/**
 *
 * @author Alexey Loubyansky
 */
public class NotGreaterThanTestCase extends ComparisonTestBase {

    @Test
    public void testSimpleBoolean() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(true);
        assertTrue(node, "result <= true");

        node.get("result").set(false);
        assertTrue(node, "result <= false");

        node.get("result").set(false);
        assertTrue(node, "result <= true");

        node.get("result").set(true);
        assertFalse(node, "result <= false");
    }

    @Test
    public void testSimpleString() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set("true");
        assertTrue(node, "result <= \"true\"");

        node.get("result").set("false");
        assertTrue(node, "result <= \"true\"");

        node.get("result").set("true");
        assertFalse(node, "result <= \"false\"");

        node.get("result").set("2");
        assertFalse(node, "result <= \"1\"");

        node.get("result").set("1");
        assertTrue(node, "result <= \"2\"");

        node.get("result").set("1");
        assertTrue(node, "result <= \"1\"");
    }

    @Test
    public void testSimpleInt() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(11);
        assertTrue(node, "result <= 11");

        node.get("result").set(111);
        assertFalse(node, "result <= 11");

        node.get("result").set(11);
        assertTrue(node, "result <= 111");

        node.get("result").clear().get("value").set(1);
        assertFalse(node, "result <= 11");

        node.get("result").get("value").set(1);
        assertTrue(node, "result.value <= 11");
        assertFalse(node, "result.value1 <= 11");
        assertFalse(node, "result <= 11");
    }

    @Test
    public void testUndefined() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result");
        assertFalse(node, "result <= 999999");
        assertFalse(node, "result <= false");
        assertFalse(node, "result <= true");
        assertFalse(node, "result <= \"string\"");
    }
}
