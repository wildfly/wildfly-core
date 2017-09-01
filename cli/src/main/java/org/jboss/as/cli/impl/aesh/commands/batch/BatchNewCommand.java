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
package org.jboss.as.cli.impl.aesh.commands.batch;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.NEW, description = "", activator = NoBatchActivator.class)
public class BatchNewCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.NEW));
            return CommandResult.SUCCESS;
        }
        //Legacy compliance
        CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.NEW);
        if (res != null) {
            return res;
        }
        return handle(commandInvocation);
    }

    static CommandResult handle(CLICommandInvocation commandInvocation) throws CommandException {
        if (commandInvocation.getCommandContext().getBatchManager().isBatchActive()) {
            throw new CommandException("Can't start a new batch while in batch mode.");
        }
        if (!commandInvocation.getCommandContext().getBatchManager().activateNewBatch()) {
            // that's more like illegal state
            throw new CommandException("Failed to activate batch.");
        }
        return CommandResult.SUCCESS;
    }

}
