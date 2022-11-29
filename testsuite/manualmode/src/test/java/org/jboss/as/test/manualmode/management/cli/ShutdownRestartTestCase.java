/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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