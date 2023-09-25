/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class CtrlCTestCase {

    @Test
    public void ctrlCTestCase() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper();

        try {
            cli.executeInteractive();

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput() + "'", closed);
                    assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 0, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

    @Test
    public void userCtrlCTestCase() throws IOException, InterruptedException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--no-local-auth");

        try{
            boolean promptFound = cli.executeInteractive("Username");
            assertTrue("Prompt isn't asking for username. Output: '" + cli.getOutput() + "'", promptFound);

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput()+ "'", closed);
            assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 1, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

    @Test
    public void passCtrlCTestCase() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument("--no-local-auth");

        try{
            boolean promptFound = cli.executeInteractive("Username");
            assertTrue("Prompt isn't asking for username. Output: '" + cli.getOutput() + "'", promptFound);

            promptFound = cli.pushLineAndWaitForResults("bob", "Password");
            assertTrue("Prompt isn't requesting password. Output: '" + cli.getOutput() + "'", promptFound);

            boolean closed = cli.ctrlCAndWaitForClose();

            assertTrue("Process did not terminate correctly. Output: '" + cli.getOutput() + "'", closed);
            assertEquals("Cli process closed with unexpected exit value: " + cli.getProcessExitValue(), 1, cli.getProcessExitValue());
        }finally{
            cli.destroyProcess();
        }
    }

}
