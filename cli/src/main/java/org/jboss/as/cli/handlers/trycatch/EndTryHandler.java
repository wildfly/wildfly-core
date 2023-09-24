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
public class EndTryHandler extends CommandHandlerWithHelp {

    public EndTryHandler() {
        super("end-try", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        return flow != null && !flow.isInTry();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final TryCatchFinallyControlFlow flow = TryCatchFinallyControlFlow.get(ctx);
        if(flow == null) {
            throw new CommandLineException("end-if may appear only at the end of try-catch-finally control flow");
        }
        if(flow.isInTry()) {
            throw new CommandLineException("end-if may appear only after catch or finally");
        }
        flow.run(ctx);
    }
}
