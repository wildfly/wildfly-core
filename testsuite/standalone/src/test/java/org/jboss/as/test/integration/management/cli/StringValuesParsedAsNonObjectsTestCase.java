/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * @author Alexey Loubyansky
 *
 */
@RunWith(WildFlyRunner.class)
public class StringValuesParsedAsNonObjectsTestCase {

    private static final String VALUE = "value";
    private CommandContext ctx;

    @Before
    public void setup() throws Exception {
        ctx = CLITestUtil.getCommandContext();
        ctx.connectController();
        ctx.handle("command add --node-type=system-property --command-name=system-property");
    }

    @After
    public void tearDown() throws Exception {
        ctx.disconnectController();
    }

    @Test
    public void testEqualsSignInParameter() throws Exception {
        // if the string is parsed with the default general parser
        // the returned value will be simply 'b'
        assertEquals("a=b", parsedAddCommandValue("a=b"));
    }

    @Test
    public void testEqualsSignInProperty() throws Exception {
        // if the string is parsed with the default general parser
        // the returned value will be simply 'b'
        assertEquals("a=b", parsedSetPropertyCommandValue("a=b"));
    }

    private String parsedAddCommandValue(String input) throws Exception {
        final ModelNode req = ctx.buildRequest("system-property add --name=test --value=" + input);
        ModelNode valueNode = req.get(VALUE);
        assertTrue(valueNode.isDefined());
        return valueNode.asString();
    }

    private String parsedSetPropertyCommandValue(String input) throws Exception {
        final ModelNode req = ctx.buildRequest("system-property --name=test --value=" + input);
        final ModelNode steps = req.get("steps");
        assertTrue(steps.isDefined());
        final ModelNode wa = steps.asList().get(0);
        assertTrue(wa.isDefined());
        ModelNode valueNode = wa.get(VALUE);
        assertTrue(valueNode.isDefined());
        return valueNode.asString();
    }
}
