/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;

import org.apache.commons.io.FileUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 * @author Alexey Loubyansky
 */
@RunWith(WildFlyRunner.class)
public class CliArgumentsTestCase {

    private static final String tempDir = TestSuiteEnvironment.getTmpDir();

    @Test
    public void testVersionArgument() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("JBOSS_HOME"));
        assertTrue(result, result.contains("Release"));
        assertTrue(result, result.contains("JAVA_HOME"));
        assertTrue(result, result.contains("java.version"));
        assertTrue(result, result.contains("java.vm.vendor"));
        assertTrue(result, result.contains("java.vm.version"));
        assertTrue(result, result.contains("os.name"));
        assertTrue(result, result.contains("os.version"));
    }

    @Test
    public void testVersionAsCommandArgument() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--command=version");
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("JBOSS_HOME"));
        assertTrue(result, result.contains("Release"));
        assertTrue(result, result.contains("JAVA_HOME"));
        assertTrue(result, result.contains("java.version"));
        assertTrue(result, result.contains("java.vm.vendor"));
        assertTrue(result, result.contains("java.vm.version"));
        assertTrue(result, result.contains("os.name"));
        assertTrue(result, result.contains("os.version"));
    }

    @Test
    public void testFileArgument() throws Exception {

        // prepare file
        File cliScriptFile = new File(tempDir, "testScript.cli");
        if (cliScriptFile.exists()) Assert.assertTrue(cliScriptFile.delete());
        FileUtils.writeStringToFile(cliScriptFile, "version" + TestSuiteEnvironment.getSystemProperty("line.separator"));

        // pass it to CLI
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--file=" + cliScriptFile.getAbsolutePath());
        final String result = cli.executeNonInteractive();
        assertNotNull(result);
        assertTrue(result, result.contains("JBOSS_HOME"));
        assertTrue(result, result.contains("Release"));
        assertTrue(result, result.contains("JAVA_HOME"));
        assertTrue(result, result.contains("java.version"));
        assertTrue(result, result.contains("java.vm.vendor"));
        assertTrue(result, result.contains("java.vm.version"));
        assertTrue(result, result.contains("os.name"));
        assertTrue(result, result.contains("os.version"));

        cliScriptFile.delete();
    }

    @Test
    public void testConnectArgument() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--commands=connect,version,ls")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort()));

        final String result = cli.executeNonInteractive();

        assertNotNull(result);
        assertTrue(result, result.contains("JBOSS_HOME"));
        assertTrue(result, result.contains("Release"));
        assertTrue(result, result.contains("JAVA_HOME"));
        assertTrue(result, result.contains("java.version"));
        assertTrue(result, result.contains("java.vm.vendor"));
        assertTrue(result, result.contains("java.vm.version"));
        assertTrue(result, result.contains("os.name"));
        assertTrue(result, result.contains("os.version"));

        assertTrue(result.contains("subsystem"));
        assertTrue(result.contains("extension"));
    }

    @Test
    public void testWrongController() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addCliArgument("--connect")
                .addCliArgument("--controller=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort() - 1))
                .addCliArgument("quit");
        cli.executeNonInteractive();

        int exitCode = cli.getProcessExitValue();
        assertTrue(exitCode != 0);
    }

    @Test
    public void testMisspelledParameter() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
           .addCliArgument("--connect")
           .addCliArgument("--controler=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort() - 1))
           .addCliArgument("quit");
        cli.executeNonInteractive();

        int exitCode = cli.getProcessExitValue();
        String output = cli.getOutput();
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line = reader.readLine();
            while (line.contains("Picked up JDK_JAVA_OPTIONS:") || line.contains("Picked up JAVA_TOOL_OPTIONS:")) {
                line = reader.readLine();
            }
            assertEquals("Unknown argument: --controler=" + TestSuiteEnvironment.getServerAddress() + ":" + (TestSuiteEnvironment.getServerPort() - 1), line);
        }
        assertTrue(exitCode != 0);
    }
}
