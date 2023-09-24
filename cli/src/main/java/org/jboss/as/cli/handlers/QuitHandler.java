/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandContext;

/**
 * Quit handler.
 *
 * @author Alexey Loubyansky
 */
public class QuitHandler extends CommandHandlerWithHelp {

    public QuitHandler() {
        this("quit");
    }

    public QuitHandler(String command) {
        super(command);
    }

    @Override
    protected void doHandle(CommandContext ctx) {
        ctx.terminateSession();
    }
}
