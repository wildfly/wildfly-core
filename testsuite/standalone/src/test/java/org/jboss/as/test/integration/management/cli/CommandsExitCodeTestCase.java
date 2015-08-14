/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.cli;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

import java.io.IOException;

/**
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
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
