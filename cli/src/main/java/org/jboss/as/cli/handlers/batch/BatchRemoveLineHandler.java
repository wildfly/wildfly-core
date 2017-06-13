/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.cli.handlers.batch;

import java.util.List;
import org.aesh.command.CommandException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.aesh.commands.batch.BatchRmLineCommand;

/**
 *
 * @author Alexey Loubyansky
 */
@Deprecated
public class BatchRemoveLineHandler extends CommandHandlerWithHelp {

    public BatchRemoveLineHandler() {
        super("batch-line-remove");
        // purely for validation, so the arguments are recognized
        new ArgumentWithValue(this, 0, "--line");
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(!super.isAvailable(ctx)) {
            return false;
        }
        return ctx.isBatchMode();
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandFormatException {
        List<String> arguments = ctx.getParsedCommandLine().getOtherProperties();
        if(arguments.isEmpty()) {
            throw new CommandFormatException("Missing line number.");
        }

        if(arguments.size() != 1) {
            throw new CommandFormatException("Expected only one argument - the line number but received: " + arguments);
        }

        String intStr = arguments.get(0);
        Integer lineNumber;
        try {
            lineNumber = Integer.parseInt(intStr);
        } catch(NumberFormatException e) {
            throw new CommandFormatException("Failed to parse line number '" + intStr + "': " + e.getLocalizedMessage());
        }

        try {
            BatchRmLineCommand cmd = new BatchRmLineCommand();
            cmd.line = lineNumber;
            cmd.execute(ctx);
        } catch (CommandException ex) {
            throw new CommandFormatException(ex.getLocalizedMessage());
        }

    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return index < 1;
    }
}
