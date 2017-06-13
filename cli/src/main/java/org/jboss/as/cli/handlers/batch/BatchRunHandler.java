/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.File;
import org.aesh.command.CommandException;
import org.jboss.as.cli.Attachments;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.handlers.BaseOperationCommand;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.cli.impl.aesh.commands.batch.BatchRunCommand;
import org.jboss.as.cli.impl.aesh.commands.batch.BatchRunFileCommand;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
@Deprecated
public class BatchRunHandler extends BaseOperationCommand {

    private final ArgumentWithValue file;
    private final ArgumentWithoutValue verbose;

    public BatchRunHandler(CommandContext ctx) {
        super(ctx, "batch-run", true);

        final FilenameTabCompleter pathCompleter = FilenameTabCompleter.newCompleter(ctx);
        file = new FileSystemPathArgument(this, pathCompleter, "--file");

        verbose = new ArgumentWithoutValue(this, "--verbose", "-v");
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final boolean v = verbose.isPresent(ctx.getParsedCommandLine());
        String f = file.getValue(ctx.getParsedCommandLine());
        final ModelNode headersNode = headers.isPresent(ctx.getParsedCommandLine()) ? headers.toModelNode(ctx) : null;
        try {
            if (f != null) {
                BatchRunFileCommand cmd = new BatchRunFileCommand();
                cmd.verbose = v;
                cmd.file = new File(f);
                cmd.headers = headersNode;
                cmd.execute(ctx);
            } else {
                BatchRunCommand cmd = new BatchRunCommand();
                cmd.headers = headersNode;
                cmd.verbose = v;
                cmd.execute(ctx);
            }
        } catch (CommandException ex) {
            throw new CommandFormatException(ex.getLocalizedMessage());
        }
    }

    @Override
    protected ModelNode buildRequestWOValidation(CommandContext ctx) throws CommandFormatException {
        final boolean v = verbose.isPresent(ctx.getParsedCommandLine());
        String f = file.getValue(ctx.getParsedCommandLine());
        final ModelNode headersNode = headers.isPresent(ctx.getParsedCommandLine()) ? headers.toModelNode(ctx) : null;
        try {
            if (f != null) {
                BatchRunFileCommand cmd = new BatchRunFileCommand();
                cmd.verbose = v;
                cmd.file = new File(f);
                cmd.headers = headersNode;
                return cmd.newRequest(ctx);
            } else {
                BatchRunCommand cmd = new BatchRunCommand();
                cmd.headers = headersNode;
                cmd.verbose = v;
                return cmd.newRequest(ctx);
            }
        } catch (CommandLineException ex) {
            throw new CommandFormatException(ex.getLocalizedMessage());
        }
    }

    @Override
    public Attachments getAttachments(CommandContext ctx) {
        return BatchRunCommand.getAttachments(ctx);
    }

    @Override
    protected void handleResponse(CommandContext ctx, ModelNode response, boolean composite) throws CommandLineException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {
        throw new UnsupportedOperationException();
    }
}
