/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertNotNull;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class CdTestCase {

    @Test
    public void testValidAddress() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle("cd subsystem=logging");
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testInvalidAddress() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            try {
                ctx.handle("cd subsystem=subsystem");
            } catch (CommandLineException e) {
                expectedEx = e;
            }
        } finally {
            ctx.terminateSession();
        }
        assertNotNull("Can't cd into a non-existing nodepath.", expectedEx);
    }

    @Test
    public void testNoValidation() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle("cd /subsystem=subsystem --no-validation");
            DefaultOperationRequestAddress address = new DefaultOperationRequestAddress();
            address.toNode("subsystem", "subsystem");
            Assert.assertEquals("Invalid address " + ctx.getCurrentNodePath().getNodeName(),
                    address.getNodeName(), ctx.getCurrentNodePath().getNodeName());
            Assert.assertEquals("Invalid address " + ctx.getCurrentNodePath().getNodeType(),
                    address.getNodeType(), ctx.getCurrentNodePath().getNodeType());

            ctx.handle("cd --no-validation /subsystem=subsystem");
            Assert.assertEquals("Invalid address " + ctx.getCurrentNodePath().getNodeName(),
                    address.getNodeName(), ctx.getCurrentNodePath().getNodeName());
            Assert.assertEquals("Invalid address " + ctx.getCurrentNodePath().getNodeType(),
                    address.getNodeType(), ctx.getCurrentNodePath().getNodeType());
        } finally {
            ctx.terminateSession();
        }
    }

    @Test
    public void testTypeAddress() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext();
        try {
            ctx.connectController();
            ctx.handle("cd deployment");
        } finally {
            ctx.terminateSession();
        }
    }
}
