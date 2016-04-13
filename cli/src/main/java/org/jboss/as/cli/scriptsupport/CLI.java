/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.cli.scriptsupport;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.dmr.ModelNode;

/**
 * This class is intended to be used with JVM-based scripting languages. It acts
 * as a facade to the CLI public API, providing a single class that can be used
 * to connect, run CLI commands, and disconnect. It also removes the need to
 * catch checked exceptions.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class CLI {

    private CommandContext ctx;

    private CLI() { // only allow new instances from newInstance() method.
        initOfflineContext();
    }

    /**
     * Create a new CLI instance.
     *
     * @return The CLI instance.
     */
    public static CLI newInstance() {
        return new CLI();
    }

    /**
     * Return the CLI CommandContext. This allows a script developer full access
     * to CLI facilities if needed. CommandContext can mute during CLI lifetime.
     * An unconnected CLI instance has a context that handles offline commands.
     * When connected to a server, the original context is replaced with a
     * context allowing to interact with the remote server.
     *
     * @return The CommandContext.
     */
    public CommandContext getCommandContext() {
        return ctx;
    }

    /**
     * Connect to the server using the default host and port.
     */
    public void connect() {
        doConnect(() -> {
            return CommandContextFactory.getInstance().newCommandContext();
        });
    }

    /**
     * Connect to the server using the default host and port.
     *
     * @param username The user name for logging in.
     * @param password The password for logging in.
     */
    public void connect(String username, char[] password) {
        doConnect(() -> {
            return CommandContextFactory.getInstance().
                    newCommandContext(username, password);
        });
    }

    /**
     * Connect to the server using a specified host and port.
     *
     * @param controllerHost The host name.
     * @param controllerPort The port.
     * @param username The user name for logging in.
     * @param password The password for logging in.
     */
    public void connect(String controller, String username, char[] password) {
        connect(controller, username, password, null);
    }

    /**
     * Connect to the server using a specified host and port.
     *
     * @param controller
     * @param username The user name for logging in.
     * @param clientBindAddress
     * @param password The password for logging in.
     */
    public void connect(String controller, String username, char[] password,
            String clientBindAddress) {
        doConnect(() -> {
            return CommandContextFactory.getInstance().
                    newCommandContext(new CommandContextConfiguration.Builder()
                            .setController(controller)
                    .setUsername(username)
                    .setPassword(password)
                    .setClientBindAddress(clientBindAddress)
                    .build());
        });
    }

    /**
     * Connect to the server using a specified host and port.
     *
     * @param controllerHost The host name.
     * @param controllerPort The port.
     * @param username The user name for logging in.
     * @param password The password for logging in.
     */
    public void connect(String controllerHost, int controllerPort,
            String username, char[] password) {
        connect("http-remoting", controllerHost, controllerPort,
                username, password, null);
    }

    /**
     * Connect to the server using a specified host and port.
     *
     * @param controllerHost The host name.
     * @param controllerPort The port.
     * @param username The user name for logging in.
     * @param password The password for logging in.
     * @param clientBindAddress the client bind address.
     */
    public void connect(String controllerHost, int controllerPort,
            String username, char[] password, String clientBindAddress) {
        connect("http-remoting", controllerHost, controllerPort,
                username, password, clientBindAddress);
    }

    /**
     * Connect to the server using a specified host and port.
     * @param protocol The protocol
     * @param controllerHost The host name.
     * @param controllerPort The port.
     * @param username The user name for logging in.
     * @param password The password for logging in.
     */
    public void connect(String protocol, String controllerHost,
            int controllerPort, String username, char[] password) {
        connect(protocol, controllerHost, controllerPort, username, password, null);
    }

    /**
     * Connect to the server using a specified host and port.
     * @param protocol The protocol
     * @param controllerHost The host name.
     * @param controllerPort The port.
     * @param username The user name for logging in.
     * @param password The password for logging in.
     */
    public void connect(String protocol, String controllerHost, int controllerPort,
            String username, char[] password, String clientBindAddress) {
        doConnect(() -> {
            return CommandContextFactory.getInstance().newCommandContext(
                    new CommandContextConfiguration.Builder().
                    setController(constructUri(protocol,
                            controllerHost,
                            controllerPort))
                    .setUsername(username)
                    .setPassword(password)
                    .setClientBindAddress(clientBindAddress)
                    .build());
        });
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        try {
            if (!isConnected()) {
                throw new IllegalStateException("Not connected to server.");
            }
            ctx.terminateSession();
        } finally {
            // Back to offline context
            initOfflineContext();
        }
    }

    /**
     * Execute a CLI command. This can be any command that you might execute on
     * the CLI command line, including both server-side operations and local
     * commands such as 'cd' or 'cn'.
     *
     * @param cliCommand A CLI command.
     * @return A result object that provides all information about the execution
     * of the command.
     */
    public Result cmd(String cliCommand) {
        try {
            ModelNode request = ctx.buildRequest(cliCommand);
            ModelNode response = ctx.getModelControllerClient().execute(request);
            return new Result(cliCommand, request, response);
        } catch (CommandFormatException cfe) {
            // if the command can not be converted to a ModelNode,
            // it might be a local command
            try {
                ctx.handle(cliCommand);
                return new Result(cliCommand, ctx.getExitCode());
            } catch (CommandLineException cle) {
                throw new IllegalArgumentException("Error handling command: "
                        + cliCommand, cle);
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("Unable to send command "
                    + cliCommand + " to server.", ioe);
        }
    }

    private String constructUri(final String protocol, final String host,
            final int port) {
        try {
            URI uri = new URI(protocol, null, host, port, null, null, null);
            // String the leading '//' if there is no protocol.
            return protocol == null ? uri.toString().substring(2) : uri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to construct URI.", e);
        }
    }

    private void initOfflineContext() {
        try {
            ctx = CommandContextFactory.getInstance().newCommandContext();
        } catch (CliInitializationException e) {
            throw new IllegalStateException("Unable to initialize "
                    + "command context.", e);
        }
    }

    private boolean isConnected() {
        return ctx.getConnectionInfo() != null;
    }

    private void doConnect(Callable<CommandContext> callable) {
        if (isConnected()) {
            throw new IllegalStateException("Already connected to server.");
        }
        CommandContext newContext = null;
        try {
            newContext = callable.call();
            newContext.connectController();
        } catch (Exception ex) {
            if (newContext != null) {
                newContext.terminateSession();
            }
            if (ex instanceof CliInitializationException) {
                throw new IllegalStateException("Unable to initialize "
                        + "command context.", ex);
            }
            if (ex instanceof CommandLineException) {
                throw new IllegalStateException("Unable to connect "
                        + "to controller.", ex);
            }
            throw new IllegalStateException(ex);
        }
        ctx = newContext;
    }

    /**
     * The Result class provides all information about an executed CLI command.
     */
    public class Result {
        private final String cliCommand;
        private ModelNode request;
        private ModelNode response;

        private boolean isSuccess = false;
        private boolean isLocalCommand = false;

        Result(String cliCommand, ModelNode request, ModelNode response) {
            this.cliCommand = cliCommand;
            this.request = request;
            this.response = response;
            this.isSuccess = response.get("outcome").asString().equals("success");
        }

        Result(String cliCommand, int exitCode) {
            this.cliCommand = cliCommand;
            this.isSuccess = exitCode == 0;
            this.isLocalCommand = true;
        }

        /**
         * Return the original command as a String.
         * @return The original CLI command.
         */
        public String getCliCommand() {
            return this.cliCommand;
        }

        /**
         * If the command resulted in a server-side operation, return the
         * ModelNode representation of the operation.
         *
         * @return The request as a ModelNode, or <code>null</code> if this was
         * a local command.
         */
        public ModelNode getRequest() {
            return this.request;
        }

        /**
         * If the command resulted in a server-side operation, return the
         * ModelNode representation of the response.
         *
         * @return The server response as a ModelNode, or <code>null</code> if
         * this was a local command.
         */
        public ModelNode getResponse() {
            return this.response;
        }

        /**
         * Return true if the command was successful. For a server-side
         * operation, this is determined by the outcome of the operation on the
         * server side.
         *
         * @return <code>true</code> if the command was successful,
         * <code>false</code> otherwise.
         */
        public boolean isSuccess() {
            return this.isSuccess;
        }

        /**
         * Return true if the command was only executed locally and did not
         * result in a server-side operation.
         *
         * @return <code>true</code> if the command was only executed locally,
         * <code>false</code> otherwise.
         */
        public boolean isLocalCommand() {
            return this.isLocalCommand;
        }
    }
}
