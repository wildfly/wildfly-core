/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.batch;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 *
 * @author Alexey Loubyansky
 */
public class BatchListHandler extends CommandHandlerWithHelp {

    public BatchListHandler() {
        super("batch-list");
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(!super.isAvailable(ctx)) {
            return false;
        }
        return ctx.isBatchMode();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {
        BatchManager batchManager = ctx.getBatchManager();
        if(!batchManager.isBatchActive()) {
            throw new CommandFormatException("No active batch.");
        }
        Batch activeBatch = batchManager.getActiveBatch();
        List<BatchedCommand> commands = activeBatch.getCommands();
        if (!commands.isEmpty()) {
            for (int i = 0; i < commands.size(); ++i) {
                BatchedCommand cmd = commands.get(i);
                ctx.printLine("#" + (i + 1) + ' ' + cmd.getCommand());
            }
        } else {
            ctx.printLine("The batch is empty.");
        }
    }

}
