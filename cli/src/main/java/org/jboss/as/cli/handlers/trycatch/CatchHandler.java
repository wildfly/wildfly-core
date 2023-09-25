/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.trycatch;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 *
 * @author Alexey Loubyansky
 */
public class CatchHandler extends CommandHandlerWithHelp {

    public CatchHandler() {
        super("catch", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        return flow != null && flow.isInTry();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        if(flow == null) {
            throw new CommandLineException("catch is available only in try-catch-finally control flow");
        }
        if(flow.isInTry()) {
            flow.moveToCatch();
        } else {
            throw new CommandLineException("catch may appear only once after try and before finally");
        }
    }
}
