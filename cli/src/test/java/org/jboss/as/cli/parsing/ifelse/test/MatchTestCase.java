/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.ifelse.test;


import org.jboss.dmr.ModelNode;
import org.junit.Test;


/**
 *
 * @author Thomas Darimont
 */
public class MatchTestCase extends ComparisonTestBase {

    @Test
    public void testExactStringPatternPatching() throws Exception {

        ModelNode node = new ModelNode();

        node.get("result").set("foo");
        assertTrue(node, "result ~= \"foo\"");

        node.get("result").set("foo");
        assertFalse(node, "result ~= \"bar\"");
    }

    @Test
    public void testFuzzyStringPatternMatching() throws Exception {

        ModelNode node = new ModelNode();

        String featureList = "feature1 feature2";

        node.get("result").set(featureList);
        assertTrue(node, "result ~= \".*feature1.*\"");

        node.get("result").set(featureList);
        assertTrue(node, "result ~= \".*feature2.*\"");

        node.get("result").set(featureList);
        assertFalse(node, "result ~= \".*feature3.*\"");
    }
}
