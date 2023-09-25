/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;


import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.accesscontrol.AccessRequirementBuilder;
import org.jboss.as.cli.impl.ArgumentWithListValue;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.CommaSeparatedCompleter;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DeployArchiveCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.DisableCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.ListCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.UndeployArchiveCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.UndeployCommand;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Alexey Loubyansky
 */
@Deprecated
public class UndeployHandler extends DeploymentHandler {

    private final ArgumentWithoutValue l;
    private final ArgumentWithoutValue path;
    private final ArgumentWithValue name;
    private final ArgumentWithListValue serverGroups;
    private final ArgumentWithoutValue allRelevantServerGroups;
    private final ArgumentWithoutValue keepContent;
    private final ArgumentWithValue script;

    private AccessRequirement listPermission;
    private AccessRequirement mainRemovePermission;
    private AccessRequirement undeployPermission;

    public UndeployHandler(CommandContext ctx) {
        super(ctx, "undeploy", true);

        final DefaultOperationRequestAddress requiredAddress = new DefaultOperationRequestAddress();
        requiredAddress.toNodeType(Util.DEPLOYMENT);
        addRequiredPath(requiredAddress);

        l = new ArgumentWithoutValue(this, "-l");
        l.setExclusive(true);
        l.setAccessRequirement(listPermission);

        final AccessRequirement removeOrUndeployPermission = AccessRequirementBuilder.Factory.create(ctx)
                .any()
                .requirement(mainRemovePermission)
                .requirement(undeployPermission)
                .build();

        name = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {

                ParsedCommandLine args = ctx.getParsedCommandLine();
                try {
                    if(path.isPresent(args)) {
                        return -1;
                    }
                } catch (CommandFormatException e) {
                    return -1;
                }

                int nextCharIndex = 0;
                while (nextCharIndex < buffer.length()) {
                    if (!Character.isWhitespace(buffer.charAt(nextCharIndex))) {
                        break;
                    }
                    ++nextCharIndex;
                }

                if(ctx.getModelControllerClient() != null) {
                    List<String> deployments = Util.getDeployments(ctx.getModelControllerClient());
                    if(deployments.isEmpty()) {
                        return -1;
                    }

                    String opBuffer = buffer.substring(nextCharIndex).trim();
                    if (opBuffer.isEmpty()) {
                        candidates.addAll(deployments);
                    } else {
                        for(String name : deployments) {
                            if(name.startsWith(opBuffer)) {
                                candidates.add(name);
                            }
                        }
                        Collections.sort(candidates);
                    }
                    return nextCharIndex;
                } else {
                    return -1;
                }

            }}, 0, "--name");
        name.addCantAppearAfter(l);
        name.setAccessRequirement(removeOrUndeployPermission);

        allRelevantServerGroups = new ArgumentWithoutValue(this, "--all-relevant-server-groups") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };
        allRelevantServerGroups.addRequiredPreceding(name);
        allRelevantServerGroups.setAccessRequirement(undeployPermission);

        serverGroups = new ArgumentWithListValue(this, new CommaSeparatedCompleter() {
            @Override
            protected Collection<String> getAllCandidates(CommandContext ctx) {
              final String deploymentName = name.getValue(ctx.getParsedCommandLine());
              final List<String> allGroups;
//              if(deploymentName == null) {
//                  allGroups = Util.getServerGroups(ctx.getModelControllerClient());
//              } else {
                  try {
                    allGroups = Util.getServerGroupsReferencingDeployment(deploymentName, ctx.getModelControllerClient());
                } catch (CommandLineException e) {
                    e.printStackTrace();
                    return Collections.emptyList();
                }
//              }
                  return allGroups;
            }}, "--server-groups") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!ctx.isDomainMode()) {
                    return false;
                }
                return super.canAppearNext(ctx);
            }
        };
        serverGroups.addRequiredPreceding(name);
        serverGroups.setAccessRequirement(undeployPermission);

        serverGroups.addCantAppearAfter(allRelevantServerGroups);
        allRelevantServerGroups.addCantAppearAfter(serverGroups);

        keepContent = new ArgumentWithoutValue(this, "--keep-content");
        keepContent.addRequiredPreceding(name);
        keepContent.setAccessRequirement(undeployPermission);

        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
        path = new FileSystemPathArgument(this, pathCompleter, "--path");
        path.addCantAppearAfter(l);
        path.setAccessRequirement(removeOrUndeployPermission);

        script = new ArgumentWithValue(this, "--script");
        script.addRequiredPreceding(path);
    }

    @Override
    protected AccessRequirement setupAccessRequirement(CommandContext ctx) {
        Permissions permissions = new Permissions(ctx);
        listPermission = permissions.getListPermission();
        mainRemovePermission = permissions.getMainRemovePermission();
        undeployPermission = permissions.getUndeployPermission();
        return AccessRequirements.undeployLegacyAccess(permissions).apply(ctx);
    }

    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final boolean l = this.l.isPresent(args);
        if (!args.hasProperties() || l) {
            try {
                ListCommand.listDeployments(ctx, l);
            } catch (CommandException ex) {
                throw new CommandLineException(ex.getLocalizedMessage());
            }
            return;
        }

        final String path = this.path.getValue(args);
        final File f;
        if (path != null) {
            f = new File(path);
            if (DeployArchiveCommand.isCliArchive(f)) {
                UndeployArchiveCommand command = new UndeployArchiveCommand(ctx);
                command.file = f;
                command.script = script.getValue(args);
                try {
                    command.execute(ctx);
                } catch (CommandException ex) {
                    throw new CommandLineException(ex.getLocalizedMessage());
                }
                return;
            }
        }

        final String name = this.name.getValue(ctx.getParsedCommandLine());
        if (name == null) {
            try {
                ListCommand.listDeployments(ctx, l);
            } catch (CommandException ex) {
                throw new CommandLineException(ex.getLocalizedMessage());
            }
            return;
        }
        UndeployCommand command = null;
        boolean keepContent = this.keepContent.isPresent(args);
        if (keepContent) {
            command = new DisableCommand(ctx);
        } else {
            command = new UndeployCommand(ctx);
        }
        command.allRelevantServerGroups = allRelevantServerGroups.isPresent(args);
        final ModelNode headersNode = headers.toModelNode(ctx);
        if (headersNode != null && headersNode.getType() != ModelType.OBJECT) {
            throw new CommandFormatException("--headers option has wrong value '" + headersNode + "'");
        }
        command.headers = headersNode;
        if (name != null) {
            command.name = name;
        }
        command.serverGroups = serverGroups.getValue(args);

        try {
            command.execute(ctx);
        } catch (CommandException ex) {
            throw new CommandLineException(ex.getLocalizedMessage());
        }
    }

    @Override
    public ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        String p = this.path.getValue(args);
        if (p != null) {
            File f = new File(p);
            if (DeployArchiveCommand.isCliArchive(f)) {
                UndeployArchiveCommand command = new UndeployArchiveCommand(ctx);
                command.file = f;
                command.script = script.getValue(args);
                return command.buildRequest(ctx);
            }
        }

        if (name == null) {
            throw new CommandFormatException("Deployment name is missing.");
        }

        UndeployCommand command = null;
        boolean keepContent = this.keepContent.isPresent(args);
        if (keepContent) {
            command = new DisableCommand(ctx);
        } else {
            command = new UndeployCommand(ctx);
        }
        final String name = this.name.getValue(ctx.getParsedCommandLine());
        command.allRelevantServerGroups = allRelevantServerGroups.isPresent(args);
        if (name != null) {
            command.name = name;
        }
        command.serverGroups = serverGroups.getValue(args);
        return command.buildRequest(ctx);
    }
}
