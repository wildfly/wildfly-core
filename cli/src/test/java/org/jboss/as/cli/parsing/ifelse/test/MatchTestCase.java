/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
