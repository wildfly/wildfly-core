/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.scriptsupport.CLI;
import org.jboss.as.cli.scriptsupport.CLI.Result;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test various connection states of the CLI class.
 *
 * @author Jean-Francois Denise (jdenise@redhat.com)
 */
public class CLITestCase {

    @Test
    public void testConnectStatus() {

        // Offline instance
        CLI cli = CLI.newInstance();

        // Must fail to connect to remote management disabled server
        // WFCORE-1471 Uncomment when this bug is fixed
        //checkFail(() -> cli.connect(TestSuiteEnvironment.getServerAddress(),
        //        TestSuiteEnvironment.getServerPort(), null, null));

        // start an embedded server
        executeCommand(cli, "embed-server --std-out=echo");

        if (cli.getCommandContext().getControllerPort() > 0) {
            Assert.fail("Invalid port for embedded "
                    + cli.getCommandContext().getControllerPort());
        }

        // Enable management
        executeCommand(cli, "reload --admin-only=false");

        CommandContext offlineCtx = cli.getCommandContext();

        // switch to connected mode, should replace the context
        cli.connect(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(),
                null,
                null);

        if (cli.getCommandContext().getControllerPort()
                != TestSuiteEnvironment.getServerPort()) {
            Assert.fail("Context has not been switched");
        }

        CommandContext connectedCtx = cli.getCommandContext();
        if (connectedCtx == offlineCtx) {
            Assert.fail("Context has not been switched");
        }

        // Connected context command
        executeCommand(cli, "version");

        // Try to connect again, must fail
        checkFail(() -> cli.connect(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), null, null));

        cli.disconnect();

        if (!connectedCtx.isTerminated()) {
            Assert.fail("Context should have been terminated");
        }

        // Try to disconnect again, must fail
        checkFail(() -> cli.disconnect());

        // Reuse instance and reconnect then disconnect
        cli.connect(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(),
                null,
                null);
        executeCommand(cli, "version");
        cli.disconnect();

        // Start a clean CLI
        CLI cli2 = CLI.newInstance();

        // Make an invalid connect
        checkFail(() -> cli2.connect(TestSuiteEnvironment.getServerAddress(),
                123,
                null,
                null));

        // Make a valid connect
        cli2.connect(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(),
                null,
                null);
        executeCommand(cli2, "version");
        cli2.disconnect();
    }

    @Test
    public void testDisconnect() {
        CLI cli = CLI.newInstance();
        checkFail(() -> cli.disconnect());
    }

    private static void checkFail(Runnable runner) {
        boolean failed = false;
        try {
            runner.run();
        } catch (RuntimeException ex) {
            failed = true;
        }
        if (!failed) {
            Assert.fail("Should have failed");
        }
    }

    private static void executeCommand(CLI cli, String cmd) {
        Result res = cli.cmd(cmd);
        if (!res.isSuccess()) {
            Assert.fail("Invalid response " + res.getResponse().asString());
        }
    }
}
