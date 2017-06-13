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
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.impl.aesh.commands.deprecated.LegacyBridge;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.MOVE_LINE, description = "", activator = BatchActivator.class)
public class BatchMvLineCommand implements Command<CLICommandInvocation>, LegacyBridge {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    protected boolean help;

    @Option(name="current-line-number", hasValue = true, required = true)
    public Integer currentLine;

    @Option(name="new-line-number", hasValue = true, required = true)
    public Integer newLine;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.MOVE_LINE));
            return CommandResult.SUCCESS;
        }

        if (currentLine == null && newLine == null) {
            //Legacy compliance
            CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.MOVE_LINE);
            if (res != null) {
                return res;
            }
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext ctx)
            throws CommandException {
        BatchManager batchManager = ctx.getBatchManager();

        if (!batchManager.isBatchActive()) {
            throw new CommandException("No active batch.");
        }

        Batch batch = batchManager.getActiveBatch();
        final int batchSize = batch.size();
        if (batchSize == 0) {
            throw new CommandException("The batch is empty.");
        }

        if (currentLine == null || newLine == null) {
            throw new CommandException("Expected two options.");
        }

        if (currentLine < 1 || currentLine > batchSize) {
            throw new CommandException(currentLine + " isn't in range [1.." + batchSize + "].");
        }

        if (newLine < 1 || newLine > batchSize) {
            throw new CommandException(newLine + " isn't in range [1.." + batchSize + "].");
        }

        batch.move(currentLine - 1, newLine - 1);

        return CommandResult.SUCCESS;
    }

}
