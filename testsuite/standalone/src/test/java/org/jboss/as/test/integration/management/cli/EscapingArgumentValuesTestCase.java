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
public class EscapingArgumentValuesTestCase {

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
    public void testSimpleBackSlashInOperation() throws Exception {
        // the value here is parsed by the cli parser
        // TODO the expected value will have to change to c:dir\test.txt once https://github.com/wildfly/wildfly-core/pull/106 has been merged
        assertEquals("c:dir\test.txt", parsedOperationRequestValue("c:\\dir\\test.txt"));
    }

    @Test
    public void testSimpleBackSlashInCommand() throws Exception {
        // the value here is parsed by the cli parser
        // TODO the expected value will have to change to c:dir\test.txt once https://github.com/wildfly/wildfly-core/pull/106 has been merged
        assertEquals("c:dir\test.txt", parsedCommandValue("c:\\dir\\test.txt"));
    }

    @Test
    public void testSimpleBackSlashQuotedInOperation() throws Exception {
        // this value, since it's quoted, is actually parsed by the DMR parser
        assertEquals("c:dirtest.txt", parsedOperationRequestValue("\"c:\\dir\\test.txt\""));
    }

    @Test
    public void testSimpleBackSlashQuotedInCommand() throws Exception {
        // this value, since it's quoted, is actually parsed by the DMR parser
        assertEquals("c:dirtest.txt", parsedCommandValue("\"c:\\dir\\test.txt\""));
    }

    @Test
    public void testBackSlashEscapedInOperation() throws Exception {
        // the value here is parsed by the cli parser
        assertEquals("c:\\dir\\test.txt", parsedOperationRequestValue("c:\\\\dir\\\\test.txt"));
    }

    @Test
    public void testBackSlashEscapedInCommand() throws Exception {
        // the value here is parsed by the cli parser
        assertEquals("c:\\dir\\test.txt", parsedCommandValue("c:\\\\dir\\\\test.txt"));
    }

    @Test
    public void testBackSlashEscapedAndQuotedInOperation() throws Exception {
        // this value, since it's quoted, is actually parsed by the DMR parser
        assertEquals("c:\\dir\\test.txt", parsedOperationRequestValue("\"c:\\\\dir\\\\test.txt\""));
    }

    @Test
    public void testBackSlashEscapedAndQuotedInCommand() throws Exception {
        // this value, since it's quoted, is actually parsed by the DMR parser
        assertEquals("c:\\dir\\test.txt", parsedCommandValue("\"c:\\\\dir\\\\test.txt\""));
    }

    private String parsedOperationRequestValue(String input) throws Exception {
        final ModelNode req = ctx.buildRequest("/system-property=test:add(value=" + input + ")");
        ModelNode value = req.get(VALUE);
        assertTrue(value.isDefined());
        return value.asString();
    }

    private String parsedCommandValue(String input) throws Exception {
        final ModelNode req = ctx.buildRequest("system-property add --name=test --value=" + input);
        ModelNode value = req.get(VALUE);
        assertTrue(value.isDefined());
        return value.asString();
    }
}
