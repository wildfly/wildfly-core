/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.trycatch;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 *
 * @author Alexey Loubyansky
 */
public class TryHandler extends CommandHandlerWithHelp {

    public TryHandler() {
        super("try", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(TryCatchFinallyControlFlow.get(ctx) != null || ctx.getBatchManager().isBatchActive()) {
            return false;
        }
        return super.isAvailable(ctx);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final BatchManager batchManager = ctx.getBatchManager();
        if(batchManager.isBatchActive()) {
            throw new CommandFormatException("try is not allowed while in batch mode.");
        }
        ctx.registerRedirection(new TryCatchFinallyControlFlow(ctx));
    }
}
