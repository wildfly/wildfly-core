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
package org.jboss.as.cli.impl.aesh.cmd.deployment;

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.AbstractCompleter;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators.NameActivator;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;

/**
 * Enable a disabled deployment. All fields are public to be accessible from
 * legacy commands. To be made private when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable", description = "", activator = ControlledCommandActivator.class)
public class EnableCommand extends AbstractDeployCommand implements LegacyBridge {

    public static class NameCompleter
            extends AbstractCompleter {

        @Override
        protected List<String> getItems(CLICompleterInvocation completerInvocation) {
            List<String> deployments = Collections.emptyList();
            if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
                deployments
                        = Util.getDeployments(completerInvocation.getCommandContext().
                                getModelControllerClient());
            }
            return deployments;
        }
    }

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    // Argument comes first, aesh behavior.
    @Argument(required = true, activator = NameActivator.class,
            completer = NameCompleter.class)
    public String name;

    public EnableCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.enableAccess(permissions), permissions);
    }

    @Deprecated
    public EnableCommand(CommandContext ctx) {
        this(ctx, null);
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment enable"));
            return CommandResult.SUCCESS;
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        if (name == null || name.isEmpty()) {
            throw new CommandException("No deployment name");
        }
        deployName(ctx, name, allServerGroups, serverGroups, headers);
        return CommandResult.SUCCESS;
    }

    static void deployName(CommandContext ctx, String name,
            boolean allServerGroups, String serverGroups, ModelNode headers)
            throws CommandException {
        try {
            ModelNode request = buildRequest(ctx,
                    name, allServerGroups, serverGroups, headers);
            final ModelNode result = ctx.
                    getModelControllerClient().execute(request);
            if (!Util.isSuccess(result)) {
                throw new CommandException(Util.getFailureDescription(result));
            }
        } catch (IOException e) {
            throw new CommandException("Failed to deploy", e);
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
    }

    @Override
    public ModelNode buildRequest(CommandContext context)
            throws CommandFormatException {
        return buildRequest(context, name, allServerGroups,
                serverGroups, headers);
    }

    private static ModelNode buildRequest(CommandContext ctx, String name,
            boolean allServerGroups, String serverGroups, ModelNode headers) throws CommandFormatException {
        ModelNode deployRequest = new ModelNode();
        if (ctx.isDomainMode()) {
            final List<String> sgList = DeploymentCommand.getServerGroups(ctx,
                    ctx.getModelControllerClient(),
                    allServerGroups, serverGroups, null);
            deployRequest.get(Util.OPERATION).set(Util.COMPOSITE);
            deployRequest.get(Util.ADDRESS).setEmptyList();
            ModelNode steps = deployRequest.get(Util.STEPS);
            for (String serverGroup : sgList) {
                if (!Util.isDeploymentPresent(name, ctx.getModelControllerClient(), serverGroup)) {
                    steps.add(Util.configureDeploymentOperation(Util.ADD, name,
                            serverGroup));
                }
            }
            for (String serverGroup : sgList) {
                steps.add(Util.configureDeploymentOperation(Util.DEPLOY, name,
                        serverGroup));
            }
        } else {
            if (serverGroups != null || allServerGroups) {
                throw new CommandFormatException("--all-server-groups and --server-groups "
                        + "can't appear in standalone mode.");
            }
            deployRequest.get(Util.OPERATION).set(Util.DEPLOY);
            deployRequest.get(Util.ADDRESS, Util.DEPLOYMENT).set(name);
        }

        if (!ctx.isBatchMode() && !Util.isDeploymentInRepository(name,
                ctx.getModelControllerClient())) {
            throw new CommandFormatException("'" + name + "' is not found among "
                    + "the registered deployments.");
        }
        if (headers != null) {
            ModelNode opHeaders = deployRequest.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return deployRequest;
    }
}
