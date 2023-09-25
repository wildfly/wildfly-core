/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

import java.io.IOException;

/**
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class CommandsExitCodeTestCase {

    private static final String PROP_NAME = "cli-arg-test";
    private static final String SET_PROP_COMMAND = "/system-property=" + PROP_NAME + ":add(value=set)";
    private static final String GET_PROP_COMMAND = "/system-property=" + PROP_NAME + ":read-resource";
    private static final String REMOVE_PROP_COMMAND = "/system-property=" + PROP_NAME + ":remove";

    /**
     * Tests that the exit code is 0 value after a successful operation
     *
     * @throws Exception
     */
    @Test
    public void testSuccess() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument(SET_PROP_COMMAND);
        cli.executeNonInteractive();
        int exitCode = cli.getProcessExitValue();
        if (exitCode == 0) {
            cli = new CliProcessWrapper()
                    .addCliArgument("--connect")
                    .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                    .addCliArgument(REMOVE_PROP_COMMAND);
            cli.executeNonInteractive();
        }
        assertEquals(0, exitCode);
    }

    /**
     * Tests that the exit code is not 0 after a failed operation.
     *
     * @throws Exception
     */
    @Test
    public void testFailure() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument(GET_PROP_COMMAND);
        cli.executeNonInteractive();
        assertTrue(cli.getProcessExitValue() != 0);
    }

    /**
     * Tests that commands following a failed command aren't executed.
     *
     * @throws Exception
     */
    @Test
    public void testValidCommandAfterInvalidCommand() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument('\"' + GET_PROP_COMMAND + ',' + SET_PROP_COMMAND + '\"');
        cli.executeNonInteractive();
        int exitCode = cli.getProcessExitValue();
        if (exitCode == 0) {
            cli = new CliProcessWrapper()
                    .addCliArgument("--connect")
                    .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                    .addCliArgument(REMOVE_PROP_COMMAND);
            cli.executeNonInteractive();
        } else {
            cli = new CliProcessWrapper()
                    .addCliArgument("--connect")
                    .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                    .addCliArgument(GET_PROP_COMMAND);
            cli.executeNonInteractive();
            assertTrue(cli.getProcessExitValue() != 0);        }
        assertTrue("Output: '" + cli.getOutput() + "'", exitCode != 0);
    }

    /**
     * Tests that commands preceding a failed command are't affected by the failure.
     *
     * @throws Exception
     */
    @Test
    public void testValidCommandBeforeInvalidCommand() throws IOException {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument(SET_PROP_COMMAND + ",bad-wrong-illegal");
        cli.executeNonInteractive();
        int exitCode = cli.getProcessExitValue();

        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument(GET_PROP_COMMAND);
        cli.executeNonInteractive();
        assertTrue(cli.getProcessExitValue() == 0);

        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()))
                .addCliArgument(REMOVE_PROP_COMMAND);
        cli.executeNonInteractive();

        assertTrue("Output: '" + cli.getOutput() + "'", exitCode != 0);
    }
}
