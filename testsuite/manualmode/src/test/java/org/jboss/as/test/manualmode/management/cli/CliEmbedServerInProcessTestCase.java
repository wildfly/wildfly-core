/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
