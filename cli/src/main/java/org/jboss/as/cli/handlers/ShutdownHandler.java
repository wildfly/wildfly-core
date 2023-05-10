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

package org.jboss.as.cli.handlers;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.AwaiterModelControllerClient;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.http.RedirectException;

/**
 * @author Alexey Loubyansky
 *
 */
public class ShutdownHandler extends BaseOperationCommand {

    private final ArgumentWithValue restart;
    private final ArgumentWithValue host;
    @Deprecated
    private final ArgumentWithValue timeout;
    private final ArgumentWithValue suspendTimeout;
    private final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef;
    private PerNodeOperationAccess hostShutdownPermission;

    private final ArgumentWithValue performInstallation;

    public ShutdownHandler(CommandContext ctx, final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef) {
        super(ctx, "shutdown", true, false);

        this.embeddedServerRef = embeddedServerRef;

        restart = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--restart") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (performInstallation.isPresent(ctx.getParsedCommandLine())) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        timeout = new ArgumentWithValue(this, "--timeout"){
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (ctx.isDomainMode()) {
                    return false;
                }
                if (suspendTimeout.isPresent(ctx.getParsedCommandLine())) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        suspendTimeout = new ArgumentWithValue(this, "--suspend-timeout") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (timeout.isPresent(ctx.getParsedCommandLine())) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        host = new ArgumentWithValue(this, new CommaSeparatedCompleter() {
            @Override
            protected Collection<String> getAllCandidates(CommandContext ctx) {
                return hostShutdownPermission.getAllowedOn(ctx);

            }} , "--host") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        performInstallation = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--" + Util.PERFORM_INSTALLATION) {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (restart.isPresent(ctx.getParsedCommandLine())) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };
    }

    @Override
    protected AccessRequirement setupAccessRequirement(CommandContext ctx) {
        hostShutdownPermission = new PerNodeOperationAccess(ctx, Util.HOST, null, Util.SHUTDOWN);
        return AccessRequirementBuilder.Factory.create(ctx).any()
                .operation(Util.SHUTDOWN)
                .requirement(hostShutdownPermission)
                .build();
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return super.isAvailable(ctx) && ((embeddedServerRef == null || embeddedServerRef.get() == null));
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            throw new CommandLineException("Connection is not available.");
        }

        if (embeddedServerRef != null && embeddedServerRef.get() != null) {
            embeddedServerRef.get().stop();
            return;
        }

        if (!(client instanceof AwaiterModelControllerClient)) {
            throw new CommandLineException("Unsupported ModelControllerClient implementation " + client.getClass().getName());
        }
        final AwaiterModelControllerClient cliClient = (AwaiterModelControllerClient) client;

        final ModelNode op = this.buildRequestWithoutHeaders(ctx);

        boolean isPerformInstallation = Util.TRUE.equalsIgnoreCase(performInstallation.getValue(ctx.getParsedCommandLine()));
        boolean disconnect = true;
        final boolean requestRestart = Util.TRUE.equalsIgnoreCase(restart.getValue(ctx.getParsedCommandLine()))
                || isPerformInstallation;
        if (requestRestart ||
                ctx.isDomainMode() &&
                !isLocalHost(ctx.getModelControllerClient(), host.getValue(ctx.getParsedCommandLine()))) {
            disconnect = false;
        }

        if (Util.TRUE.equalsIgnoreCase(performInstallation.getValue(ctx.getParsedCommandLine()))) {
            // Check if I am using a client launched from the server/host installation we want to shutdown to perform an update.
            // In such a case, we will exit from the current JBoss CLI process to avoid interfering with the server/host update.
            // This will also force the user to relaunch the CLI session using the most recent updates once the server/host has
            // been updated.
            boolean localClientLaunch = isLocalClientLaunch(ctx);
            executeOperation(client, cliClient, op, !localClientLaunch);
            if (localClientLaunch) {
                ctx.printLine("The JBoss CLI session will be closed automatically to allow the server be updated. Once the server has been restarted, you can relaunch the JBoss CLI session.");
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    // Ignored
                }

                // We are using a CLI which was launched from the server installation we have requested to be updated.
                // In order to prevent keeping using a jboss-modules.jar that could have been updated, we finish the CLI process
                // Once the server has been restarted the user will launch again the CLI that will use the most recent updates
                ctx.terminateSession();
            }
        } else {
            executeOperation(client, cliClient, op, true);
        }

        if (disconnect) {
            ctx.disconnectController();
        } else {
            // if I try to reconnect immediately, it'll hang for 5 sec
            // which the default connection timeout for model controller client
            // waiting half a sec on my machine works perfectly
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new CommandLineException("Interrupted while pausing before reconnecting.", e);
            }
            try {
                long configuredTimeout = ctx.getConfig().getConnectionTimeout() + 1_000L;
                // If we are performing an installation, adds one additional minute to the reconnect timeout since apply the server
                // installation takes more time than a usual restart
                configuredTimeout = isPerformInstallation ? configuredTimeout + (60 * 1_000L) : configuredTimeout;
                cliClient.ensureConnected(configuredTimeout);
            } catch(CommandLineException e) {
                ctx.disconnectController();
                throw e;
            } catch (IOException ex) {
                if (ex instanceof RedirectException) {
                    if (!Util.reconnectContext((RedirectException) ex, ctx)) {
                        throw new CommandLineException("Can't reconnect context.", ex);
                    }
                } else {
                    throw new CommandLineException(ex);
                }
            }
        }
    }

    private static void executeOperation(ModelControllerClient client, AwaiterModelControllerClient cliClient, ModelNode op, boolean awaitClose) throws CommandLineException {
        try {
            final ModelNode response = cliClient.execute(op, awaitClose);
            if (!Util.isSuccess(response)) {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        } catch (IOException e) {
            // if it's not connected, it's assumed the connection has already been shutdown
            if (cliClient.isConnected()) {
                StreamUtils.safeClose(client);
                throw new CommandLineException("Failed to execute :shutdown", e);
            }
        }
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ModelNode op = new ModelNode();
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        if(ctx.isDomainMode()) {
            final String hostName = host.getValue(args);
            if(hostName == null) {
                throw new CommandFormatException("Missing required argument " + host.getFullName());
            }
            op.get(Util.ADDRESS).add(Util.HOST, hostName);

            if(timeout.isPresent(args)){
                throw new CommandFormatException(timeout.getFullName() + " is not allowed in the domain mode.");
            }

        } else {
            if(host.isPresent(args)) {
                throw new CommandFormatException(host.getFullName() + " is not allowed in the standalone mode.");
            }

            if (timeout.isPresent(args) && suspendTimeout.isPresent(args)) {
                throw new CommandFormatException(timeout.getFullName() + " cannot be used in conjunction with suspend-timeout.");
            }

            op.get(Util.ADDRESS).setEmptyList();
        }

        if (restart.isPresent(args) && performInstallation.isPresent(args)) {
            throw new CommandFormatException(performInstallation.getFullName() + " cannot be used in conjunction with restart.");
        }

        op.get(Util.OPERATION).set(Util.SHUTDOWN);
        setBooleanArgument(args, op, restart, Util.RESTART);
        setBooleanArgument(args, op, performInstallation, Util.PERFORM_INSTALLATION);
        setIntArgument(args, op, timeout, Util.TIMEOUT);
        setIntArgument(args, op, suspendTimeout, Util.SUSPEND_TIMEOUT);
        return op;
    }

    protected boolean isLocalHost(ModelControllerClient client, String host) throws CommandLineException {
        ModelNode request = new ModelNode();
        request.get(Util.ADDRESS).setEmptyList();
        request.get(Util.OPERATION).set(Util.READ_ATTRIBUTE);
        request.get(Util.NAME).set(Util.LOCAL_HOST_NAME);
        ModelNode response;
        try {
            response = client.execute(request);
        } catch (IOException e) {
            throw new CommandLineException("Failed to read attribute " + Util.LOCAL_HOST_NAME, e);
        }
        if(!Util.isSuccess(response)) {
            throw new CommandLineException("Failed to read attribute " + Util.LOCAL_HOST_NAME
                    + ": " + Util.getFailureDescription(response));
        }
        ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            throw new CommandLineException("The result is not defined for attribute " + Util.LOCAL_HOST_NAME + ": " + result);
        }

        return result.asString().equals(host);
    }

    /**
     * Reads the server JBoss Home directory and compared it with the JBoss CLI Jboss Home directory to determine whether
     * the local JBoss CLI process has been launched from the same Jboss server/host.
     *
     * If the paths of both JBoss Home directories are equals, it is assumed the current JBoss CLI process was launched from
     * the server/host where it is connected to.
     *
     * @param ctx
     * @return
     * @throws CommandLineException
     */
    protected boolean isLocalClientLaunch(CommandContext ctx) throws CommandLineException {
        final ModelNode op = new ModelNode();
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final ModelControllerClient client = ctx.getModelControllerClient();
        if(ctx.isDomainMode()) {
            final String hostName = host.getValue(args);
            if (hostName == null) {
                throw new CommandFormatException("Missing required argument " + host.getFullName());
            }
            op.get(Util.ADDRESS).add(Util.HOST, hostName);
            op.get(Util.ADDRESS).add(Util.CORE_SERVICE, "host-environment");

        } else {
            op.get(Util.ADDRESS).add(Util.CORE_SERVICE, "server-environment");
        }
        op.get(Util.OPERATION).set(Util.READ_ATTRIBUTE);
        op.get(Util.NAME).set(Util.HOME_DIR);

        ModelNode response;
        try {
            response = client.execute(op);
        } catch (IOException e) {
            throw new CommandLineException("Failed to read attribute " + Util.HOME_DIR, e);
        }
        if(!Util.isSuccess(response)) {
            throw new CommandLineException("Failed to read attribute " + Util.HOME_DIR
                    + ": " + Util.getFailureDescription(response));
        }
        ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            throw new CommandLineException("The result is not defined for attribute " + Util.HOME_DIR + ": " + result);
        }

        final String jbossHome = WildFlySecurityManager.getEnvPropertyPrivileged("JBOSS_HOME", null);
        return jbossHome != null && Paths.get(jbossHome).normalize().toAbsolutePath().equals(
                Paths.get(result.asString()).normalize().toAbsolutePath()
        );
    }

    protected void setBooleanArgument(final ParsedCommandLine args, final ModelNode op, ArgumentWithValue arg, String paramName)
            throws CommandFormatException {
        if(!arg.isPresent(args)) {
            return;
        }
        final String value = arg.getValue(args);
        if(value == null) {
            throw new CommandFormatException(arg.getFullName() + " is missing value.");
        }
        if(value.equalsIgnoreCase(Util.TRUE)) {
            op.get(paramName).set(true);
        } else if(value.equalsIgnoreCase(Util.FALSE)) {
            op.get(paramName).set(false);
        } else {
            throw new CommandFormatException("Invalid value for " + arg.getFullName() + ": '" + value + "'");
        }
    }

    private void setIntArgument(final ParsedCommandLine args, final ModelNode op, ArgumentWithValue arg, String paramName)
            throws CommandFormatException {
        if(!arg.isPresent(args)) {
            return;
        }
        final String value = arg.getValue(args);
        if(value == null) {
            throw new CommandFormatException(arg.getFullName() + " is missing value.");
        }
        try {
            Integer i = Integer.parseInt(value);
            op.get(paramName).set(i);
        } catch (NumberFormatException nfe) {
            throw new CommandFormatException("Invalid value for " + arg.getFullName() + ": '" + value + "'");
        }
    }
}
