/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.cli.ifelse.CLISystemPropertyTestBase;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class TryCatchFinallyTestCase extends CLISystemPropertyTestBase {

    @Test
    public void testSuccessfulTry() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(getAddPropertyReq("try"));
            ctx.handle("catch");
            ctx.handle(getRemovePropertyReq());
            ctx.handle("end-try");
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("try", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testCatchException() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle("echo try block");
            ctx.handle("fail try");
            ctx.handle("catch");
            ctx.handle("echo catch block");
            ctx.handle("fail catch");
            ctx.handle("finally");
            ctx.handle("echo finally block");
            ctx.handle("fail finally");
            ctx.handleSafe("end-try");
            String out = cliOut.toString();
            assertFalse(out.contains("fail try"));
            assertTrue(out.contains("fail catch"));
            assertTrue(out.contains("fail finally"));
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testTryException() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle("echo try block");
            ctx.handle("fail try");
            ctx.handle("finally");
            ctx.handle("echo finally block");
            ctx.handle("fail finally");
            ctx.handleSafe("end-try");
            String out = cliOut.toString();
            assertTrue(out.contains("fail try"));
            assertTrue(out.contains("fail finally"));
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testCatch() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle(getAddPropertyReq("catch"));
            ctx.handle("end-try");
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("catch", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testErrorInCatch() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle(this.getReadNonexistingPropReq());
            try {
                ctx.handle("end-try");
            } catch (CommandLineException e) {
                expectedEx = e;
            }
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("catch is expected to throw an exception", expectedEx);
    }

    @Test
    public void testTryFinally() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getAddPropertyReq("try"));
            ctx.handle("finally");
            ctx.handle(this.getWritePropertyReq("finally"));
            ctx.handle("end-try");
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("finally", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testErrorInTryCatchFinally() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle(this.getAddPropertyReq("catch"));
            ctx.handle("finally");
            ctx.handle(this.getWritePropertyReq("finally"));
            ctx.handle("end-try");
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("finally", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testErrorInTryErrorInCatchFinally() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle(this.getReadNonexistingPropReq());
            ctx.handle("finally");
            ctx.handle(this.getAddPropertyReq("finally"));
            try {
                ctx.handle("end-try");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e;
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("finally", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("catch is expceted to throw an exception", expectedEx);
    }

    @Test
    public void testErrorInFinally() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle(this.getAddPropertyReq("try"));
            ctx.handle("finally");
            ctx.handle(this.getReadNonexistingPropReq());
            try {
                ctx.handle("end-try");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e;
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq());
            assertEquals("try", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq());
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("finally is expected to throw an exception", expectedEx);
    }

    @Test
    public void testNonBatchedBlocks() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("try", "1"));
            ctx.handle(getAddPropertyReq("catch", "1"));
            ctx.handle(getAddPropertyReq("finally", "1"));
            ctx.handle("try");
            ctx.handle(getWritePropertyReq("try", "2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle(getWritePropertyReq("catch", "2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("finally");
            ctx.handle(getWritePropertyReq("finally", "2"));
            ctx.handle(getReadNonexistingPropReq());
            try {
                ctx.handle("end-try");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq("try"));
            assertEquals("2", getValue());
            cliOut.reset();
            ctx.handle(getReadPropertyReq("catch"));
            assertEquals("2", getValue());
            cliOut.reset();
            ctx.handle(getReadPropertyReq("finally"));
            assertEquals("2", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq("try"));
            ctx.handleSafe(getRemovePropertyReq("catch"));
            ctx.handleSafe(getRemovePropertyReq("finally"));
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testBatchedBlocks() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle(getAddPropertyReq("try", "1"));
            ctx.handle(getAddPropertyReq("catch", "1"));
            ctx.handle(getAddPropertyReq("finally", "1"));
            ctx.handle("try");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("try", "2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("catch");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("catch", "2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            ctx.handle("finally");
            ctx.handle("batch");
            ctx.handle(getWritePropertyReq("finally", "2"));
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("run-batch");
            try {
                ctx.handle("end-try");
            } catch (CommandLineException e) {
                expectedEx = e;
                throw e; // cleanup
            }
        } catch(CommandLineException e) {
            cliOut.reset();
            ctx.handle(getReadPropertyReq("try"));
            assertEquals("1", getValue());
            cliOut.reset();
            ctx.handle(getReadPropertyReq("catch"));
            assertEquals("1", getValue());
            cliOut.reset();
            ctx.handle(getReadPropertyReq("finally"));
            assertEquals("1", getValue());
        } finally {
            ctx.handleSafe(getRemovePropertyReq("try"));
            ctx.handleSafe(getRemovePropertyReq("catch"));
            ctx.handleSafe(getRemovePropertyReq("finally"));
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }

    @Test
    public void testCommentOnlyCatchBlock() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle("echo try block");
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle("# commented line");
            ctx.handleSafe("end-try");
            ctx.handle("echo after-block");
            String out = cliOut.toString();
            assertTrue(out.contains("try block"));
            assertTrue(out.contains("after-block"));
            assertFalse(out.contains("system-property"));
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testCommentOnlyCatchAndFinallyBlock() throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.connectController();
            ctx.handle("try");
            ctx.handle("echo try block");
            ctx.handle(getReadNonexistingPropReq());
            ctx.handle("catch");
            ctx.handle("# commented line");
            ctx.handle("finally");
            ctx.handle("# commented line");
            ctx.handleSafe("end-try");
            ctx.handle("echo after-block");
            String out = cliOut.toString();
            assertTrue(out.contains("try block"));
            assertTrue(out.contains("after-block"));
            assertFalse(out.contains("system-property"));
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    @Test
    public void testTryInsideTry() throws Exception {
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        CommandLineException expectedEx = null;
        try {
            ctx.connectController();
            ctx.handle("try");
            try {
                ctx.handle("try");
            } catch (CommandLineException e) {
                expectedEx = e;
            }
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
        assertNotNull("expected exception", expectedEx);
    }
}
