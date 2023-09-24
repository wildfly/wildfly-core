/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.loop;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithoutValue;

/**
 *
 * @author jfdenise
 */
public class DoneHandler extends CommandHandlerWithHelp {

    private final ArgumentWithoutValue discard = new ArgumentWithoutValue(this, "--discard");

    public DoneHandler() {
        super("done", true);
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return ForControlFlow.get(ctx) != null;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ForControlFlow forCF = ForControlFlow.get(ctx);
        if (forCF == null) {
            throw new CommandLineException("done is not available outside 'for' loop");
        }
        forCF.run(ctx, discard.isPresent(ctx.getParsedCommandLine()));
    }
}
