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
package org.jboss.as.test.integration.management.cli.ifelse;

import javax.inject.Inject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class BatchesInIfElseTestCase extends CLISystemPropertyTestBase {
    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testIfNoBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("end-if");
            fail("expected exception");
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfNoBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("end-if");
            fail("expected exception");
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("end-if");
            fail("expected exception");
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("end-if");
            fail("expected exception");
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testElseNoBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("end-if");
            fail("expected exception");
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testElseNoBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("end-if");
            fail("expected exception");
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testElseBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("end-if");
            fail("expected exception");
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testElseBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("end-if");
            fail("expected exception");
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }
}
