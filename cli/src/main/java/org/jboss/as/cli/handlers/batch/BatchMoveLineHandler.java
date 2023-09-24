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
public class BatchMoveLineHandler extends CommandHandlerWithHelp {

    public BatchMoveLineHandler() {
        super("batch-move-line");
        // purely for validation, so the arguments are recognized
        new ArgumentWithValue(this, 0, "--current");
        new ArgumentWithValue(this, 1, "--new");
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

        if(arguments.size() != 2) {
            throw new CommandFormatException("Expected two arguments but received: " + arguments);
        }

        String intStr = arguments.get(0);
        final int lineNumber;
        try {
            lineNumber = Integer.parseInt(intStr);
        } catch(NumberFormatException e) {
            throw new CommandFormatException("Failed to parse line number '" + intStr + "': " + e.getLocalizedMessage());
        }

        if(lineNumber < 1 || lineNumber > batchSize) {
            throw new CommandFormatException(lineNumber + " isn't in range [1.." + batchSize + "].");
        }

        intStr = arguments.get(1);
        final int toLineNumber;
        try {
            toLineNumber = Integer.parseInt(intStr);
        } catch(NumberFormatException e) {
            throw new CommandFormatException("Failed to parse line number '" + intStr + "': " + e.getLocalizedMessage());
        }

        if(toLineNumber < 1 || toLineNumber > batchSize) {
            throw new CommandFormatException(toLineNumber + " isn't in range [1.." + batchSize + "].");
        }

        batch.move(lineNumber - 1, toLineNumber - 1);
    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return index < 2;
    }
}
