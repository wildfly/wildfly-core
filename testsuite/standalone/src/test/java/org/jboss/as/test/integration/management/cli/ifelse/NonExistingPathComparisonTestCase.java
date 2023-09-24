/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.ifelse;

import jakarta.inject.Inject;
import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.CommandContext;
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
public class NonExistingPathComparisonTestCase extends CLISystemPropertyTestBase {
    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testEqualsBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"false\""));
            assertEquals("false", runIf(ctx, "==", "&&", "\"false\""));
            assertEquals("true", runIf(ctx, "==", "||", "\"false\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testEquals() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"false\""));
            assertEquals("false", runIf(ctx, "==", "&&", "\"false\""));
            assertEquals("true", runIf(ctx, "==", "||", "\"false\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotEqualBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"false\""));
            assertEquals("false", runIf(ctx, "!=", "&&", "\"true\""));
            assertEquals("true", runIf(ctx, "!=", "||", "\"true\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotEqual() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"false\""));
            assertEquals("false", runIf(ctx, "!=", "&&", "\"true\""));
            assertEquals("true", runIf(ctx, "!=", "||", "\"true\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testUndefined() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReqWithoutValue());
            assertEquals("undefined", runIf(ctx, "==", "&&", "undefined"));
            assertEquals("true", runIf(ctx, "==", "||", "undefined"));
        }finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testUndefinedBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReqWithoutValue());
            assertEquals("undefined", runIf(ctx, "==", "&&", "undefined"));
            assertEquals("true", runIf(ctx, "==", "||", "undefined"));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testGreaterThan() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, ">", "&&", "\"1\""));
            assertEquals("true", runIf(ctx, ">", "||", "\"1\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testGreaterThanBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, ">", "&&", "\"1\""));
            assertEquals("true", runIf(ctx, ">", "||", "\"1\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testLessThanBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, "<", "&&", "\"7\""));
            assertEquals("true", runIf(ctx, "<", "||", "\"7\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testLessThan() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, "<", "&&", "\"7\""));
            assertEquals("true", runIf(ctx, "<", "||", "\"7\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotLessThan() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, ">=", "&&", "\"5\""));
            assertEquals("true", runIf(ctx, ">=", "||", "\"5\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotLessThanBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, ">=", "&&", "\"5\""));
            assertEquals("true", runIf(ctx, ">=", "||", "\"5\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotGreaterThan() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, "<=", "&&", "\"5\""));
            assertEquals("true", runIf(ctx, "<=", "||", "\"5\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testNotGreaterThanBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"5\""));
            assertEquals("5", runIf(ctx, "<=", "&&", "\"5\""));
            assertEquals("true", runIf(ctx, "<=", "||", "\"5\""));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    protected String runIf(CommandContext ctx, String comparison, String logical, String value) throws Exception {
        ctx.handle(getIfStatement(comparison, logical, value));
        ctx.handle(this.getWritePropertyReq("\"true\""));
        ctx.handle("end-if");
        cliOut.reset();
        ctx.handle(getReadPropertyReq());
        return getValue();
    }

    protected String getIfStatement(String comparison, String logical, String value) {
        return new StringBuilder().append("if (result.value ").append(comparison).append(' ').append(value).append(' ').
                append(logical).
                append(" result.value2 ").append(comparison).append(' ').append(value).
                append(") of ").append(getReadPropertyReq()).toString();
    }
}
