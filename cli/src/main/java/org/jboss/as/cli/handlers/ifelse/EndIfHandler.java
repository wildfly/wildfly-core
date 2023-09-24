/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;

/**
 *
 * @author Alexey Loubyansky
 */
public class EndIfHandler extends CommandHandlerWithHelp {

    public EndIfHandler() {
        super("end-if", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return IfElseControlFlow.get(ctx) != null;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final IfElseControlFlow ifElse = IfElseControlFlow.get(ctx);
        if(ifElse == null) {
            throw new CommandLineException("end-if is not available outside if-else control flow");
        }
        ifElse.run(ctx);
    }
}
