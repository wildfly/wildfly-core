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
public class NotEqualsTestCase extends ComparisonTestBase {

    @Test
    public void testSimpleBoolean() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(true);
        assertFalse(node, "result != true");
        node.get("result").set(false);
        assertFalse(node, "result != false");
        node.get("result").set(false);
        assertTrue(node, "result != true");
    }

    @Test
    public void testPathBoolean() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("value").set(true);
        assertFalse(node, "result.value != true");
        assertFalse(node, "result.value1 != true");
        assertTrue(node, "result != true");

        node.get("result").get("value").set(false);
        assertFalse(node, "result.value != false");
        assertFalse(node, "result.value1 != false");
        assertTrue(node, "result != false");

        node.get("result").get("value").set(false);
        assertTrue(node, "result.value != true");
    }

    @Test
    public void testSimpleString() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set("true");
        assertFalse(node, "result != \"true\"");
        node.get("result").set("false");
        assertTrue(node, "result != \"true\"");
    }

    @Test
    public void testSimpleInt() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").set(11);
        assertFalse(node, "result != 11");

        node.get("result").set(111);
        assertTrue(node, "result != 11");

        node.get("result").set("11");
        assertTrue(node, "result != 11");
    }

    @Test
    public void testUndefined() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result");
        assertFalse(node, "result != undefined");
        assertTrue(node, "result != \"undefined\"");
        assertTrue(node, "result != 'undefined'");

        node.get("result").set("undefined");
        assertTrue(node, "result != undefined");
    }
}
