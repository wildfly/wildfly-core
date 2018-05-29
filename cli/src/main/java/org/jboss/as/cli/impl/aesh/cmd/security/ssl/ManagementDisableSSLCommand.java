/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.ssl;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.AwaiterModelControllerClient;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_NO_RELOAD;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-ssl-management", description = "", activator = SecurityCommandActivator.class)
public class ManagementDisableSSLCommand implements Command<CLICommandInvocation>, DMRCommand {

    @Option(name = OPT_NO_RELOAD, hasValue = false)
    boolean noReload;

    @Option(name = OPT_MANAGEMENT_INTERFACE, completer = OptionCompleters.ManagementInterfaceCompleter.class)
    String managementInterface;

    private final AtomicReference<EmbeddedProcessLaunch> embeddedServerRef;

    public ManagementDisableSSLCommand(AtomicReference<EmbeddedProcessLaunch> embeddedServerRef) {
        this.embeddedServerRef = embeddedServerRef;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        ModelNode request;
        try {
            request = buildRequest(ctx);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex.getLocalizedMessage(), ex);
        }
        execute(ctx, request);
        return CommandResult.SUCCESS;
    }

    private void execute(CommandContext ctx, ModelNode request) throws CommandException {
        ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException ex) {
            throw new CommandException(ex);
        }
        if (!Util.isSuccess(response)) {
            throw new CommandException(Util.getFailureDescription(response));
        }

        if (!noReload) {
            try {
                reload(ctx);
                ctx.printLine("Server reloaded.");
                reconnect(ctx);
                ctx.printLine("Reconnected to server.");
            } catch (CommandLineException ex) {
                throw new CommandException(ex.getLocalizedMessage(), ex);
            }
        } else {
            ctx.printLine("Warning: server has not been reloaded. Call 'reload' to apply changes.");
        }
        ctx.printLine("SSL disabled for " + managementInterface);
    }

    private void reload(CommandContext ctx) throws CommandException, OperationFormatException {
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
        AwaiterModelControllerClient aclient = (AwaiterModelControllerClient) ctx.getModelControllerClient();
        try {
            String mode = Util.getRunningMode(ctx);
            builder.setOperationName(Util.RELOAD);
            builder.addProperty(Util.START_MODE, Util.ADMIN_ONLY.equals(mode) ? Util.ADMIN_ONLY : Util.NORMAL);
            ModelNode response;
            response = aclient.execute(builder.buildRequest(), true);
            if (!Util.isSuccess(response)) {
                throw new CommandException(Util.getFailureDescription(response));
            }
        } catch (IOException ex) {
            // if it's not connected it's assumed the reload is in process
            if (aclient.isConnected()) {
                throw new CommandException(ex);
            }
        }
    }

    private boolean isEmbedded() {
        return embeddedServerRef != null && embeddedServerRef.get() != null;
    }

    private void reconnect(CommandContext ctx) throws CommandLineException {
        if (isEmbedded()) {
            return;
        }

        final long start = System.currentTimeMillis();
        final long timeoutMillis = ctx.getConfig().getConnectionTimeout() + 1000;
        Exception exception;
        while (true) {
            try {
                ctx.connectController();
                break;
            } catch (Exception ex) {
                // Ignore and try again
                exception = ex;
            }
            if (System.currentTimeMillis() - start > timeoutMillis) {
                ctx.disconnectController();
                throw new CommandLineException("Failed to re-establish connection in " + (System.currentTimeMillis() - start)
                        + "ms." + (exception != null ? exception : ""));
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
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        try {
            managementInterface = ManagementInterfaces.disableSSL(context,
                    managementInterface, composite.get(Util.STEPS));
        } catch (Exception ex) {
            throw new CommandFormatException(ex.getLocalizedMessage() == null
                    ? ex.toString() : ex.getLocalizedMessage(), ex);
        }
        return composite;
    }

}
