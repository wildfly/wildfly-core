/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.Util;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Emulate jboss-cli.sh process with input coming from a file. This activates
 * aesh-readline ExtTerminal in an intensive multi-thread context.
 *
 */
@RunWith(WildFlyRunner.class)
public class FileInputTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void test() throws Exception {
        // test with a new CLI process and variable line length
        for (int i = 1; i < 10; i++) {
            String output = null;
            CliProcessWrapper cli = new CliProcessWrapper()
                    .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                    .addCliArgument("--controller="
                            + TestSuiteEnvironment.getServerAddress() + ":"
                            + TestSuiteEnvironment.getServerPort())
                    .addCliArgument("--connect");
            try {
                cli.executeInteractive();
                StringBuilder builder = new StringBuilder();
                // Create a bunch of commands send them all in a single line.
                for (int j = 0; j < i * 100; j++) {
                    builder.append(":read-attribute(name=name" + j + ")").append(Util.isWindows() ? "\r\n" : "\n");
                }
                builder.append("quit").append(Util.isWindows() ? "\r\n" : "\n");
                boolean ret = cli.pushLineAndWaitForClose(builder.toString());
                output = cli.getOutput();
                assertTrue("Process not terminated correctly: ", ret);
                assertEquals("Invalid exit value " + cli.getProcessExitValue(), 0, cli.getProcessExitValue());
                // Check that all command have been executed
                assertTrue(output.contains("quit"));
                for (int j = 0; j < i * 100; j++) {
                    assertTrue(output.contains("name" + j));
                }
            } catch (Throwable ex) {
                System.out.println("Iteration " + i + " Failure " + ex);
                System.out.println("CLI output:\n " + output == null ? cli.getOutput() : output);
                throw ex;
            } finally {
                cli.destroyProcess();
            }
        }
    }

}
