/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands;

import org.aesh.command.option.Argument;
import org.aesh.command.CommandDefinition;
import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.command.CommandException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.wildfly.core.cli.command.aesh.ValueResolverConverter;

@CommandDefinition(name = "connect", description = "")
public class ConnectCommand implements Command<CLICommandInvocation> {

    @Argument(converter = ValueResolverConverter.class)
    private String controller;

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option
    public String bind;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {

        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("connect"));
            return CommandResult.SUCCESS;
        }

        try {
            commandInvocation.getCommandContext().connectController(controller, bind);
        } catch (CommandLineException ex) {
            throw new CommandException(Util.getMessagesFromThrowable(ex));
        }
        return CommandResult.SUCCESS;
    }
}
