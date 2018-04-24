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

import org.jboss.as.cli.impl.aesh.cmd.deployment.security.CommandWithPermissions;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.cmd.HeadersConverter;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.jboss.as.cli.impl.aesh.cmd.LegacyBridge;
import org.jboss.as.cli.impl.aesh.cmd.deployment.AbstractDeployCommand.ServerGroupsCompleter;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.OptionActivators;

/**
 * Base class for deployment undeploy commands (disable and undeploy). All
 * fields are public to be accessible from legacy commands. To be made private
 * when legacies are removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-undeploy-deployment", description = "", activator = ControlledCommandActivator.class)
public abstract class AbstractUndeployCommand extends CommandWithPermissions
        implements Command<CLICommandInvocation>, BatchCompliantCommand, LegacyBridge {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option(name = "server-groups", activator = OptionActivators.UndeployServerGroupsActivator.class,
            completer = ServerGroupsCompleter.class, required = false)
    public String serverGroups;

    @Option(name = "all-relevant-server-groups", activator = OptionActivators.AllRelevantServerGroupsActivator.class,
            hasValue = false, required = false)
    public boolean allRelevantServerGroups;

    @Option(converter = HeadersConverter.class, completer = HeadersCompleter.class,
            required = false)
    public ModelNode headers;

    // Public for compat reason. Make it private when removing compat code.
    public AbstractUndeployCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.undeployAccess(permissions), permissions);
    }

    protected abstract boolean keepContent();

    protected abstract String getName();

    protected abstract String getCommandName();

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("deployment " + getCommandName()));
            return CommandResult.SUCCESS;
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        String name = getName();
        if (name == null) {
            throw new CommandException("No deployment name");
        }
        undeployName(ctx, name, allRelevantServerGroups,
                serverGroups, keepContent(), headers);
        return CommandResult.SUCCESS;
    }

    @Override
    public BatchCompliantCommand.BatchResponseHandler buildBatchResponseHandler(CommandContext commandContext,
            Attachments attachments) {
        return null;
    }

    static void undeployName(CommandContext ctx, String name,
            boolean allServerGroups, String serverGroups, boolean keepContent, ModelNode headers)
            throws CommandException {
        try {
            ModelNode request = buildRequest(ctx,
                    name, allServerGroups, serverGroups, keepContent, headers);
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
        return buildRequest(context, getName(), allRelevantServerGroups,
                serverGroups, keepContent(), headers);
    }

    private static ModelNode buildRequest(CommandContext ctx, String name,
            boolean allRelevantServerGroups, String serverGroupsStr,
            boolean keepContent, ModelNode headers) throws CommandFormatException {
        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);

        final ModelControllerClient client = ctx.getModelControllerClient();
        DefaultOperationRequestBuilder builder;

        final List<String> deploymentNames;
        boolean all = false;
        if (name.indexOf('*') < 0) {
            deploymentNames = Collections.singletonList(name);
        } else {
            deploymentNames = Util.getMatchingDeployments(client, name, null);
            if (deploymentNames.isEmpty()) {
                throw new CommandFormatException("No deployment matched wildcard expression " + name);
            }
            all = true;
        }

        // WFCORE-3566 - adding the UNDEPLOY step for each found deployment is required as this
        // request is included in the composite operation which would fail with empty steps otherwise,
        // if the deployment does not exist, the composite will report a proper error
        for (String deploymentName : deploymentNames) {

            final List<String> serverGroups;
            if (ctx.isDomainMode()) {
                if (allRelevantServerGroups) {
                    if (keepContent) {
                        serverGroups = Util.getAllEnabledServerGroups(deploymentName, client);
                        if (all && serverGroups.isEmpty()) {
                            continue;
                        }
                    } else {
                        try {
                            serverGroups = Util.getServerGroupsReferencingDeployment(deploymentName, client);
                        } catch (CommandLineException e) {
                            throw new CommandFormatException("Failed to retrieve all referencing server groups", e);
                        }
                    }
                } else if (serverGroupsStr == null) {
                    //throw new OperationFormatException("Either --all-relevant-server-groups or --server-groups must be specified.");
                    serverGroups = Collections.emptyList();
                } else {
                    serverGroups = Arrays.asList(serverGroupsStr.split(","));
                }

                if (serverGroups.isEmpty() && keepContent) {
                    throw new OperationFormatException("None of the server groups is specified or references deployment " + deploymentName);
                } else {
                    // If !keepContent, check that all server groups have been listed by user.
                    if (!keepContent) {
                        try {
                            List<String> sg = Util.getServerGroupsReferencingDeployment(deploymentName, client);
                            // Keep the content if some groups are missing.
                            keepContent = !serverGroups.containsAll(sg);
                        } catch (CommandLineException e) {
                            throw new CommandFormatException("Failed to retrieve all referencing server groups", e);
                        }
                    }
                    for (String group : serverGroups) {
                        if (all && !Util.isDeploymentPresent(deploymentName, client, group)) {
                            continue;
                        }
                        ModelNode groupStep = Util.configureDeploymentOperation(Util.UNDEPLOY, deploymentName, group);
                        steps.add(groupStep);
                        if (!keepContent) {
                            groupStep = Util.configureDeploymentOperation(Util.REMOVE, deploymentName, group);
                            steps.add(groupStep);
                        }
                    }
                }
            } else {
                builder = new DefaultOperationRequestBuilder();
                builder.setOperationName(Util.UNDEPLOY);
                builder.addNode(Util.DEPLOYMENT, deploymentName);
                steps.add(builder.buildRequest());
            }
        }

        if (!keepContent) {
            for (String deploymentName : deploymentNames) {
                builder = new DefaultOperationRequestBuilder();
                builder.setOperationName(Util.REMOVE);
                builder.addNode(Util.DEPLOYMENT, deploymentName);
                steps.add(builder.buildRequest());
            }
        } else {
            if (ctx.isDomainMode() && allRelevantServerGroups && all && !steps.isDefined()) {
                throw new OperationFormatException("No enabled deployment found in any server-groups.");
            }
        }
        if (headers != null) {
            ModelNode opHeaders = composite.get(Util.OPERATION_HEADERS);
            opHeaders.set(headers);
        }
        return composite;
    }
}
