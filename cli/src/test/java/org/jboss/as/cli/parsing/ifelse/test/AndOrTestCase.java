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
public class AndOrTestCase extends ComparisonTestBase {

    @Test
    public void testSimpleAnd() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("i").set(10);
        node.get("result").get("b").set(true);

        assertTrue(node, "result.i < 11 && result.b == true");
        assertFalse(node, "result.i < 11 && result.b != true");
        assertFalse(node, "result.i < 1 && result.b == true");
        assertFalse(node, "result.i < 1 && result.b != true");
        assertTrue(node, "result.i > 1 && result.b != false");
    }

    @Test
    public void testSimpleOr() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("i").set(10);
        node.get("result").get("b").set(true);

        assertTrue(node, "result.i < 11 || result.b == true");
        assertTrue(node, "result.i < 11 || result.b != true");
        assertTrue(node, "result.i < 1 || result.b == true");
        assertFalse(node, "result.i < 1 || result.b != true");
    }

    @Test
    public void testAndOr() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("a").set("a");
        node.get("result").get("b").set("b");
        node.get("result").get("c").set("c");

        assertTrue(node, "result.a == a || result.b == b && result.c == c");
        assertTrue(node, "result.a == x || result.b == b && result.c == c");
        assertFalse(node, "result.a == x || result.b == x && result.c == c");
        assertFalse(node, "result.a == x || result.b == b && result.c == x");
        assertTrue(node, "result.a == a || result.b == b && result.c == x");
    }

    @Test
    public void testParentheses() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("a").set("a");
        node.get("result").get("b").set("b");
        node.get("result").get("c").set("c");

        assertTrue(node, "(result.a == a || result.b == b) && result.c == c");
        assertFalse(node, "(result.a == a || result.b == b) && result.c == x");
        assertTrue(node, "(result.a == a || result.b == x) && result.c == c");
        assertTrue(node, "(result.a == x || result.b == b) && result.c == c");
        assertFalse(node, "(result.a == x || result.b == x) && result.c == c");
    }

    @Test
    public void testNestedParentheses() throws Exception {
        ModelNode node = new ModelNode();
        node.get("result").get("a").set("a");
        node.get("result").get("b").set("b");
        node.get("result").get("c").set("c");
        node.get("result").get("d").set("d");
        node.get("result").get("e").set("e");

        assertTrue(node, "(result.a == a || result.e == e && (result.b == b || result.d == d)) && result.c == c");
        assertFalse(node, "(result.a == a || result.e == e && (result.b == b || result.d == d)) && result.c == x");
        assertTrue(node, "(result.a == a || result.e == e && (result.b == x || result.d == d)) && result.c == c");
        assertTrue(node, "(result.a == a || result.e == x && (result.b == x || result.d == d)) && result.c == c");
        assertFalse(node, "(result.a == x || result.e == x && (result.b == x || result.d == d)) && result.c == c");
    }
}
