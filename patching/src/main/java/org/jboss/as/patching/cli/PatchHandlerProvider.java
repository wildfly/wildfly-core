/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandHandlerProvider;

/**
 * WARNING: NO MORE IN USE, REPLACED BY PatchCommand.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public class PatchHandlerProvider implements CommandHandlerProvider {

    @Override
    public CommandHandler createCommandHandler(CommandContext ctx) {
        return new PatchHandler(ctx);
    }

    @Override
    public String[] getNames() {
        return new String[] { PatchHandler.PATCH };
    }

    @Override
    public boolean isTabComplete() {
        return true;
    }
}
