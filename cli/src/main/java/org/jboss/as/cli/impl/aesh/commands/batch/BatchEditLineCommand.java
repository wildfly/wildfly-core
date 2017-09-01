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

import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.impl.aesh.commands.operation.OperationCommandContainer;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.EDIT_LINE, description = "", activator = BatchActivator.class)
public class BatchEditLineCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    protected boolean help;

    // XXX JFDENISE AESH-401
    @Arguments(completer = CommandCompleter.class) // required = true
    List<String> cmd;

    @Option(name = "line-number", required = true)
    private Integer line_number;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.EDIT_LINE));
            return CommandResult.SUCCESS;
        }

        if ((cmd == null || cmd.isEmpty()) && line_number == null) {
            //Legacy compliance
            CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.EDIT_LINE);
            if (res != null) {
                return res;
            }
        }
        StringBuilder builder = new StringBuilder();
        String editedLine = null;
        if (cmd != null) {
            for (String s : cmd) {
                builder.append(s).append(" ");
            }
            editedLine = builder.toString();
        }
        return execute(commandInvocation.
                getCommandContext(), line_number, editedLine);
    }

    public static CommandResult execute(CommandContext ctx, Integer lineNumber, String editedLine)
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

        if (lineNumber == null) {
            throw new CommandException("Missing line number.");
        }

        if (editedLine == null) {
            throw new CommandException("Missing the new command line.");
        }

        if (lineNumber < 1 || lineNumber > batchSize) {
            throw new RuntimeException(lineNumber + " isn't in range [1.."
                    + batchSize + "].");
        }


        if (editedLine.charAt(0) == '"') {
            if (editedLine.length() > 1
                    && editedLine.charAt(editedLine.length() - 1) == '"') {
                editedLine = editedLine.substring(1, editedLine.length() - 1);
            }
        }

        try {
            BatchedCommand newCmd = ctx.toBatchedCommand(editedLine);
            batch.set(lineNumber - 1, newCmd);
            ctx.println("#" + lineNumber
                    + " " + newCmd.getCommand());
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
        return CommandResult.SUCCESS;
    }

    public static class CommandCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            BatchEditLineCommand cmd = (BatchEditLineCommand) completerInvocation.getCommand();
            StringBuilder existingBuilder = new StringBuilder();
            List<String> command = cmd.cmd;
            if (command != null) {
                for (int i = 0; i < command.size(); i++) {
                    existingBuilder.append(command.get(i));
                    existingBuilder.append(" ");
                }
            }
            List<String> candidates = new ArrayList<>();
            String existingCommand = existingBuilder.toString();
            String buffer = existingCommand
                    + (completerInvocation.getGivenCompleteValue() != null ? completerInvocation.getGivenCompleteValue() : "");
            int cursor = buffer.length();
            CommandContext ctx = completerInvocation.getCommandContext();
            int offset = ctx.getDefaultCommandCompleter().complete(ctx,
                    buffer,
                    cursor,
                    candidates);
            if (!candidates.isEmpty()) {
                if (OperationCommandContainer.isOperation(buffer)) {
                    completerInvocation.addAllCompleterValues(candidates);
                    if (offset >= 0) {
                        completerInvocation.setOffset(cursor - offset);
                    }
                    completerInvocation.setAppendSpace(false);
                } else {
                    // XXX JFDENISE TODO
                }
            }
        }

    }

}
