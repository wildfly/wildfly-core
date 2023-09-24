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
public class FinallyHandler extends CommandHandlerWithHelp {

    public FinallyHandler() {
        super("finally", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        return flow != null && !flow.isInFinally();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {
        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        if(flow == null) {
            throw new CommandLineException("finally is available only in try-catch-finally control flow");
        }
        if(flow.isInFinally()) {
            throw new CommandLineException("Already in finally");
        }
        flow.moveToFinally();
    }
}
