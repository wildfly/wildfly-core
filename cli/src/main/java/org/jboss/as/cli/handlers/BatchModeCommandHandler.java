/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;


/**
 *
 * @author Alexey Loubyansky
 */
public abstract class BatchModeCommandHandler extends BaseOperationCommand {

    public BatchModeCommandHandler(CommandContext ctx, String command, boolean connectionRequired) {
        super(ctx, command, connectionRequired);
    }

    @Override
    public boolean isBatchMode(CommandContext ctx) {
        try {
            if(this.helpArg.isPresent(ctx.getParsedCommandLine())) {
                return false;
            }
        } catch (CommandFormatException e) {
            // this is not nice...
            // but if it failed here it won't be added to the batch,
            // will be executed immediately and will fail with the same exception
            return false;
        }
        return true;
    }
}
