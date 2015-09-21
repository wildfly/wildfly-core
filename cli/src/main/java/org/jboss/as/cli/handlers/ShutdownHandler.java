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
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;
import org.jboss.as.cli.embedded.EmbeddedServerLaunch;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.CLIModelControllerClient;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;

/**
 * @author Alexey Loubyansky
 *
 */
public class ShutdownHandler extends BaseOperationCommand {

    private final ArgumentWithValue restart;
    private final ArgumentWithValue host;
    private final AtomicReference<EmbeddedServerLaunch> embeddedServerRef;
    private PerNodeOperationAccess hostShutdownPermission;

    public ShutdownHandler(CommandContext ctx, final AtomicReference<EmbeddedServerLaunch> embeddedServerRef) {
        super(ctx, "shutdown", true);

        this.embeddedServerRef = embeddedServerRef;

        restart = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--restart");

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

        if(!(client instanceof CLIModelControllerClient)) {
            throw new CommandLineException("Unsupported ModelControllerClient implementation " + client.getClass().getName());
        }
        final CLIModelControllerClient cliClient = (CLIModelControllerClient) client;

        final ModelNode op = this.buildRequestWithoutHeaders(ctx);

        boolean disconnect = true;
        final String restartValue = restart.getValue(ctx.getParsedCommandLine());
        if (Util.TRUE.equals(restartValue) ||
                ctx.isDomainMode() &&
                !isLocalHost(ctx.getModelControllerClient(), host.getValue(ctx.getParsedCommandLine()))) {
            disconnect = false;
        }

        try {
            final ModelNode response = cliClient.execute(op, true);
            if(!Util.isSuccess(response)) {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        } catch(IOException e) {
            // if it's not connected, it's assumed the connection has already been shutdown
            if(cliClient.isConnected()) {
                StreamUtils.safeClose(cliClient);
                throw new CommandLineException("Failed to execute :shutdown", e);
            }
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
                cliClient.ensureConnected(ctx.getConfig().getConnectionTimeout() + 1000);
            } catch(CommandLineException e) {
                ctx.disconnectController();
                throw e;
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
        } else {
            if(host.isPresent(args)) {
                throw new CommandFormatException(host.getFullName() + " is not allowed in the standalone mode.");
            }
            op.get(Util.ADDRESS).setEmptyList();
        }
        op.get(Util.OPERATION).set(Util.SHUTDOWN);
        setBooleanArgument(args, op, restart, Util.RESTART);
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
}
