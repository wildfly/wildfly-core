/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.test.integration.management.util.CLITestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author kanovotn@redhat.com
 */
public class GrepTestCase {

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
    public void testGrepHelp() throws Exception {
        testCommand("grep --help", "SYNOPSIS", false);
    }

    @Test
    public void testGrepIgnoreCase() throws Exception {
        testCommand("echo ABcd | grep -i \"ABCD\"", "ABcd", true);
    }

    @Test
    public void testGrepWithoutArguments() throws Exception {
        testCommand("grep", "SYNOPSIS", false);
    }

    @Test
    public void testGrepWithoutInput() throws Exception {
        testCommand("grep java", "no file or input given", false);
    }

    @Test
    public void testGrepWithInputFromFile() throws Exception {
        ByteArrayOutputStream cliOut = new ByteArrayOutputStream();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        Path tempFile = Files.createTempFile("tempFile", ".tmp");
        String tempFileStringPath = tempFile.toString();
        try {
            ctx.handle("version >" + tempFileStringPath);

            ctx.handle("grep java.version " + tempFileStringPath);
            String output = cliOut.toString(StandardCharsets.UTF_8.name());
            assertTrue("Wrong results of the grep command", output.contains("java.version"));
        } finally {
            ctx.terminateSession();
            cliOut.close();
            Files.delete(tempFile);
        }
    }

    @Test
    public void testGrepWithInputNonExistingFile() throws Exception {
        String fileName = "testNoneExistingFile";
        testCommand("grep java.version " + fileName, "No such file or directory", false);
    }

    @Test
    public void testGrepWithInvalidPattern() throws Exception {
        testCommand("grep \\.?\\(\\/1\\)", "invalid pattern", false);
    }

    @Test(expected = OperationFormatException.class)
    public void testGrepWithInvalidParameter() throws Exception {
        testCommand("grep --", "'' is not a valid parameter name", false);
    }

    @Test(expected = CommandFormatException.class)
    public void testGrepWithNonExistingParameter() throws Exception {
        testCommand("grep --mo", "The option --mo is unknown.", false);
    }

    @Test
    public void testGrepStringWithDoubleQuotes() throws Exception {
        testCommand("echo \"String with double quotes\" | grep \"String with double quotes\"",
                "\"String with double quotes\"", true);
    }

    @Test
    public void testGrepStringWithDoubleQuotesBackslashes() throws Exception {
        testCommand("echo \"\"String with double quotes\"\" | grep \"\\\"String with double quotes\"\\\"",
                "\"\"String with double quotes\"\"", true);
    }

    @Test
    public void testGrepInvalidExpressionWithBracketsSlash() throws Exception {
        testCommand("echo a | grep \"\\((a))\"", "invalid pattern", false);
    }

    @Test
    public void testGrepExpressionWithBrackets() throws Exception {
        testCommand("echo (a) | grep (\\(a\\))", "(a)", true);
    }

    @Test
    public void testGrepExpressionWithBracketsDoubleQuotes() throws Exception {
        testCommand("echo (a) | grep \"(\\(a\\))\"", "(a)", true);
    }

    @Test
    public void testGrepExpressionWithQuestionMark() throws Exception {
        testCommand("echo test?test | grep .*\\?", "test?test", true);
    }

    @Test
    public void testGrepExpressionWithPipe() throws Exception {
        testCommand("echo test\\|test | grep .*\\|", "test|test", true);
    }

    @Test
    public void testGrepExpressionWithDollar() throws Exception {
        testCommand("echo test\\$test | grep .*\\$", "test$test", true);
    }

    @Test
    public void testGrepCountMatches() throws Exception {
        testCommand("echo foo | grep f --count", "1", true);
    }

    @Test
    public void testGrepLineNumber() throws Exception {
        testCommand("echo foo | grep f --line-number", "1: foo", true);
    }

    private void testCommand(String cmd, String expectedOutput, boolean exactMatch) throws Exception {
        cliOut.reset();
        final CommandContext ctx = CLITestUtil.getCommandContext(cliOut);
        try {
            ctx.handle(cmd);
            String output = cliOut.toString(StandardCharsets.UTF_8.name());
            if (exactMatch) {
                assertEquals("Wrong results of the grep command", expectedOutput, output.trim());
            } else {
                assertTrue("Wrong results of the grep command", output.contains(expectedOutput));
            }
        } finally {
            ctx.terminateSession();
            cliOut.reset();
        }
    }
}
