/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.ifelse;

import jakarta.inject.Inject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class BatchesInIfElseTestCase extends CLISystemPropertyTestBase {
    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testIfNoBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testIfNoBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testIfBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testIfBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"1\" of " + getReadPropertyReq());
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testElseNoBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testElseNoBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testElseBatchBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch (CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testElseBatch() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("1"));
            ctx.handle("if result.value==\"3\" of " + getReadPropertyReq());
            ctx.handle("else");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            try {
                ctx.handle("end-if");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // for cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }
}
