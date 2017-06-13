/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands.deployment;

import org.jboss.as.cli.impl.aesh.commands.deployment.security.Permissions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.commands.security.ControlledCommandActivator;
import org.jboss.as.cli.impl.aesh.commands.deployment.security.Activators.NameActivator;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.jboss.as.cli.impl.aesh.commands.deprecated.LegacyBridge;

/**
 * XXX jfdenise, all fields are public to be accessible from legacy view. To be
 * made private when removed.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "enable", description = "", activator = ControlledCommandActivator.class)
public class EnableCommand extends AbstractDeployCommand implements LegacyBridge {

    public static class NameCompleter
            implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            if (completerInvocation.getCommandContext().getModelControllerClient() != null) {
                List<String> deployments
                        = Util.getDeployments(completerInvocation.getCommandContext().
                                getModelControllerClient());
                if (!deployments.isEmpty()) {
                    List<String> candidates = new ArrayList<>();
                    String opBuffer = completerInvocation.getGivenCompleteValue();
                    if (opBuffer.isEmpty()) {
                        candidates.addAll(deployments);
                    } else {
                        for (String name : deployments) {
                            if (name.startsWith(opBuffer)) {
                                candidates.add(name);
                            }
                        }
                        Collections.sort(candidates);
                    }
                    completerInvocation.addAllCompleterValues(candidates);
                }
            }
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
                steps.add(Util.configureDeploymentOperation(Util.ADD, name,
                        serverGroup));
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
