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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.impl.internal.ProcessedCommand;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.AbstractCommandActivator;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.REACTIVATE, description = "",
        activator = BatchReactivateCommand.ReactivateBatchActivator.class)
public class BatchReactivateCommand implements Command<CLICommandInvocation> {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Argument(required = true, completer = BatchNameCompleter.class)
    private String name;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.REACTIVATE));
            return CommandResult.SUCCESS;
        }
        if (name == null || name.isEmpty()) {
            //Legacy compliance
            CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.REACTIVATE);
            if (res != null) {
                return res;
            }
        }
        return handle(commandInvocation, name);
    }

    static CommandResult handle(CLICommandInvocation commandInvocation, String batchName) throws CommandException {
        CommandContext ctx = commandInvocation.getCommandContext();
        BatchManager batchManager = ctx.getBatchManager();

        if (batchName == null) {
            throw new CommandException("No batch name to re-activate");
        }
        if (batchManager.isHeldback(batchName)) {
            boolean activated = batchManager.activateHeldbackBatch(batchName);
            if (activated) {
                final String msg = batchName == null ? "Re-activated batch"
                        : "Re-activated batch '" + batchName + "'";
                commandInvocation.println(msg);
                List<BatchedCommand> batch = batchManager.getActiveBatch().getCommands();
                if (!batch.isEmpty()) {
                    for (int i = 0; i < batch.size(); ++i) {
                        BatchedCommand cmd = batch.get(i);
                        commandInvocation.println("#"
                                + (i + 1) + ' ' + cmd.getCommand());
                    }
                }
            } else {
                // that's more like illegal state
                throw new CommandException("Failed to activate batch.");
            }
        } else {
            throw new CommandException("'" + batchName
                    + "' not found among the held back batches.");
        }
        return CommandResult.SUCCESS;
    }

    public static class BatchNameCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            List<String> candidates = new ArrayList<>();
            int cursor = complete(completerInvocation.getCommandContext(),
                    completerInvocation.getGivenCompleteValue(), candidates);
            completerInvocation.addAllCompleterValues(candidates);
            completerInvocation.setOffset(completerInvocation.getGivenCompleteValue().length() - cursor);
            completerInvocation.setAppendSpace(false);
        }

        private int complete(CommandContext ctx, String buffer, List<String> candidates) {
            BatchManager batchManager = ctx.getBatchManager();
            Set<String> names = batchManager.getHeldbackNames();
            if (names.isEmpty()) {
                return -1;
            }

            int nextCharIndex = 0;
            while (nextCharIndex < buffer.length()) {
                if (!Character.isWhitespace(buffer.charAt(nextCharIndex))) {
                    break;
                }
                ++nextCharIndex;
            }

            String chunk = buffer.substring(nextCharIndex).trim();
            for (String name : names) {
                if (name != null && name.startsWith(chunk)) {
                    candidates.add(name);
                }
            }
            Collections.sort(candidates);
            return nextCharIndex;
        }

    }

    public static class ReactivateBatchActivator extends AbstractCommandActivator {

        public ReactivateBatchActivator() {
        }

        @Override
        public boolean isActivated(ProcessedCommand cmd) {
            return !getCommandContext().isBatchMode()
                    && !getCommandContext().getBatchManager().getHeldbackNames().isEmpty();
        }
    }

}
