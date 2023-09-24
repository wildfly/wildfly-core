/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.batch;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.operation.ParsedCommandLine;

/**
 *
 * @author Alexey Loubyansky
 */
public class BatchHoldbackHandler extends CommandHandlerWithHelp {

    public BatchHoldbackHandler() {
        super("batch-holdback");
        // purely for validation, so the arguments are recognized
        new ArgumentWithValue(this, 0, "--name");
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
            throw new CommandFormatException("No active batch to holdback.");
        }

        String name = null;
        ParsedCommandLine args = ctx.getParsedCommandLine();
        if(args.hasProperties()) {
            name = args.getOtherProperties().get(0);
        }

        if(batchManager.isHeldback(name)) {
            throw new CommandFormatException("There already is " + (name == null ? "unnamed" : "'" + name + "'") + " batch held back.");
        }

        if(!batchManager.holdbackActiveBatch(name)) {
            throw new CommandFormatException("Failed to holdback the batch.");
        }
    }
}
