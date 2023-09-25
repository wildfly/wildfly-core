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
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithValue;

/**
 *
 * @author Alexey Loubyansky
 */
public class BatchRemoveLineHandler extends CommandHandlerWithHelp {

    public BatchRemoveLineHandler() {
        super("batch-line-remove");
        // purely for validation, so the arguments are recognized
        new ArgumentWithValue(this, 0, "--line");
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

        Batch batch = batchManager.getActiveBatch();
        final int batchSize = batch.size();
        if(batchSize == 0) {
            throw new CommandFormatException("The batch is empty.");
        }

        List<String> arguments = ctx.getParsedCommandLine().getOtherProperties();
        if(arguments.isEmpty()) {
            throw new CommandFormatException("Missing line number.");
        }

        if(arguments.size() != 1) {
            throw new CommandFormatException("Expected only one argument - the line number but received: " + arguments);
        }

        String intStr = arguments.get(0);
        int lineNumber;
        try {
            lineNumber = Integer.parseInt(intStr);
        } catch(NumberFormatException e) {
            throw new CommandFormatException("Failed to parse line number '" + intStr + "': " + e.getLocalizedMessage());
        }

        if(lineNumber < 1 || lineNumber > batchSize) {
            throw new CommandFormatException(lineNumber + " isn't in range [1.." + batchSize + "].");
        }

        batch.remove(lineNumber - 1);
    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return index < 1;
    }
}
