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
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.batch.BatchManager;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.HOLDBACK, description = "", activator = BatchActivator.class)
public class BatchHoldbackCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    protected boolean help;

    @Argument(required = true)
    public String batchName;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.HOLDBACK));
            return CommandResult.SUCCESS;
        }

        if (batchName == null) {
            //Legacy compliance
            CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.HOLDBACK);
            if (res != null) {
                return res;
            }
        }

        return execute(commandInvocation.getCommandContext());
    }

    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        BatchManager batchManager = ctx.getBatchManager();
        if (!batchManager.isBatchActive()) {
            throw new CommandException("No active batch to holdback.");
        }

        if (batchName == null || batchName.isEmpty()) {
            throw new CommandException("No batch name to holdback");
        }
        if (batchManager.isHeldback(batchName)) {
            throw new CommandException("There already is "
                    + (batchName == null ? "unnamed" : "'" + batchName
                            + "'") + " batch held back.");
        }

        if (!batchManager.holdbackActiveBatch(batchName)) {
            throw new CommandException("Failed to holdback the batch.");
        }
        return CommandResult.SUCCESS;
    }

}
