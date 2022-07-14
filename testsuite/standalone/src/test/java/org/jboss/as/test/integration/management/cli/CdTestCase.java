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
