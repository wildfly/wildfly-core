/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandContextFactoryImpl extends CommandContextFactory {

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandContextFactory#newCommandContext()
     */
    @Override
    public CommandContext newCommandContext() throws CliInitializationException {
        return new CommandContextImpl();
    }

    @Override
    public CommandContext newCommandContext(String username, char[] password) throws CliInitializationException {
        return newCommandContext(new CommandContextConfiguration.Builder()
                .setUsername(username)
                .setPassword(password)
                .setInitConsole(false)
                .build());
    }

    @Override
    public CommandContext newCommandContext(String controller, String username, char[] password) throws CliInitializationException {
        return newCommandContext(new CommandContextConfiguration.Builder()
                .setController(controller)
                .setUsername(username)
                .setPassword(password)
                .setInitConsole(false)
                .build());
    }

    @Override
    @Deprecated
    public CommandContext newCommandContext(String controller, String username, char[] password, boolean initConsole, int connectionTimeout) throws CliInitializationException {
        return newCommandContext(new CommandContextConfiguration.Builder()
                .setController(controller)
                .setUsername(username)
                .setPassword(password)
                .setInitConsole(initConsole)
                .setConnectionTimeout(connectionTimeout)
                .build());
    }

    @Override
    @Deprecated
    public CommandContext newCommandContext(String controller, String username, char[] password, boolean disableLocalAuth, boolean initConsole, int connectionTimeout) throws CliInitializationException {
        return newCommandContext(new CommandContextConfiguration.Builder()
                .setController(controller)
                .setUsername(username)
                .setPassword(password)
                .setDisableLocalAuth(disableLocalAuth)
                .setInitConsole(initConsole)
                .setConnectionTimeout(connectionTimeout)
                .build());
    }

    @Override
    public CommandContext newCommandContext(String controller, String username, char[] password, InputStream consoleInput, OutputStream consoleOutput) throws CliInitializationException {
        return newCommandContext(new CommandContextConfiguration.Builder()
                .setController(controller)
                .setUsername(username)
                .setPassword(password)
                .setConsoleInput(consoleInput)
                .setConsoleOutput(consoleOutput)
                .setDisableLocalAuth(false)
                .setInitConsole(false)
                .build());
    }

    @Override
    @Deprecated
    public CommandContext newCommandContext(String controllerHost, int controllerPort, String username, char[] password)
            throws CliInitializationException {
        try {
            return newCommandContext(new CommandContextConfiguration.Builder()
                    .setController(new URI(null, null, controllerHost, controllerPort, null, null, null).toString().substring(2))
                    .setUsername(username)
                    .setPassword(password)
                    .build());
        } catch (URISyntaxException e) {
            throw new CliInitializationException("Unable to construct URI for connection.", e);
        }
    }

    @Override
    @Deprecated
    public CommandContext newCommandContext(String controllerHost, int controllerPort, String username, char[] password,
            boolean initConsole, int connectionTimeout) throws CliInitializationException {
        try {
            return newCommandContext(new CommandContextConfiguration.Builder()
                .setController(new URI(null, null, controllerHost, controllerPort, null, null, null).toString().substring(2))
                .setUsername(username)
                .setPassword(password)
                .setInitConsole(initConsole)
                .setConnectionTimeout(connectionTimeout)
                .build());
        } catch (URISyntaxException e) {
            throw new CliInitializationException("Unable to construct URI for connection.", e);
        }
    }

    @Override
    @Deprecated
    public CommandContext newCommandContext(String controllerHost, int controllerPort, String username, char[] password,
            InputStream consoleInput, OutputStream consoleOutput) throws CliInitializationException {
        try {
            return newCommandContext(new CommandContextConfiguration.Builder()
                    .setController(new URI(null, null, controllerHost, controllerPort, null, null, null).toString().substring(2))
                    .setUsername(username)
                    .setPassword(password)
                    .setConsoleInput(consoleInput)
                    .setConsoleOutput(consoleOutput)
                    .setInitConsole(false)
                    .setDisableLocalAuth(false)
                    .build());
        } catch (URISyntaxException e) {
            throw new CliInitializationException("Unable to construct URI for connection.", e);
        }
    }

    @Override
    public CommandContext newCommandContext(CommandContextConfiguration configuration) throws CliInitializationException {
        return new CommandContextImpl(configuration);
    }
}
