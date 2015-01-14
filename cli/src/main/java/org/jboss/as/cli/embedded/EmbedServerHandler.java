/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.embedded;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.embedded.EmbeddedServerFactory;
import org.jboss.as.embedded.ServerStartException;
import org.jboss.as.embedded.StandaloneServer;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.stdio.NullOutputStream;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Handler for the "embed-server" command.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
class EmbedServerHandler extends CommandHandlerWithHelp {

    private static final String ECHO = "echo";
    private static final String DISCARD_STDOUT = "discard";

    private final AtomicReference<EmbeddedServerLaunch> serverReference;
    private ArgumentWithValue jbossHome;
    private ArgumentWithValue stdOutHandling;

    static EmbedServerHandler create(AtomicReference<EmbeddedServerLaunch> serverReference, CommandContext ctx) {
        EmbedServerHandler result = new EmbedServerHandler(serverReference);
        final FilenameTabCompleter pathCompleter = Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
        result.jbossHome = new FileSystemPathArgument(result, pathCompleter, "--jboss-home");
        result.stdOutHandling = new ArgumentWithValue(result, new SimpleTabCompleter(new String[]{ECHO, DISCARD_STDOUT}), "--std-out");
        return result;
    }

    private EmbedServerHandler(AtomicReference<EmbeddedServerLaunch> serverReference) {
        super("embed-server", false);
        assert serverReference != null;
        this.serverReference = serverReference;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return ctx.getModelControllerClient() == null;
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        if(parsedCmd.getOtherProperties().size() > 1) {
            throw new CommandFormatException("The command accepts only one argument but received: " + parsedCmd.getOtherProperties());
        }
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        EmbeddedContainerConfiguration configuration = new EmbeddedContainerConfiguration();
        final String jbossHome = getJBossHome(parsedCmd);
        configuration.setJbossHome(jbossHome);
        configuration.validate();
        final List<String> args = parsedCmd.getOtherProperties();

        if (!args.isEmpty()) {
            if (args.size() != 1) {
                throw new CommandFormatException("The command expects only one argument but got " + args);
            }
        }

        final EnvironmentRestorer restorer = new EnvironmentRestorer();
        boolean ok = false;
        try {
            if (!ECHO.equalsIgnoreCase(stdOutHandling.getValue(parsedCmd))) {
                PrintStream nullStream = new UncloseablePrintStream(NullOutputStream.getInstance());
                StdioContext currentContext = restorer.getStdioContext();
                StdioContext newContext = StdioContext.create(currentContext.getIn(), nullStream, currentContext.getErr());
                StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(newContext));
            }

            // Create our own LogContext
            final LogContext embeddedLogContext = LogContext.create();
            LogContext.setLogContextSelector(new LogContextSelector() {
                @Override
                public LogContext getLogContext() {
                    return embeddedLogContext;
                }
            });

            StandaloneServer server = EmbeddedServerFactory.create(configuration.getJbossHome(), configuration.getModulePath(), null);
            server.start();
            serverReference.set(new EmbeddedServerLaunch(server, restorer));
            ctx.bindClient(server.getModelControllerClient());
            // TODO wait for start
            ok = true;
        } catch (ServerStartException e) {
            throw new CommandLineException("Cannot start embedded server", e);
        } finally {
            if (!ok) {
                restorer.restoreEnvironment();
            } else {
                // Just put back the LogContextSelector
                //restorer.restoreLogContextSelector();
            }
        }
    }

    private String getJBossHome(final ParsedCommandLine parsedCmd) throws CommandLineException {
        String jbossHome = this.jbossHome.getValue(parsedCmd);
        if (jbossHome == null) {
            jbossHome = WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null);
            if (jbossHome == null) {
                throw new CommandLineException("Missing configuration value for --jboss-home and environment variable JBOSS_HOME is not set");
            }
        }
        return jbossHome;
    }
}
