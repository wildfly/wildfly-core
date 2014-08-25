/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * @author Alexey Loubyansky
 *
 */
@RunWith(WildflyTestRunner.class)
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
