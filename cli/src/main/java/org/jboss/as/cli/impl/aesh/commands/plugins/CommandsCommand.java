/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands.plugins;

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.GroupCommand;
import org.aesh.command.GroupCommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.impl.CommandContextImpl;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@GroupCommandDefinition(name = "commands", description = "")
public class CommandsCommand implements GroupCommand<CLICommandInvocation, Command> {

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    private final CLICommandRegistry aeshRegistry;
    private final CommandContextImpl ctx;

    public CommandsCommand(CLICommandRegistry aeshRegistry, CommandContextImpl ctx) {
        this.aeshRegistry = aeshRegistry;
        this.ctx = ctx;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("commands"));
            return CommandResult.SUCCESS;
        }
        throw new CommandException("Command action is missing.");
    }

    @Override
    public List<Command> getCommands() {
        List<Command> commands = new ArrayList<>();
        commands.add(new CommandsLoadModulePlugins(ctx));
        commands.add(new CommandsListAvailable(aeshRegistry));
        commands.add(new CommandsListPlugins(ctx));
        commands.add(new CommandsListExtensions(ctx));
        commands.add(new CommandsExtensionsErrors(ctx));
        commands.add(new CommandsLoadJarPlugins(ctx));
        commands.add(new CommandsListAll(aeshRegistry));
        commands.add(new CommandsListDeprecated(aeshRegistry));
        commands.add(new CommandsListUnavailable(aeshRegistry));
        return commands;
    }
}
