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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommand;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.parser.CommandLineParserException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.AccessRequirements;
import org.jboss.as.cli.impl.aesh.cmd.deployment.security.Permissions;
import org.jboss.as.cli.impl.aesh.cmd.security.ControlledCommandActivator;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * The parent command of all deployment commands.
 *
 * @author jdenise@redhat.com
 */
@GroupCommandDefinition(name = "deployment", description = "", activator = ControlledCommandActivator.class)
public class DeploymentCommand extends CommandWithPermissions
        implements GroupCommand<CLICommandInvocation> {

    public DeploymentCommand(CommandContext ctx, Permissions permissions) {
        super(ctx, AccessRequirements.deploymentAccess(permissions), permissions);
    }

    public static void registerDeploymentCommands(CommandContext ctx, CLICommandRegistry registry)
            throws CommandLineException, CommandLineParserException {
        Permissions p = new Permissions(ctx);
        DeploymentCommand deploy = new DeploymentCommand(ctx, p);
        registry.addCommand(deploy);
    }

    @Deprecated
    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        throw new CommandException("Command action is missing.");
    }

    @Override
    public List<Command<CLICommandInvocation>> getCommands() {
        List<Command<CLICommandInvocation>> commands = new ArrayList<>();
        commands.add(new EnableCommand(getCommandContext(), getPermissions()));
        commands.add(new EnableAllCommand(getCommandContext(), getPermissions()));
        commands.add(new DeployArchiveCommand(getCommandContext(), getPermissions()));
        commands.add(new DeployUrlCommand(getCommandContext(), getPermissions()));
        commands.add(new DeployFileCommand(getCommandContext(), getPermissions()));
        commands.add(new ListCommand(getCommandContext(), getPermissions()));
        commands.add(new InfoCommand(getCommandContext(), getPermissions()));
        commands.add(new UndeployCommand(getCommandContext(), getPermissions()));
        commands.add(new DisableAllCommand(getCommandContext(), getPermissions()));
        commands.add(new DisableCommand(getCommandContext(), getPermissions()));
        commands.add(new UndeployArchiveCommand(getCommandContext(), getPermissions()));

        return commands;
    }

    static List<String> getServerGroups(CommandContext ctx,
            ModelControllerClient client,
            boolean allServerGroups, String serverGroups, File f)
            throws CommandFormatException {
        List<String> sgList = null;
        if (ctx.isDomainMode()) {
            if (allServerGroups) {
                if (serverGroups != null) {
                    throw new CommandFormatException("--all-server-groups and "
                            + "--server-groups can't appear in the same command");
                }
                sgList = Util.getServerGroups(client);
                if (sgList.isEmpty()) {
                    throw new CommandFormatException("No server group is available.");
                }
            } else if (serverGroups == null) {
                final StringBuilder buf = new StringBuilder();
                buf.append("One of ");
                if (f != null) {
                    buf.append("--disabled,");
                }
                buf.append(" --all-server-groups or --server-groups is missing.");
                throw new CommandFormatException(buf.toString());
            } else {
                sgList = Arrays.asList(serverGroups.split(","));
                if (sgList.isEmpty()) {
                    throw new CommandFormatException("Couldn't locate server "
                            + "group name in '--server-groups=" + serverGroups
                            + "'.");
                }
            }
        } else if (serverGroups != null || allServerGroups) {
            throw new CommandFormatException("--server-groups and --all-server-groups "
                    + "can't appear in standalone mode.");
        }
        return sgList;
    }
}
