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
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandNotFoundException;
import org.aesh.command.CommandResult;
import org.aesh.command.impl.parser.CommandLineParser;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.impl.aesh.CLICommandRegistry;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "list-all", description = "")
public class CommandsListAll extends AbstractListCommand implements Command<CLICommandInvocation> {

    private final CLICommandRegistry aeshRegistry;

    public CommandsListAll(CLICommandRegistry aeshRegistry) {
        this.aeshRegistry = aeshRegistry;
    }

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        CommandContext ctx = commandInvocation.getCommandContext();
        List<String> lst = new ArrayList<>();
        for (String c : aeshRegistry.getAllCommandNames()) {
            CommandLineParser<? extends Command> cmdParser;
            try {
                cmdParser = aeshRegistry.findCommand(c, null);
            } catch (CommandNotFoundException ex) {
                continue;
            }
            if (cmdParser.isGroupCommand()) {
                for (CommandLineParser child : cmdParser.getAllChildParsers()) {
                    lst.add(c + " " + child.getProcessedCommand().name());
                }
            } else {
                lst.add(c);
            }
        }
        print(lst, ctx);
        ctx.println("To read a description of a specific command execute 'help <command name>'.");

        return CommandResult.SUCCESS;
    }

}
