/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.wildfly.security.manager.WildFlySecurityManager;

import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class CommandContextFactory {

    private static final String DEFAULT_FACTORY_CLASS = "org.jboss.as.cli.impl.CommandContextFactoryImpl";

    public static CommandContextFactory getInstance() throws CliInitializationException {
        Class<?> factoryCls;
        try {
            factoryCls = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged().loadClass(DEFAULT_FACTORY_CLASS);
        } catch (ClassNotFoundException e) {
            throw new CliInitializationException("Failed to load " + DEFAULT_FACTORY_CLASS, e);
        }

        try {
            return (CommandContextFactory) factoryCls.newInstance();
        } catch (Exception e) {
            throw new CliInitializationException("Failed to create an instance of " + factoryCls, e);
        }
    }

    protected CommandContextFactory() {}

    public abstract CommandContext newCommandContext() throws CliInitializationException;

    public abstract CommandContext newCommandContext(String username, char[] password) throws CliInitializationException;

    public abstract CommandContext newCommandContext(String controller, String username, char[] password) throws CliInitializationException;

    /**
     * @deprecated Use {@link #newCommandContext(CommandContextConfiguration configuration)} instead.
     */
    @Deprecated
    public abstract CommandContext newCommandContext(String controller, String username, char[] password, boolean initConsole,
            final int connectionTimeout) throws CliInitializationException;

    /**
     * @deprecated Use {@link #newCommandContext(CommandContextConfiguration configuration)} instead.
     */
    @Deprecated
    public abstract CommandContext newCommandContext(String controller, String username, char[] password, boolean disableLocalAuth,
            boolean initConsole, final int connectionTimeout) throws CliInitializationException;

    public abstract CommandContext newCommandContext(String controller, String username, char[] password, InputStream consoleInput,
            OutputStream consoleOutput) throws CliInitializationException;

    public abstract CommandContext newCommandContext(CommandContextConfiguration configuration) throws CliInitializationException;

    /**
     * @deprecated Use {@link #newCommandContext(String, String, char[])} instead.
     */
    @Deprecated
    public abstract CommandContext newCommandContext(String controllerHost, int controllerPort,
            String username, char[] password) throws CliInitializationException;

    /**
     * @deprecated Use {@link #newCommandContext(String, int, String, char[], boolean, int)} instead.
     */
    @Deprecated
    public abstract CommandContext newCommandContext(String controllerHost, int controllerPort,
            String username, char[] password, boolean initConsole, final int connectionTimeout) throws CliInitializationException;

    /**
     * @deprecated Use {@link #newCommandContext(String, int, String, char[], InputStream, OutputStream)} instead.
     */
    @Deprecated
    public abstract CommandContext newCommandContext(String controllerHost, int controllerPort,
            String username, char[] password,
            InputStream consoleInput, OutputStream consoleOutput) throws CliInitializationException;

}
