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


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import org.aesh.terminal.utils.Config;
import org.jboss.as.cli.Attachments;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.handlers.BaseOperationCommand;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.FileSystemPathArgument;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 *
 * @author Alexey Loubyansky
 */
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

        final OperationResponse response;
        boolean failed = false;
        boolean hasFile = file.getValue(ctx.getParsedCommandLine()) != null;
        try {
            final ModelNode request = buildRequest(ctx);
            OperationBuilder builder = new OperationBuilder(request, true);
            for (String path : getAttachments(ctx).getAttachedFiles()) {
                builder.addFileAsAttachment(new File(path));
            }
            final ModelControllerClient client = ctx.getModelControllerClient();
            try {
                response = client.executeOperation(builder.build(), OperationMessageHandler.DISCARD);
            } catch(Exception e) {
                throw new CommandFormatException("Failed to perform operation: " + e.getLocalizedMessage());
            }
            if (!Util.isSuccess(response.getResponseNode())) {
                String msg = formatBatchError(ctx, response.getResponseNode());
                if (msg == null) {
                    msg = Util.getFailureDescription(response.getResponseNode());
                }
                throw new CommandFormatException(msg);
            }

            ModelNode steps = response.getResponseNode().get(Util.RESULT);
            if (steps.isDefined()) {
                // Dispatch to non null response handlers.
                final Batch batch = ctx.getBatchManager().getActiveBatch();
                int i = 1;
                for (BatchedCommand cmd : batch.getCommands()) {
                    ModelNode step = steps.get("step-" + i);
                    if (step.isDefined()) {
                        if (cmd.getResponseHandler() != null) {
                            cmd.getResponseHandler().handleResponse(step, response);
                        }
                        i += 1;
                    }
                }
            }

        } catch(CommandLineException e) {
            failed = true;
            if (hasFile) {
                throw new CommandLineException("The batch failed with the following error: ", e);
            } else {
                throw new CommandLineException("The batch failed with the following error "
                        + "(you are remaining in the batch editing mode to have a chance to correct the error)", e);
            }
        } finally {
            // There is a change in behavior between the file and the added command
            // With a file, the batch is discarded, whatever the result.
            if (hasFile) {
                ctx.getBatchManager().discardActiveBatch();
            } else if (!failed) {
                if (ctx.getBatchManager().isBatchActive()) {
                    ctx.getBatchManager().discardActiveBatch();
                }
            }
        }

        if(v) {
            ctx.printDMR(response.getResponseNode());
        } else {
            ctx.printLine("The batch executed successfully");
            super.handleResponse(ctx, response.getResponseNode(), true);
        }
    }

    private static String formatBatchError(CommandContext ctx, ModelNode responseNode) {
        if (responseNode == null) {
            return null;
        }
        ModelNode mn = responseNode.get(Util.RESULT);
        String msg = null;
        try {
            if (mn.isDefined()) {
                int index = 0;
                Batch batch = ctx.getBatchManager().getActiveBatch();
                StringBuilder b = new StringBuilder();
                ModelNode fd = responseNode.get(Util.FAILURE_DESCRIPTION);
                if (fd.isDefined()) {
                    Property p = fd.asProperty();
                    b.append(Config.getLineSeparator()).
                            append(p.getName()).append(Config.getLineSeparator());
                    boolean foundError = false;
                    for (Property prop : mn.asPropertyList()) {
                        ModelNode val = prop.getValue();
                        if (val.hasDefined(Util.FAILURE_DESCRIPTION)) {
                            b.append("Step: step-").append(index + 1).
                                    append(Config.getLineSeparator());
                            b.append("Operation: ").append(batch.getCommands().
                                    get(index).getCommand()).append(Config.getLineSeparator());
                            b.append("Failure: ").append(val.get(Util.FAILURE_DESCRIPTION).asString()).
                                    append(Config.getLineSeparator());
                            foundError = true;
                        }
                        index += 1;
                    }
                    if (foundError) {
                        msg = b.toString();
                    }
                }
            }
        } catch (Exception ex) {
            // XXX OK, will fallback to null msg.
        }
        return msg;
    }

    @Override
    protected ModelNode buildRequestWOValidation(CommandContext ctx) throws CommandFormatException {

        final String path = file.getValue(ctx.getParsedCommandLine());
        final ModelNode headersNode = headers.isPresent(ctx.getParsedCommandLine()) ? headers.toModelNode(ctx) : null;

        final BatchManager batchManager = ctx.getBatchManager();
        if(batchManager.isBatchActive()) {
            if(path != null) {
                throw new CommandFormatException("--file is not allowed in the batch mode.");
            }
            final Batch batch = batchManager.getActiveBatch();
            List<BatchedCommand> currentBatch = batch.getCommands();
            if(currentBatch.isEmpty()) {
                batchManager.discardActiveBatch();
                throw new CommandFormatException("The batch is empty.");
            }
            final ModelNode request = batch.toRequest();
            if(headersNode != null) {
                request.get(Util.OPERATION_HEADERS).set(headersNode);
            }
            return request;
        }

        if(path != null) {
            final File f = new File(path);
            if(!f.exists()) {
                throw new CommandFormatException("File " + f.getAbsolutePath() + " does not exist.");
            }

            final File currentDir = ctx.getCurrentDir();
            final File baseDir = f.getParentFile();
            if(baseDir != null) {
                ctx.setCurrentDir(baseDir);
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line = reader.readLine();
                batchManager.activateNewBatch();
                while(line != null) {
                    ctx.handle(line.trim());
                    line = reader.readLine();
                }
                final ModelNode request = batchManager.getActiveBatch().toRequest();
                if(headersNode != null) {
                    request.get(Util.OPERATION_HEADERS).set(headersNode);
                }
                return request;
            } catch(IOException e) {
                throw new CommandFormatException("Failed to read file " + f.getAbsolutePath(), e);
            } catch(CommandLineException e) {
                throw new CommandFormatException("Failed to create batch from " + f.getAbsolutePath(), e);
            } finally {
                if(baseDir != null) {
                    ctx.setCurrentDir(currentDir);
                }
            }
        }

        throw new CommandFormatException("Without arguments the command can be executed only in the batch mode.");
    }

    @Override
    public Attachments getAttachments(CommandContext ctx) {
        final BatchManager batchManager = ctx.getBatchManager();
        if (batchManager.isBatchActive()) {
            final Batch batch = batchManager.getActiveBatch();
            return batch.getAttachments();
        }
        return new Attachments();
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
