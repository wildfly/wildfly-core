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
package org.jboss.as.cli.impl.aesh.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.aesh.command.completer.OptionCompleter;
import org.aesh.command.Command;
import org.aesh.command.CommandException;
import org.aesh.command.CommandResult;
import org.jboss.as.cli.Attachments;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.wildfly.core.cli.command.aesh.CLICompleterInvocation;
import org.wildfly.core.cli.command.aesh.activator.HideOptionActivator;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.cli.command.BatchCompliantCommand;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "display", description = "")
public class AttachmentDisplayCommand implements Command<CLICommandInvocation>, BatchCompliantCommand {

    public static class OperationCompleter implements OptionCompleter<CLICompleterInvocation> {

        @Override
        public void complete(CLICompleterInvocation completerInvocation) {
            List<String> candidates = new ArrayList<>();
            String buff = completerInvocation.getGivenCompleteValue();
            int offset = completerInvocation.getCommandContext().
                    getDefaultCommandCompleter().complete(completerInvocation.getCommandContext(),
                            buff, buff.length(), candidates);
            if (offset >= 0) {
                completerInvocation.setOffset(buff.length() - offset);
                Collections.sort(candidates);
                completerInvocation.addAllCompleterValues(candidates);
                completerInvocation.setAppendSpace(false);
            }
        }
    }

    @Deprecated
    @Option(hasValue = false, activator = HideOptionActivator.class)
    private boolean help;

    @Option(hasValue = true, completer = OperationCompleter.class)
    private String operation;

    @Override
    public CommandResult execute(CLICommandInvocation commandInvocation) throws CommandException, InterruptedException {
        if (help) {
            commandInvocation.println(commandInvocation.getHelpInfo("attachment display"));
            return CommandResult.SUCCESS;
        }
        final ModelControllerClient client = commandInvocation.getCommandContext().getModelControllerClient();
        OperationResponse response;
        ModelNode request;
        try {
            request = buildRequest(commandInvocation.getCommandContext());
        } catch (CommandFormatException ex) {
            throw new CommandException(ex);
        }
        OperationBuilder builder = new OperationBuilder(request, true);
        try {
            response = client.executeOperation(builder.build(),
                    OperationMessageHandler.DISCARD);
        } catch (Exception e) {
            throw new CommandException("Failed to perform operation: "
                    + e.getLocalizedMessage());
        }
        if (!Util.isSuccess(response.getResponseNode())) {
            throw new CommandException(Util.getFailureDescription(response.getResponseNode()));
        }

        try {
            buildHandler(commandInvocation.getCommandContext()).
                    handleResponse(response.getResponseNode(), response);
        } catch (CommandLineException ex) {
            throw new CommandException(ex.getLocalizedMessage());
        }

        return CommandResult.SUCCESS;
    }

    @Override
    public BatchResponseHandler buildBatchResponseHandler(CommandContext commandContext, Attachments attachments) throws CommandLineException {
        AttachmentResponseHandler handler = buildHandler(commandContext);
        return (ModelNode step, OperationResponse response) -> {
            handler.handleResponse(step, response);
        };
    }

    @Override
    public ModelNode buildRequest(CommandContext context) throws CommandFormatException {
        return context.buildRequest(operation);
    }

    AttachmentResponseHandler buildHandler(CommandContext ctx) {
        return new AttachmentResponseHandler((String t) -> {
            ctx.println(t);
        }, null, false, false);
    }
}
