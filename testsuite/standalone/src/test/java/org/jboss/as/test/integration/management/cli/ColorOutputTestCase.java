/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import java.io.BufferedReader;
import java.io.StringReader;

import org.aesh.readline.util.Parser;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

@RunWith(WildFlyRunner.class)
public class ColorOutputTestCase {

    private static CliProcessWrapper cli;
    private static String hostAndPort = TestSuiteEnvironment.getServerAddress() + ":" + TestSuiteEnvironment.getServerPort();

    /**
     * Initialize CommandContext before all tests
     */
    @BeforeClass
    public static void init() {
        cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + hostAndPort);
        cli.executeInteractive();
    }

    /**
     * Terminate CommandContext after all tests are executed
     */
    @AfterClass
    public static void close() {
        cli.destroyProcess();
    }

    @Test
    public void requiredArguments() throws Exception {

        cli.clearOutput();
        cli.pushLineAndWaitForResults(":write-attribute(\t");
        String out = cli.getOutput();

        Assert.assertTrue("Required attribute is not colorized. The output was: " + out,
                out.contains("\u001B[1;95;109mname*\u001B[0m"));
    }

    @Test
    public void cliPrompt() {
        Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m/]",
                cli.getCurrentPrompt().trim());
    }

    @Test
    public void batchPrompt() throws Exception {

        cli.pushLineAndWaitForResults("batch");
        try {
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m/\u001B[;92;109m #\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("discard-batch");
        }
    }

    @Test
    public void ifPrompt() throws Exception {

        cli.pushLineAndWaitForResults("if outcome==failed of /system-property=test:read-resource");
        try {
            cli.pushLineAndWaitForResults("echo \"Not Exists\"");
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m/\u001B[;92;109m ...\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("end-if");
        }
    }

    @Test
    public void forPrompt() throws Exception {

        cli.pushLineAndWaitForResults("for PROP in :read-children-names(child-type=system-property)");
        try {
            cli.pushLineAndWaitForResults("echo $PROP");
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m/\u001B[;92;109m ...\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("done");
        }
    }

    @Test
    public void tryPrompt() throws Exception {

        cli.pushLineAndWaitForResults("try");
        try {
            cli.pushLineAndWaitForResults("echo \"Trying\"");
            Assert.assertEquals("[\u001B[;94;109mstandalone\u001B[0m@\u001B[;94;109m" + hostAndPort + " \u001B[0m/\u001B[;92;109m ...\u001B[0m]",
                    cli.getCurrentPrompt().trim());
        } finally {
            cli.pushLineAndWaitForResults("finally");
            cli.pushLineAndWaitForResults("end-try");
        }
    }

    /**
     * This is regression test for WFCORE-3849
     * @throws Exception
     */
    @Test
    public void longCommand() throws Exception {

        final int terminalWidth = 80;

        cli.pushLineAndWaitForResults("security enable-ssl-management --key-store-path=target/server.keystore.jks --key-store-password=secret --new-key-store-name=nks --new-key-manager-name=nkm --new-ssl-context-name=nsslctx");

        String printableChars = Parser.stripAwayAnsiCodes(cli.getOutput());
        try (BufferedReader reader = new BufferedReader(new StringReader(printableChars))) {
            String line = reader.readLine();
            // WFCORE-7130 workaround
            if (Runtime.version().feature() >= 24) {
                // Ignore JDK warnings about sun.misc.Unsafe deprecated method usages on JDK24+
                while (line.contains("WARNING: ")) {
                    line = reader.readLine();
                }
            }
            while (line.contains("Picked up JDK_JAVA_OPTIONS:") || line.contains("Picked up JAVA_TOOL_OPTIONS:")) {
                line = reader.readLine();
            }
            // Issue in WFCORE-3849 was that carriage return character was printed on wrong position.
            // This assert checks that the position is correct.
            Assert.assertEquals(line, 0, line.trim().length() % terminalWidth);
        }
    }

}
