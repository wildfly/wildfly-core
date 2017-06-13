/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.commands.batch;

import java.io.File;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.utils.Config;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.batch.Batch;
import org.jboss.as.cli.batch.BatchManager;
import org.jboss.as.cli.batch.BatchedCommand;
import org.jboss.as.cli.impl.aesh.commands.deprecated.LegacyBridge;
import org.jboss.as.cli.impl.aesh.completer.HeadersCompleter;
import org.jboss.as.cli.impl.aesh.converter.HeadersConverter;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.wildfly.core.cli.command.DMRCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = BatchCommand.RUN, description = "", activator = BatchActivator.class)
public class BatchRunCommand implements Command<CLICommandInvocation>, DMRCommand, LegacyBridge {

    @Deprecated
    @Option(name = "help", hasValue = false, activator = HideOptionActivator.class)
    public boolean help;

    @Option(name = "verbose", hasValue = false, required=false, shortName = 'v')
    public boolean verbose;

    @Option(name = "headers", completer = HeadersCompleter.class,
            converter = HeadersConverter.class, required=false)
    public ModelNode headers;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation)
            throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("batch " + BatchCommand.RUN));
            return CommandResult.SUCCESS;
        }
        //Legacy compliance
        CommandResult res = BatchCommand.handle(commandInvocation, BatchCommand.RUN);
        if (res != null) {
            return res;
        }
        return execute(commandInvocation.getCommandContext());
    }

    @Override
    public CommandResult execute(CommandContext context)
            throws CommandException {
        boolean failed = false;
        OperationResponse response;
        Attachments attachments = getAttachments(context);
        try {
            final ModelNode request = newRequest(context);
            OperationBuilder builder = new OperationBuilder(request, true);
            for (String path : attachments.getAttachedFiles()) {
                builder.addFileAsAttachment(new File(path));
            }
            final ModelControllerClient client = context.getModelControllerClient();
            try {
                response = client.executeOperation(builder.build(), OperationMessageHandler.DISCARD);
            } catch (Exception e) {
                throw new CommandFormatException("Failed to perform operation: "
                        + e.getLocalizedMessage());
            }
            if (!Util.isSuccess(response.getResponseNode())) {
                String msg = formatBatchError(context, response.getResponseNode());
                if (msg == null) {
                    msg = Util.getFailureDescription(response.getResponseNode());
                }
                throw new CommandFormatException(msg);
            }

            ModelNode steps = response.getResponseNode().get(Util.RESULT);
            if (steps.isDefined()) {
                // Dispatch to non null response handlers.
                final Batch batch = context.getBatchManager().getActiveBatch();
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

        } catch (CommandFormatException e) {
            failed = true;
            throw new CommandException("The batch failed with the following error "
                    + "(you are remaining in the batch editing mode to have a "
                    + "chance to correct the error) " + e.getMessage(), e);
        } catch (CommandLineException e) {
            failed = true;
            throw new CommandException("The batch failed with the following error "
                    + e.getMessage(), e);
        } finally {
            attachments.done();
            if (!failed) {
                if (context.getBatchManager().isBatchActive()) {
                    context.getBatchManager().discardActiveBatch();
                }
            }
        }
        if (verbose) {
            context.println(response.getResponseNode().toString());
        } else {
            context.println("The batch executed successfully");
            CommandUtil.displayResponseHeaders(context, response.getResponseNode());
        }
        return CommandResult.SUCCESS;
    }

    public ModelNode newRequest(CommandContext context) throws CommandLineException {
        final BatchManager batchManager = context.getBatchManager();
        if (batchManager.isBatchActive()) {
            final Batch batch = batchManager.getActiveBatch();
            List<BatchedCommand> currentBatch = batch.getCommands();
            if (currentBatch.isEmpty()) {
                batchManager.discardActiveBatch();
                throw new CommandLineException("The batch is empty.");
            }
            ModelNode request = batch.toRequest();
            if (headers != null) {
                request.get(Util.OPERATION_HEADERS).set(headers);
            }
            return request;
        }
        throw new CommandLineException("Command can be executed only in the batch mode.");
    }

    @Override
    public ModelNode buildRequest(CommandContext context)
            throws CommandFormatException {
        try {
            return newRequest(context);
        } catch (CommandLineException ex) {
            throw new CommandFormatException(ex);
        }
    }

    public static Attachments getAttachments(CommandContext context) {
        final BatchManager batchManager = context.getBatchManager();
        if (batchManager.isBatchActive()) {
            final Batch batch = batchManager.getActiveBatch();
            return batch.getAttachments();
        }
        return new Attachments();
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
}
