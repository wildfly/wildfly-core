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
public class EqualsTestCase extends ComparisonTestBase {

    @Test
    public void testSimpleBoolean() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(true);
        assertTrue(node, "result == true");
        node.get("result").set(false);
        assertTrue(node, "result == false");
        node.get("result").set(false);
        assertFalse(node, "result == true");
    }

    @Test
    public void testPathBoolean() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("value").set(true);
        assertTrue(node, "result.value == true");
        assertFalse(node, "result.value1 == true");
        assertFalse(node, "result == true");

        node.get("result").get("value").set(false);
        assertTrue(node, "result.value == false");
        assertFalse(node, "result.value1 == false");
        assertFalse(node, "result == false");

        node.get("result").get("value").set(false);
        assertFalse(node, "result.value == true");
    }

    @Test
    public void testSimpleString() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set("true");
        assertTrue(node, "result == \"true\"");
        node.get("result").set("false");
        assertFalse(node, "result == \"true\"");
    }

    @Test
    public void testSimpleInt() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(11);
        assertTrue(node, "result == 11");

        node.get("result").set(111);
        assertFalse(node, "result == 11");

        node.get("result").set("11");
        assertFalse(node, "result == 11");
    }

    @Test
    public void testUndefined() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result");
        assertTrue(node, "result == undefined");
        assertFalse(node, "result == \"undefined\"");
        assertFalse(node, "result == 'undefined'");

        node.get("result").set("undefined");
        assertFalse(node, "result == undefined");
    }

    @Test
    public void testWhitespace() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set("foo bar");
        assertTrue(node, "result == \"foo bar\"");

        node.get("result").set("foo\nbar");
        assertTrue(node, "result == \"foo\\nbar\"");
    }
}
