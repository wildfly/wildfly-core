/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.handlers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import javax.security.sasl.SaslException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.accesscontrol.PerNodeOperationAccess;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.AwaiterModelControllerClient;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.dmr.ModelNode;
import org.xnio.http.RedirectException;

/**
 * @author Alexey Loubyansky
 *
 */
public class ReloadHandler extends BaseOperationCommand {
    private static final String ADMIN_ONLY = "admin-only";
    private static final String NORMAL = "normal";
    private static final String SUSPEND = "suspend";
    private static final String START_MODE = "start-mode";

    private final ArgumentWithValue adminOnly;
    private final ArgumentWithValue startMode;

    // standalone only arguments
    private final ArgumentWithValue useCurrentServerConfig;
    private final ArgumentWithValue serverConfig;
    // domain only arguments
    private final ArgumentWithValue host;
    private final ArgumentWithValue restartServers;
    private final ArgumentWithValue useCurrentDomainConfig;
    private final ArgumentWithValue useCurrentHostConfig;
    private final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef;
    private final ArgumentWithValue domainConfig;
    private final ArgumentWithValue hostConfig;

    private PerNodeOperationAccess hostReloadPermission;

    public ReloadHandler(CommandContext ctx, final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef) {
        super(ctx, Util.RELOAD, true);

        this.embeddedServerRef = embeddedServerRef;

        adminOnly = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--admin-only") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        startMode = new ArgumentWithValue(this, new DefaultCompleter(new DefaultCompleter.CandidatesProvider() {
            @Override
            public Collection<String> getAllCandidates(CommandContext ctx) {
                return Arrays.asList(ADMIN_ONLY, NORMAL, SUSPEND);
            }
        }), "--start-mode") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if (ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };

        startMode.addCantAppearAfter(adminOnly);
        adminOnly.addCantAppearAfter(startMode);

        useCurrentServerConfig = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--use-current-server-config"){
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        serverConfig = new ArgumentWithValue(this, "--server-config") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
        }};

        restartServers = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--restart-servers"){
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(!ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        useCurrentDomainConfig = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--use-current-domain-config"){
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(!ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        useCurrentHostConfig = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--use-current-host-config"){
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(!ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        hostConfig = new ArgumentWithValue(this, "--host-config") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(!ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        domainConfig = new ArgumentWithValue(this, "--domain-config") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
            if(!ctx.isDomainMode()) {
                return false;
            }
            return super.canAppearNext(ctx);
        }};

        host = new ArgumentWithValue(this, new CommaSeparatedCompleter() {
            @Override
            protected Collection<String> getAllCandidates(CommandContext ctx) {
                return hostReloadPermission.getAllowedOn(ctx);
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
        hostReloadPermission = new PerNodeOperationAccess(ctx, Util.HOST, null, Util.RELOAD);
        return AccessRequirementBuilder.Factory.create(ctx).any()
                .operation(Util.RELOAD)
                .requirement(hostReloadPermission)
                .build();
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
            doHandleEmbedded(ctx, client);
            return;
        }

        if (!(client instanceof AwaiterModelControllerClient)) {
            throw new CommandLineException("Unsupported ModelControllerClient implementation " + client.getClass().getName());
        }
        final AwaiterModelControllerClient cliClient = (AwaiterModelControllerClient) client;

        final ModelNode op = this.buildRequestWithoutHeaders(ctx);
        try {
            final ModelNode response = cliClient.execute(op, true);
            if(!Util.isSuccess(response)) {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        } catch(IOException e) {
            // if it's not connected it's assumed the reload is in process
            if (cliClient.isConnected()) {
                StreamUtils.safeClose(client);
                throw new CommandLineException("Failed to execute :reload", e);
            }
        }

        ensureServerRebootComplete(ctx, client);
    }

    private boolean isAdminOnly(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        boolean legacy = this.adminOnly.isPresent(args) && "TRUE".equalsIgnoreCase(this.adminOnly.getValue(args));
        boolean mode = this.startMode.isPresent(args) && ADMIN_ONLY.equalsIgnoreCase(this.startMode.getValue(args));
        return mode || legacy;
    }

    private void doHandleEmbedded(CommandContext ctx, ModelControllerClient client) throws CommandLineException {

        assert(embeddedServerRef != null);
        assert(embeddedServerRef.get() != null);

        final ModelNode op = this.buildRequestWithoutHeaders(ctx);
        if (embeddedServerRef.get().isHostController()) {
            // WFCORE-938
            // for embedded-hc, we require --admin-only=true to be passed until the EHC supports --admin-only=false
            if (!isAdminOnly(ctx)) {
                throw new CommandLineException("Reload into running mode is not supported, --admin-only must be specified.");
            }
        }

        try {
            final ModelNode response = client.execute(op);
            if(!Util.isSuccess(response)) {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
        } catch(IOException e) {
            // This shouldn't be possible, as this is a local client
            StreamUtils.safeClose(client);
            throw new CommandLineException("Failed to execute :reload", e);
        }

        ensureServerRebootComplete(ctx, client);
    }

    private void ensureServerRebootComplete(CommandContext ctx, ModelControllerClient client) throws CommandLineException {
        final long start = System.currentTimeMillis();
        final long timeoutMillis = ctx.getConfig().getConnectionTimeout() + 1000L;
        final ModelNode getStateOp = new ModelNode();
        if(ctx.isDomainMode()) {
            final ParsedCommandLine args = ctx.getParsedCommandLine();
            final String hostName = host.getValue(args);
            getStateOp.get(Util.ADDRESS).add(Util.HOST, hostName);
        }

        getStateOp.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        // this is left for compatibility with older hosts, it could use runtime-configuration-state on newer hosts.
        if(ctx.isDomainMode()) {
            getStateOp.get(ClientConstants.NAME).set("host-state");
        }else {
            getStateOp.get(ClientConstants.NAME).set("server-state");
        }

        while (true) {
            String serverState = null;
            try {
                final ModelNode response = client.execute(getStateOp);
                if (Util.isSuccess(response)) {
                    serverState = response.get(ClientConstants.RESULT).asString();
                    if ("running".equals(serverState) || "restart-required".equals(serverState)) {
                        // we're reloaded and the server is started
                        break;
                    }
                }
            } catch (IOException e) {
                // A Redirect Exception? Need to connect again the Client
                // if that is an http to https redirect only.
                Throwable ex = e;
                while (ex != null) {
                    if (ex instanceof RedirectException) {
                        // Attempt to reconnect the context
                        if (Util.reconnectContext((RedirectException) ex, ctx)) {
                            return;
                        }
                    } else if (ex instanceof SaslException) {
                        // Try to reconnect, would make the CLI
                        // to prompt for credentials in case the current ones became
                        // invalid (eg: change of security-realm or SASL reconfiguration).
                        try {
                            ctx.connectController();
                            return;
                        } catch (CommandLineException clex) {
                            // Not reconnected.
                        }
                    }

                    ex = ex.getCause();
                }
                // ignore and try again
            } catch( IllegalStateException ex) {
                // ignore and try again
                // IllegalStateException is because the embedded server ModelControllerClient will
                // throw that when the server-state / host-state is "stopping"
            }

            if (System.currentTimeMillis() - start > timeoutMillis) {
                if (!"starting".equals(serverState))  {
                    ctx.disconnectController();
                    throw new CommandLineException("Failed to establish connection in " + (System.currentTimeMillis() - start)
                            + "ms");
                }
                // else we don't wait any longer for start to finish
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                ctx.disconnectController();
                throw new CommandLineException("Interrupted while pausing before reconnecting.", e);
            }
        }
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine args = ctx.getParsedCommandLine();

        final ModelNode op = new ModelNode();
        if(ctx.isDomainMode()) {
            if(useCurrentServerConfig.isPresent(args)) {
                throw new CommandFormatException(useCurrentServerConfig.getFullName() + " is not allowed in the domain mode.");
            }
            if (serverConfig.isPresent(args)) {
                throw new CommandFormatException(serverConfig.getFullName() + " is not allowed in the domain mode.");
            }

            final String hostName = host.getValue(args);
            if(hostName == null) {
                throw new CommandFormatException("Missing required argument " + host.getFullName());
            }
            op.get(Util.ADDRESS).add(Util.HOST, hostName);

            setBooleanArgument(args, op, restartServers, "restart-servers");
            setBooleanArgument(args, op, this.useCurrentDomainConfig, "use-current-domain-config");
            setBooleanArgument(args, op, this.useCurrentHostConfig, "use-current-host-config");
            setStringValue(args, op, hostConfig, "host-config");
            setStringValue(args, op, domainConfig, "domain-config");
        } else {
            if(host.isPresent(args)) {
                throw new CommandFormatException(host.getFullName() + " is not allowed in the standalone mode.");
            }
            if(useCurrentDomainConfig.isPresent(args)) {
                throw new CommandFormatException(useCurrentDomainConfig.getFullName() + " is not allowed in the standalone mode.");
            }
            if(useCurrentHostConfig.isPresent(args)) {
                throw new CommandFormatException(useCurrentHostConfig.getFullName() + " is not allowed in the standalone mode.");
            }
            if(restartServers.isPresent(args)) {
                throw new CommandFormatException(restartServers.getFullName() + " is not allowed in the standalone mode.");
            }
            if (hostConfig.isPresent(args)) {
                throw new CommandFormatException(hostConfig.getFullName() + " is not allowed in the standalone mode.");
            }
            if (domainConfig.isPresent(args)) {
                throw new CommandFormatException(domainConfig.getFullName() + " is not allowed in the standalone mode.");
            }

            op.get(Util.ADDRESS).setEmptyList();
            setBooleanArgument(args, op, this.useCurrentServerConfig, "use-current-server-config");
            setStringValue(args, op, serverConfig, "server-config");

        }
        op.get(Util.OPERATION).set(Util.RELOAD);

        setStartMode(ctx, args, op);
        return op;
    }

    private void setStartMode(CommandContext ctx, final ParsedCommandLine args,
            final ModelNode op) throws CommandFormatException {
        if (startMode.isPresent(args) && ctx.isDomainMode()) {
            throw new CommandFormatException("--start-mode can't be used in domain mode.");
        }
        if (adminOnly.isPresent(args) && startMode.isPresent(args)) {
            throw new CommandFormatException("--start-mode and --admin-only can't be used all together.");
        }
        // Requires a value
        if (startMode.isPresent(args)) {
            String value = startMode.getValue(args, true);
            if ("true".equals(value)) {
                throw new CommandFormatException("--start-mode is missing value.");
            }
        }

        if (isAdminOnly(ctx)) {
            // Special case for domain
            if (ctx.isDomainMode()) {
                op.get(ADMIN_ONLY).set(true);
            } else {
                op.get(START_MODE).set(ADMIN_ONLY);
            }
        } else {
            setStringValue(args, op, startMode, START_MODE);
        }
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

    private void setStringValue(final ParsedCommandLine args, final ModelNode op, ArgumentWithValue arg, String paramName)
            throws CommandFormatException {
        if(!arg.isPresent(args)) {
            return;
        }
        final String value = arg.getValue(args);
        if (value == null) {
            throw new CommandFormatException(arg.getFullName() + " is missing value.");
        }
        op.get(paramName).set(value);
    }
}
