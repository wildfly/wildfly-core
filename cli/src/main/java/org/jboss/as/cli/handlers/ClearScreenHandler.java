/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandContext;

/**
 *
 * @author Alexey Loubyansky
 */
public class ClearScreenHandler extends CommandHandlerWithHelp {

    public ClearScreenHandler() {
        super("clear");
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#handle(org.jboss.as.cli.CommandContext, java.lang.String)
     */
    @Override
    protected void doHandle(CommandContext ctx) {
        ctx.clearScreen();
    }
}
