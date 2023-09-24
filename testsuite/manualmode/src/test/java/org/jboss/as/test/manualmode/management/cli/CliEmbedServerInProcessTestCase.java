/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.management.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.test.integration.management.cli.CliProcessWrapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Start an embedded server in the CLI remote process.
 * embedded server updates System I/O in a way that impacts CLI output.
 * @author jdenise@redhat.com
 */
public class CliEmbedServerInProcessTestCase {

    @Rule
    public final TemporaryFolder temporaryUserHome = new TemporaryFolder();

    @Test
    public void testEmbedServerInRemoteCliProcess() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--no-color-output");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            boolean ret = cli.pushLineAndWaitForResults("embed-server", "[standalone@embedded /]");
            assertTrue("Invalid output " + cli.getOutput(), ret);
            ret = cli.pushLineAndWaitForResults("stop-embedded-server", "[disconnected /]");
            assertTrue("Invalid output " + cli.getOutput(), ret);
        } finally {
            cli.destroyProcess();
        }
    }

    @Test
    public void testEmbedServerReloadProcess() throws Exception {
        CliProcessWrapper cli = new CliProcessWrapper()
                .addJavaOption("-Duser.home=" + temporaryUserHome.getRoot().toPath().toString())
                .addCliArgument("--no-color-output");
        try {
            cli.executeInteractive();
            cli.clearOutput();
            sendLine(cli, "embed-server");

            // Reload the server, note this will reload and start the runtime which is important as we need the logging
            // subsystem to execute runtime operations
            sendLine(cli, "reload");

            // Clear the output and send a command and check the output
            cli.clearOutput();
            sendLine(cli, "echo hello");
            final String output = cli.getOutput();
            // We should only end up with 3 lines;
            // echo hello
            // hello
            // [statandalone@embedded /]
            final List<String> lines = readLines(cli.getOutput());
            assertEquals(String.format("Expected 3 lines got %d: %s", lines.size(), output), 3, lines.size());
            assertEquals("Expected hello to be at line 2 found " + lines.get(1), "hello", lines.get(1));

            final boolean result = cli.pushLineAndWaitForResults("stop-embedded-server", "[disconnected /]");
            assertTrue("Invalid output " + cli.getOutput(), result);
        } finally {
            cli.destroyProcess();
        }
    }

    private void sendLine(final CliProcessWrapper cli, final String command) throws IOException {
        final boolean result = cli.pushLineAndWaitForResults(command, "[standalone@embedded /]");
        assertTrue(String.format("Command \"%s\" failed: %s", command, cli.getOutput()), result);
    }

    private static List<String> readLines(final String output) throws IOException {
        final List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }
}
