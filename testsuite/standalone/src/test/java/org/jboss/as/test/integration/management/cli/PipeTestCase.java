/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.hamcrest.CoreMatchers;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
/**
 * @author kanovotn@redhat.com
 */
public class PipeTestCase {

    private static ByteArrayOutputStream cliOut;

    @BeforeClass
    public static void setup() throws Exception {
        cliOut = new ByteArrayOutputStream();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        cliOut = null;
    }

    @Test
    public void testPipeWithGrep() throws Exception {
        testCommand("version | grep \"^java\\.vm\"", "java.vm.version", false);
    }

    @Test
    public void testPipeWithGrepAndRedirect() throws Exception {
        String filename = "testFile";
        CommandContext ctx = CLITestUtil.getCommandContext(TestSuiteEnvironment.getServerAddress(),
                TestSuiteEnvironment.getServerPort(), System.in, System.out);
        Path tempFile = Files.createTempFile(filename, ".tmp");
        String tempFileStringPath = tempFile.toString();

        try {
            ctx.handle("version | grep \"^java\\.vm\" >> " + tempFileStringPath);
            Assert.assertTrue("The file " + tempFileStringPath + " doesn't have expected content.",
                    checkFileContains(tempFile, "java.vm.version"));
        } finally {
            ctx.terminateSession();
            Files.delete(tempFile);
        }
    }

    @Test
    public void testPipeWithLongOutput() throws Exception {
        testCommand("help deploy | grep \"deploy \\(\\(file_path\"", "deploy ((file_path", false);
    }

    @Test
    public void testPipeWithUnsupportedCommand() throws Exception {
        testCommand("version | echo test", "test", true);
    }

    @Test(expected = CommandLineException.class)
    public void testPipeWithNonExistingCommand() throws Exception {
        testCommand("version | nonsense", "Unexpected command", false);
    }

    @Test
    public void testPipeWithCommandNotSupportingPipeOuput() throws Exception {
        testCommand("version | pwd", "/", true);
    }

    private void testCommand(String cmd, String expectedOutput, boolean exactMatch) throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.handle(cmd);
            String output = cliOut.toString(StandardCharsets.UTF_8.name());
            if (exactMatch) {
                assertThat("Wrong results of the command - " + cmd, output.trim(), CoreMatchers.is(CoreMatchers.equalTo(expectedOutput)));
            } else {
                assertThat("Wrong results of the command - " + cmd, output, CoreMatchers.containsString(expectedOutput));
            }
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }

    private boolean checkFileContains(Path path, String expectedString) {

        String currentLine = null;
        boolean found = false;

        try (BufferedReader br = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            while ((currentLine = br.readLine()) != null) {
                if (currentLine.contains(expectedString)) {
                    found = true;
                    break;
                }
            }
        } catch (IOException e) {
            Assert.fail("Failed to read content of the test file.");
        }
        return found;
    }
}
