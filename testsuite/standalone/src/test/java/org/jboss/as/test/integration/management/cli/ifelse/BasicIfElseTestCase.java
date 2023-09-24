/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli.ifelse;

import jakarta.inject.Inject;
import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
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
public class BasicIfElseTestCase extends CLISystemPropertyTestBase {
    @Inject
    protected ManagementClient managementClient;

    @Test
    public void testMainBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("\"true\""));
            assertEquals("false", runIf(ctx));
            assertEquals("true", runIf(ctx));
            assertEquals("false", runIf(ctx));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testMain() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("\"true\""));
            assertEquals("false", runIf(ctx));
            assertEquals("true", runIf(ctx));
            assertEquals("false", runIf(ctx));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfMatchComparisonBoot() throws Exception {

        final CommandContext ctx = new CommandContextImpl(cliOut, false);
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle(this.getAddPropertyReq("match-test-values", "\"AAA BBB\""));
            assertEquals("true", runIfWithMatchComparison("match-test-values", "AAA", ctx));
            assertEquals("true", runIfWithMatchComparison("match-test-values", "BBB", ctx));
            assertEquals("false", runIfWithMatchComparison("match-test-values", "CCC", ctx));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq("match-test-values"));
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfMatchComparison() throws Exception {

        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle(this.getAddPropertyReq("match-test-values", "\"AAA BBB\""));
            assertEquals("true", runIfWithMatchComparison("match-test-values", "AAA", ctx));
            assertEquals("true", runIfWithMatchComparison("match-test-values", "BBB", ctx));
            assertEquals("false", runIfWithMatchComparison("match-test-values", "CCC", ctx));
        } finally {
            ctx.handleSafe(this.getRemovePropertyReq("match-test-values"));
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testIfInsideIfBoot() throws Exception {
        final CommandContext ctx = new CommandContextImpl(cliOut);
        boolean failed = false;
        try {
            ctx.bindClient(managementClient.getControllerClient());
            ctx.handle("if result.value==\"true\" of " + this.getReadPropertyReq());
            ctx.handle("if result.value==\"true\" of " + this.getReadPropertyReq());
        } catch (CommandFormatException ex) {
            failed = true;
        } finally {
            try {
                if (!failed) {
                    throw new Exception("if inside if should have failed");
                }
            } finally {
                ctx.terminateSession();
                cliOut.reset();
            }
        }
    }

    @Test
    public void testIfInsideIf() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        boolean failed = false;
        try {
            ctx.connectController();
            ctx.handle("if result.value==\"true\" of " + this.getReadPropertyReq());
            ctx.handle("if result.value==\"true\" of " + this.getReadPropertyReq());
        } catch (CommandFormatException ex) {
            failed = true;
        } finally {
            try {
                if (!failed) {
                    throw new Exception("if inside if should have failed");
                }
            } finally {
                ctx.terminateSession();
                cliOut.reset();
            }
        }
    }

    protected String runIf(CommandContext ctx) throws Exception {
        ctx.handle("if result.value==\"true\" of " + this.getReadPropertyReq());
        ctx.handle(this.getWritePropertyReq("\"false\""));
        ctx.handle("else");
        ctx.handle(this.getWritePropertyReq("\"true\""));
        ctx.handle("end-if");
        cliOut.reset();
        ctx.handle(getReadPropertyReq());
        return getValue();
    }

    protected String runIfWithMatchComparison(String propertyName, String lookupValue, CommandContext ctx) throws Exception {

        ctx.handle("set match=false");

        ctx.handle("if result.value~=\".*" + lookupValue + ".*\" of " + this.getReadPropertyReq(propertyName));
        ctx.handle("set match=true");
        ctx.handle("else");
        ctx.handle("set match=false");
        ctx.handle("end-if");
        cliOut.reset();

        ctx.handle("echo $match");

        return cliOut.toString().trim();
    }
}
