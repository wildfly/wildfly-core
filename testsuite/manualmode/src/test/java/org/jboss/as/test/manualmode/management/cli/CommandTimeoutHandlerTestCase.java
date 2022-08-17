/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.manualmode.management.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.inject.Inject;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.LsHandler;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.cli.impl.CommandExecutor;
import org.jboss.as.cli.impl.CommandExecutor.TimeoutCommandContext;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.ExtensionUtils;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.junit.After;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;
import static org.junit.Assert.assertEquals;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CommandTimeoutHandlerTestCase {

    @Inject
    private ServerController container;

    private interface Action {

        void doIt(CommandContext ctx) throws CommandLineException;
    }

    private class CommandHandlerWrapper implements CommandHandler {

        private final CommandHandler handler;
        private final Action action;

        CommandHandlerWrapper(CommandContext ctx, CommandHandler handler, Action action) {
            this.handler = handler;
            this.action = action;
        }

        @Override
        public boolean isAvailable(CommandContext ctx) {
            return handler.isAvailable(ctx);
        }

        @Override
        public boolean isBatchMode(CommandContext ctx) {
            return handler.isBatchMode(ctx);
        }

        @Override
        public void handle(CommandContext ctx) throws CommandLineException {
            action.doIt(ctx);
        }

        @Override
        public CommandArgument getArgument(CommandContext ctx, String name) {
            return handler.getArgument(ctx, name);
        }

        @Override
        public boolean hasArgument(CommandContext ctx, String name) {
            return handler.hasArgument(ctx, name);
        }

        @Override
        public boolean hasArgument(CommandContext ctx, int index) {
            return handler.hasArgument(ctx, index);
        }

        @Override
        public Collection<CommandArgument> getArguments(CommandContext ctx) {
            return handler.getArguments(ctx);
        }
    }
    private static final int CONFIG_TIMEOUT = 1;
    private CommandContext ctx;
    private static final ByteArrayOutputStream consoleOutput = new ByteArrayOutputStream();

    @Before
    public void beforeTest() throws CliInitializationException, IOException, CommandLineException {
        container.start();
        consoleOutput.reset();
        ExtensionUtils.createExtensionModule("org.wildfly.extension.blocker-test", BlockerExtension.class,
                EmptySubsystemParser.class.getPackage());
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().
                setInitConsole(true).setConsoleOutput(consoleOutput).
                setController("remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).
                setCommandTimeout(CONFIG_TIMEOUT).build();
        ctx = CommandContextFactory.getInstance().newCommandContext(config);
        ctx.handle("command-timeout reset default");
        ctx.handle("connect");
        ctx.handle("/extension=org.wildfly.extension.blocker-test:add");
        ctx.handle("command-timeout reset config");
    }

    @After
    public void afterTest() throws Exception {
        // Just in case something went wrong.
        try {
            ctx.handle("command-timeout reset default");
            if (ctx.isBatchMode()) {
                ctx.handle("discard-batch");
            }

            checkInactiveOperations();

            ctx.handle("/extension=org.wildfly.extension.blocker-test:remove");

        } finally {
            ctx.terminateSession();
            container.stop();
            ExtensionUtils.deleteExtensionModule("org.wildfly.extension.blocker-test");
        }
    }

    @Test
    public void testTimeoutHandler() throws CommandLineException {
        int t = CONFIG_TIMEOUT;
        checkTimeout(t, ctx);

        t = 4;
        ctx.handle("command-timeout set " + t);
        checkTimeout(t, ctx);

        t = 0;
        ctx.handle("command-timeout set " + t);
        checkTimeout(t, ctx);

        ctx.handle("command-timeout reset config");
        checkTimeout(CONFIG_TIMEOUT, ctx);

        ctx.handle("command-timeout reset default");
        checkTimeout((short) 0, ctx);

        t = 4;
        ctx.handle("command-timeout set " + t);
        checkTimeout(t, ctx);

        try {
            ctx.handle("command-timeout set a");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            // XXX OK, expected.
        }

        try {
            ctx.handle("command-timeout set -1");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            // XXX OK, expected.
        }

        try {
            ctx.handle("command-timeout reset toto");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            // XXX OK, expected.
        }
    }

    @Test
    public void testReloadShutdown() throws CommandLineException {
        ctx.handle("command-timeout set 60");
        ctx.handle("reload");
    }

    @Test
    public void testExecutionTimeout() throws CommandLineException {
        ctx.setCommandTimeout((short) 2);
        ctx.handle("take-your-time 1000");

        try {
            ctx.handle("take-your-time 3000 --local");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            // XXX OK, expected
        }

        try {
            ctx.handle("/subsystem=blocker-test:block(block-point=MODEL,block-time=400000)");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            // XXX OK, expected
        }

        ctx.handle("command-timeout set 2");
        ctx.handle("take-your-time 1000");
    }

    @Test
    public void testBatchTimeout() throws CommandLineException {
        ctx.handle("command-timeout set 2");
        ctx.handle("batch");
        ctx.handle("take-your-time 2000 --local");
        ctx.handle("take-your-time 2000");
        try {
            ctx.handle("run-batch");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().equals("Timeout exception for run-batch")) {
                throw new CommandLineException("Not excepected exception ", ex);
            }
            // XXX OK expected.
        }

        // For now, keep the batch after a timeout so must discard it
        ctx.handle("discard-batch");
    }

    @Test
    public void testIfTimeout() throws CommandLineException {
        String prop = "test-" + System.currentTimeMillis();

        ctx.handle("/system-property=" + prop + ":add(value=false)");
        // This timeout is for each command
        ctx.handle("command-timeout set 3");

        ctx.handle("if result.value==\"true\" of /system-property=" + prop + ":read-resource");
        ctx.handle(":never-called-or-failed-1");
        ctx.handle("else");
        ctx.handle("take-your-time 2000");
        ctx.handle("take-your-time 2000");
        ctx.handle("end-if");

        // inside an if block, timeout exception stop the block execution
        ctx.handle("if result.value==\"true\" of /system-property=" + prop + ":read-resource");
        ctx.handle(":never-called-or-failed-2");
        ctx.handle("else");
        // This one will fail
        ctx.handle("command-timeout set 2");
        ctx.handle("take-your-time 40000");
        ctx.handle("/system-property=" + prop + ":write-attribute(name=value, value=true)");
        try {
            ctx.handle("end-if");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().equals("Timeout exception for take-your-time 40000")) {
                throw new CommandLineException("Not expected exception ", ex);
            }
        }

        //Check that the property has not been set to true.
        ctx.handle("if result.value==\"true\" of /system-property=" + prop + ":read-resource");
        ctx.handle(":never-called-or-failed-3");
        ctx.handle("end-if");

        //Check that a batch that timeout in an if block stops execution
        ctx.handle("if result.value==\"false\" of /system-property=" + prop + ":read-resource");
        ctx.handle("command-timeout set 2");
        // This one should fail
        ctx.handle("batch");
        ctx.handle("take-your-time 20000");
        ctx.handle("take-your-time 20000");
        ctx.handle("run-batch");
        // This one should not be executed
        ctx.handle("/system-property=" + prop + ":write-attribute(name=value, value=true)");
        try {
            ctx.handle("end-if");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().equals("Timeout exception for run-batch")) {
                throw new CommandLineException("Not expected exception ", ex);
            }
        }

        //Check that the property has not been added.
        ctx.handle("if result.value==\"true\" of /system-property=" + prop + ":read-resource");
        ctx.handle(":never-called-or-failed-4");
        ctx.handle("end-if");

        // Timeout in condition.
        ctx.handle("command-timeout set 2");
        ctx.handle("if result.value!=true of take-your-time 40000");
        ctx.handle("/system-property=" + prop + ":write-attribute(name=value, value=true)");
        ctx.handle("else");
        ctx.handle("/system-property=" + prop + ":write-attribute(name=value, value=true)");
        try {
            ctx.handle("end-if");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().equals("Timeout exception for if condition")) {
                throw new CommandLineException("Not expected exception ", ex);
            }
        }

        //Check that the property has not been set.
        ctx.handle("if result.value==\"true\" of /system-property=" + prop + ":read-resource");
        ctx.handle(":never-called-or-failed-5");
        ctx.handle("end-if");

        ctx.handle("/system-property=" + prop + ":remove");
    }

    @Test
    public void testTryTimeout() throws CommandLineException {
        String catchProp = "catch-" + System.currentTimeMillis();
        String tryProp = "try-" + System.currentTimeMillis();
        String finallyProp = "finally-" + System.currentTimeMillis();

        // This timeout is for each command
        ctx.handle("command-timeout set 2");
        ctx.handle("try");
        ctx.handle("take-your-time 40000");
        ctx.handle("/system-property=" + tryProp + ":add(value=true)");
        ctx.handle("catch");
        ctx.handle("/system-property=" + catchProp + ":add(value=true)");
        ctx.handle("end-try");

        // check that catch has been called.
        ctx.handle("if result.value!=\"true\" of /system-property=" + catchProp + ":read-resource");
        ctx.handle(":never-called-or-failed-1");
        ctx.handle("end-if");

        //Check that the property has not been added by try.
        ctx.handle("if result.value==\"true\" of /system-property=" + tryProp + ":read-resource");
        ctx.handle(":never-called-or-failed-2");
        ctx.handle("end-if");

        ctx.handle("try");
        ctx.handle("/system-property=" + tryProp + ":add(value=true)");
        ctx.handle("catch");
        ctx.handle("/system-property=" + catchProp + ":add(value=true)");
        ctx.handle("finally");
        ctx.handle("take-your-time 40000");
        ctx.handle("/system-property=" + finallyProp + ":add(value=true)");
        try {
            ctx.handle("end-try");
            throw new RuntimeException("Should have failed");
        } catch (CommandLineException ex) {
            if (!ex.getMessage().equals("Timeout exception for take-your-time 40000")) {
                throw new CommandLineException("Not expected exception ", ex);
            }
        }

        // check that try has been called.
        ctx.handle("if result.value!=\"true\" of /system-property=" + tryProp + ":read-resource");
        ctx.handle(":never-called-or-failed-3");
        ctx.handle("end-if");

        //Check that the property has not been added by finally.
        ctx.handle("if result.value==\"true\" of /system-property=" + finallyProp + ":read-resource");
        ctx.handle(":never-called-or-failed-4");
        ctx.handle("end-if");

        ctx.handle("/system-property=" + catchProp + ":remove");
        ctx.handle("/system-property=" + tryProp + ":remove");
    }

    @Test
    public void testForTimeout() throws CommandLineException {
        for (int i = 0; i < 10; i++) {
            ctx.handle("/system-property=prop" + i + ":add(value=true)");
        }
        try {
            // This timeout is for each command, not for the whole for block.
            ctx.handle("command-timeout set 2");
            ctx.handle("for propName in :read-children-names(child-type=system-property");
            ctx.handle("take-your-time 1000");
            ctx.handle("done");
        } finally {
            ctx.handle("for propName in :read-children-names(child-type=system-property");
            ctx.handle("/system-property=$propName:remove");
            ctx.handle("done");
        }
    }

    @Test
    public void testForTimeout2() throws CommandLineException {
        for (int i = 0; i < 10; i++) {
            ctx.handle("/system-property=prop" + i + ":add(value=true)");
        }
        try {
            // This timeout is for each command, not for the whole for block.
            ctx.handle("command-timeout set 2");
            ctx.handle("for propName in :read-children-names(child-type=system-property");
            ctx.handle("take-your-time 4000");
            try {
                ctx.handle("done");
                throw new RuntimeException("Should have failed");
            } catch (Exception ex) {
                // XXX OK, expected.
                if (!ex.getMessage().equals("Timeout exception for take-your-time 4000")) {
                    throw new CommandLineException("Not expected exception ", ex);
                }
            }
        } finally {
            ctx.handle("for propName in :read-children-names(child-type=system-property");
            ctx.handle("/system-property=$propName:remove");
            ctx.handle("done");
        }
    }

    @Test
    public void testComandExecutor() throws Exception {
        CommandExecutor executor = new CommandExecutor(ctx);
        CommandHandler ls = new LsHandler(ctx);
        ctx.setCommandTimeout(100);
        // Required in order for the ctx to be in sync when calling the handler directly.
        ctx.handle("ls");
        {
            List<Boolean> holder = new ArrayList<>();
            CommandHandlerWrapper wrapper = new CommandHandlerWrapper(ctx, ls, (context) -> {
                holder.add(true);
                TimeoutCommandContext tc = (TimeoutCommandContext) context;
                tc.setLastHandlerTask(null);
                ls.handle(context);
                Future<?> future = tc.getLastTask();
                if (future == null || !future.isDone()) {
                    throw new CommandLineException("Future is not done " + future);
                }
            });
            executor.execute(wrapper, 100, TimeUnit.SECONDS);
            if (holder.size() != 1) {
                throw new Exception("Handler not called");
            }
        }
        {
            List<Object> holder = new ArrayList<>();
            CommandHandlerWrapper wrapper = new CommandHandlerWrapper(ctx, ls, (context) -> {
                TimeoutCommandContext tc = (TimeoutCommandContext) context;
                tc.setLastHandlerTask(null);
                try {
                    long sleep = TimeoutUtil.adjust(1000);
                    Thread.sleep(sleep);
                    holder.add(null);
                } catch (InterruptedException ex) {
                    holder.add(ex);
                    Thread.currentThread().interrupt();
                }
                try {
                    ls.handle(context);
                    holder.add(null);
                } catch (Exception ex) {
                    // Expecting a timeout exception,
                    // the task has already been canceled.
                    holder.add(ex);
                }
                Future<?> future = tc.getLastTask();
                holder.add(future);
            });
            try {
                executor.execute(wrapper, 100, TimeUnit.MILLISECONDS);
                throw new RuntimeException("Should have failed");
            } catch (TimeoutException ex) {
                // XXX OK expected.
            }
            // Wait for the task to terminate and check the various steps.
            int waitTime = 1000;
            while (holder.size() != 3 && waitTime > 0) {
                Thread.sleep(100);
                waitTime -= 100;
            }
            if (holder.size() != 3) {
                throw new Exception("Task didn't terminate");
            }
            if (holder.get(0) == null) {
                throw new Exception("Task thread not interrupted");
            }
            if (holder.get(1) == null) {
                throw new Exception("Ls has not timeouted");
            }
            if (holder.get(2) != null) {
                throw new Exception("Future task is not null. Steps: "
                        + holder);
            }
        }
    }

    private static void checkTimeout(int expected,
            CommandContext ctx) throws CommandLineException {
        consoleOutput.reset();
        ctx.handle("command-timeout get");
        String output = consoleOutput.toString();
        assertEquals("Original Output --" + consoleOutput.toString() + "--",
                "" + expected + (Util.isWindows() ? "\r\n" : "\n"), output);
        assertEquals(ctx.getCommandTimeout(), expected);
    }

    private void checkInactiveOperations() throws Exception {
        // Check that no operation is still running
        ModelNode request = ctx.buildRequest("/core-service=management/"
                + "service=management-operations:read-children-resources(child-type=active-operation)");
        ModelNode mn = ctx.getModelControllerClient().execute(request);
        String result = mn.get(Util.RESULT).toString();
        if (result.contains("blocker-test")) {
            throw new Exception("Some requests are still running: " + result);
        }
    }
}
