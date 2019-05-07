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
package org.jboss.as.cli.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContext.Scope;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.RequestWithAttachments;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.dmr.ModelNode;

/**
 * The operation request handler.
 *
 * @author Alexey Loubyansky
 */
public class OperationRequestHandler implements CommandHandler, OperationCommand {

    @Override
    public boolean isBatchMode(CommandContext ctx) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandHandler#handle(org.jboss.as.cli.CommandContext)
     */
    @Override
    public void handle(CommandContext ctx) throws CommandLineException {

        ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            throw new CommandFormatException("You are disconnected at the moment." +
                    " Type 'connect' to connect to the server" +
                    " or 'help' for the list of supported commands.");
        }
        RequestWithAttachments reqWithAttachments
                = (RequestWithAttachments) ctx.get(Scope.REQUEST, "OP_REQ");
        if (reqWithAttachments == null) {
            throw new CommandLineException("Parsed request isn't available.");
        }
        ModelNode request = reqWithAttachments.getRequest();
        OperationBuilder opBuilder = new OperationBuilder(request, true);
        for (String path : reqWithAttachments.getAttachedFiles()) {
            opBuilder.addFileAsAttachment(new File(path));
        }
        Operation op = opBuilder.build();

        if(ctx.getConfig().isValidateOperationRequests()) {
            ModelNode opDescOutcome = Util.validateRequest(ctx, request);
            if (opDescOutcome != null) { // operation has params that might need to be replaced
                Util.replaceFilePathsWithBytes(request, opDescOutcome);
            }
        }

        try {
            final ModelNode result = ctx.execute(op, "Operation request");
            if (Util.isSuccess(result)) {
                ctx.printDMR(result);
            } else {
                if (!ctx.getConfig().isOutputJSON()) {
                    throw new CommandLineException(result.toString());
                } else {
                    throw new CommandLineException(result.toJSONString(false));
                }
            }
        } catch(NoSuchElementException e) {
            throw new CommandLineException("ModelNode request is incomplete", e);
        } catch (CancellationException e) {
            throw new CommandLineException("The result couldn't be retrieved (perhaps the task was cancelled", e);
        } catch (IOException e) {
            if (e.getCause() != null && !(e.getCause() instanceof InterruptedException)) {
                ctx.disconnectController();
            }
            throw new CommandLineException("Communication error", e);
        } catch (RuntimeException e) {
            throw new CommandLineException("Failed to execute operation.", e);
        }
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        return true;
    }

    @Override
    public ModelNode buildRequest(CommandContext ctx) throws CommandFormatException {
        return ((DefaultCallbackHandler)ctx.getParsedCommandLine()).toOperationRequest(ctx);
    }

    @Override
    public CommandArgument getArgument(CommandContext ctx, String name) {
        return null;
    }

    @Override
    public boolean hasArgument(CommandContext ctx, String name) {
        return false;
    }

    @Override
    public boolean hasArgument(CommandContext ctx, int index) {
        return false;
    }

    @Override
    public List<CommandArgument> getArguments(CommandContext ctx) {
        return Collections.emptyList();
    }
}
