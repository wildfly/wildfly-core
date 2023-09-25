/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import jakarta.inject.Inject;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ShutdownRestartTestCase {
    @Inject
    private ServerController serverController;

    @Test
    public void test() throws Exception {
        serverController.start();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().
                setController("remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        ctx.handle("command-timeout set 6000");
        ctx.handle("connect");

        Runnable restartTrigger = () -> {
            try {
                ctx.handle("shutdown --restart");
            } catch (CommandLineException e) {
                throw new RuntimeException(e);
            }
        };

        serverController.handleServerRestart(restartTrigger, TimeoutUtil.adjust(10000), false);
    }
}