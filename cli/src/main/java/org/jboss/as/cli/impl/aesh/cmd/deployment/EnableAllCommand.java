/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 * Enable all disabled deployments.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable-all", description = "", activator = ControlledCommandActivator.class)
public class EnableAllCommand extends AbstractDeployCommand implements LegacyBridge {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    public EnableAllCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.enableAllAccess(permissions), permissions);
    }

    @Deprecated
    public EnableAllCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment enable-all"));
            return CommandResult.SUCCESS;
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        deployAll(ctx, allServerGroups, serverGroups, headers);
        return CommandResult.SUCCESS;
    }

    static void deployAll(CommandContext ctx,
            boolean allServerGroups,
            String serverGroups, ModelNode headers) throws CommandException {
        try {
            ModelNode request = buildRequest(ctx,
                    allServerGroups, serverGroups, headers);
            final ModelNode result = ctx.
                    getModelControllerClient().execute(request);
            if (!Util.isSuccess(result)) {
                throw new CommandException(Util.getFailureDescription(result));
            }
        } catch (IOException e) {
            throw new CommandException("Failed to enable-all", e);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
    }

    @Override
    public ModelNode buildRequest(CommandContext context)
            throws CommandFormatException {
        return buildRequest(context, allServerGroups, serverGroups, headers);
    }

    private static ModelNode buildRequest(CommandContext ctx,
            boolean allServerGroups, String serverGroups, ModelNode headers)
            throws CommandFormatException {
        List<String> sgList = DeploymentCommand.getServerGroups(ctx,
                ctx.getModelControllerClient(),
                allServerGroups, serverGroups, null);
        if (sgList == null) {
            // No serverGroups means a null serverGroup.
            sgList = Collections.singletonList(null);
        }
        ModelControllerClient client = ctx.getModelControllerClient();
        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);
        boolean empty = true;
        for (String serverGroup : sgList) {
            List<String> deployments = Util.getDeployments(client, serverGroup);
            for (String deploymentName : deployments) {
                try {
                    if (!Util.isEnabledDeployment(deploymentName,
                            client, serverGroup)) {
                        DefaultOperationRequestBuilder builder
                                = new DefaultOperationRequestBuilder();
                        if (serverGroup != null) {
                            builder.addNode(Util.SERVER_GROUP, serverGroup);
                        }
                        builder.addNode(Util.DEPLOYMENT, deploymentName);
                        builder.setOperationName(Util.DEPLOY);
                        steps.add(builder.buildRequest());
                        empty = false;
                    }
                } catch (IOException ex) {
                    throw new CommandFormatException(ex);
                }
            }
        }
        if (empty) {
            throw new CommandFormatException("No disabled deployment to enable.");
        }
        if (headers != null) {
            ModelNode opHeaders = composite.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return composite;
    }
}
