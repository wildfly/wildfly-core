/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.integration.management.util;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;


/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 * @author Alexey Loubyansky <olubyans@redhat.com>
 */
public class CLIWrapper implements AutoCloseable {

    private final CommandContext ctx;

    private ByteArrayOutputStream consoleOut;

    /**
     * Creates new CLI wrapper.
     *
     * @throws Exception
     */
    public CLIWrapper() throws Exception {
        this(false);
    }

    /**
     * Creates new CLI wrapper. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @throws Exception
     */
    public CLIWrapper(boolean connect) throws Exception {
        this(connect, null);
    }

    /**
     * Creates new CLI wrapper using the host, port from the managementClient. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @throws Exception
     */
    public CLIWrapper(String mgmtAddress, int mgmtPort, boolean connect) throws Exception {
        this(connect, mgmtAddress, mgmtPort, null, -1);
    }

    /**
     * Creates new CLI wrapper. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @param cliAddress The default name of the property containing the cli address. If null the value of the {@code node0} property is
     * used, and if that is absent {@code localhost} is used
     */
    public CLIWrapper(boolean connect, String cliAddress) throws CliInitializationException {
        this(connect, cliAddress, null);
    }

    /**
     * Creates new CLI wrapper. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @param cliAddress The default name of the property containing the cli address. If null the value of the {@code node0} property is
     * used, and if that is absent {@code localhost} is used
     * @param consoleInput input stream to use for sending to the CLI, or {@code null} if the standard input stream should be used
     */
    public CLIWrapper(boolean connect, String cliAddress, InputStream consoleInput) throws CliInitializationException {
        this(connect, cliAddress, consoleInput, -1);
    }

    /**
     * Creates new CLI wrapper. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @param cliAddress The default name of the property containing the cli address. If null the value of the {@code node0} property is
     * used, and if that is absent {@code localhost} is used
     * @param consoleInput input stream to use for sending to the CLI, or {@code null} if the standard input stream should be used
     * @param connectionTimeout timeout of the CLI connection (in milliseconds)
     */
    public CLIWrapper(boolean connect, String cliAddress, InputStream consoleInput, int connectionTimeout) throws CliInitializationException {

        consoleOut = new ByteArrayOutputStream();
        System.setProperty("aesh.terminal","org.jboss.aesh.terminal.TestTerminal");
        ctx = CLITestUtil.getCommandContext(
                TestSuiteEnvironment.getServerAddress(), TestSuiteEnvironment.getServerPort(),
                consoleInput, consoleOut, connectionTimeout);

        if (!connect) {
            return;
        }
        Assert.assertTrue(sendConnect(cliAddress));
    }

    /**
     * Creates new CLI wrapper. If the connect parameter is set to true the CLI will connect to the server using
     * <code>connect</code> command.
     *
     * @param connect indicates if the CLI should connect to server automatically.
     * @param cliAddress The cli address, if null the value of the {@code node0} property is used, and if that is absent {@code localhost} is used
     * @param serverPort The cli port, if null the value of the {@code node0} property is used, and if that is absent {@code 9990} is used
     * @param consoleInput input stream to use for sending to the CLI, or {@code null} if the standard input stream should be used
     * @param connectionTimeout timeout of the CLI connection (in milliseconds)
     */
    public CLIWrapper(boolean connect, String cliAddress, Integer serverPort, InputStream consoleInput, int connectionTimeout) throws CliInitializationException {

        if(cliAddress == null)
            cliAddress = TestSuiteEnvironment.getServerAddress();
        if(serverPort == null)
            serverPort = TestSuiteEnvironment.getServerPort();
        consoleOut = new ByteArrayOutputStream();
        System.setProperty("aesh.terminal","org.jboss.aesh.terminal.TestTerminal");
        ctx = CLITestUtil.getCommandContext(
                cliAddress, serverPort,
                consoleInput, consoleOut, connectionTimeout);

        if (!connect) {
            return;
        }
        Assert.assertTrue(sendConnect());
    }

    public CommandContext getCommandContext() {
        return ctx;
    }

    public boolean isConnected() {
        return ctx.getModelControllerClient() != null;
    }

    /**
     * Sends a line with the connect command. This will look for the {@code node0} system property
     * and use that as the address. If the system property is not set {@code localhost} will
     * be used
     */
    public boolean sendConnect() {
        return sendConnect(null);
    }

    /**
     * Sends a line with the connect command.
     * @param cliAddress The address to connect to. If null it will look for the {@code node0} system
     * property and use that as the address. If the system property is not set {@code localhost} will
     * be used
     */
    public final boolean sendConnect(String cliAddress) {
        try {
            if (cliAddress!=null) {
                ctx.connectController(new URI("remote+http", null, cliAddress, TestSuiteEnvironment.getServerPort(), null, null, null).toString());
            }else{
                ctx.connectController();//use already configured ctx
            }
            return true;
        } catch (CommandLineException e) {
            e.printStackTrace();
            return false;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sends command line to CLI.
     *
     * @param line specifies the command line.
     * @param ignoreError if set to false, asserts that handling the line did not result in a {@link org.jboss.as.cli.CommandLineException}.
     *
     * @return true if the CLI is in a non-error state following handling the line
     */
    public boolean sendLine(String line, boolean ignoreError)  {
        consoleOut.reset();
        if(ignoreError) {
            ctx.handleSafe(line);
            return ctx.getExitCode() == 0;
        } else {
            try {
                ctx.handle(line);
            } catch (CommandLineException e) {
                StringWriter stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));
                Assert.fail(String.format("Failed to execute line '%s'%n%s", line, stackTrace.toString()));
            }
        }
        return true;
    }

     /**
     * Sends command line to CLI.
     *
     * @param line specifies the command line.
     * @throws org.jboss.as.cli.CommandLineException
     */
    public void sendLineForValidation(String line) throws CommandLineException  {
        consoleOut.reset();
        ctx.handle(line);
    }
    /**
     * Sends command line to CLI.
     *
     * @param line specifies the command line.
     */
    public void sendLine(String line) {
        sendLine(line, false);
    }

    /**
     * Reads the last command's output.
     *
     * @return next line from CLI output
     */
    public String readOutput()  {
        if(consoleOut.size() <= 0) {
            return null;
        }
        return new String(consoleOut.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    /**
     * Consumes all available output from CLI and converts the output to ModelNode operation format
     *
     * @return array of CLI output lines
     */
    public CLIOpResult readAllAsOpResult() throws IOException {
        if(consoleOut.size() <= 0) {
            return new CLIOpResult();
        }
        final ModelNode node = ModelNode.fromStream(new ByteArrayInputStream(consoleOut.toByteArray()));
        return new CLIOpResult(node);

    }

    /**
     * Sends quit command to CLI.
     */
    public synchronized void quit() {
        ctx.terminateSession();
    }

    /**
     * Returns CLI status.
     *
     * @return true if and only if the CLI has finished.
     */
    public boolean hasQuit() {
        return ctx.isTerminated();
    }

    public boolean isValidPath(String... node) {
        try {
            return Util.isValidPath(ctx.getModelControllerClient(), node);
        } catch (CommandLineException e) {
            Assert.fail("Failed to validate path: " + e.getLocalizedMessage());
            return false;
        }
    }

    protected String getUsername() {
        return Authentication.USERNAME;
    }

    protected String getPassword() {
        return Authentication.PASSWORD;
    }

    @Override
    public void close() throws Exception {
        if(!hasQuit())
            quit();
    }
}
