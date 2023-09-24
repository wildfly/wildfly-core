/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.operation.OperationRequestAddress;

/**
 *
 * @author Alexey Loubyansky
 */
public class PrintWorkingNodeHandler extends CommandHandlerWithHelp {

    public PrintWorkingNodeHandler() {
        super("pwn");
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#handle(org.jboss.as.cli.CommandContext, java.lang.String)
     */
    @Override
    protected void doHandle(CommandContext ctx) {
        OperationRequestAddress prefix = ctx.getCurrentNodePath();
        ctx.printLine(ctx.getNodePathFormatter().format(prefix));
    }
}
