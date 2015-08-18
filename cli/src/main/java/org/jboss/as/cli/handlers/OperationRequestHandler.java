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
package org.jboss.as.cli.handlers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

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

        ModelNode request = (ModelNode) ctx.get("OP_REQ");
        if(request == null) {
            throw new CommandLineException("Parsed request isn't available.");
        }

        if(ctx.getConfig().isValidateOperationRequests()) {
            ModelNode opDescOutcome = validateRequest(ctx, request);
            if (opDescOutcome != null) { // operation has params that might need to be replaced
                replaceFilePathsWithBytes(request, opDescOutcome);
            }
        }

        try {
            final ModelNode result = client.execute(request);
            if(Util.isSuccess(result)) {
                ctx.printLine(result.toString());
            } else {
                throw new CommandLineException(result.toString());
            }
        } catch(NoSuchElementException e) {
            throw new CommandLineException("ModelNode request is incomplete", e);
        } catch (CancellationException e) {
            throw new CommandLineException("The result couldn't be retrieved (perhaps the task was cancelled", e);
        } catch (IOException e) {
            ctx.disconnectController();
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

    // returns the READ_OPERATION_DESCRIPTION outcome used to validate the request params
    // return null if the operation has no params to validate
    private ModelNode validateRequest(CommandContext ctx, ModelNode request) throws CommandFormatException {

        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            throw new CommandFormatException("No connection to the controller.");
        }

        final Set<String> keys = request.keys();

        if(!keys.contains(Util.OPERATION)) {
            throw new CommandFormatException("Request is missing the operation name.");
        }
        final String operationName = request.get(Util.OPERATION).asString();

        if(!keys.contains(Util.ADDRESS)) {
            throw new CommandFormatException("Request is missing the address part.");
        }
        final ModelNode address = request.get(Util.ADDRESS);

        if(keys.size() == 2) { // no props
            return null;
        }

        final ModelNode opDescrReq = new ModelNode();
        opDescrReq.get(Util.ADDRESS).set(address);
        opDescrReq.get(Util.OPERATION).set(Util.READ_OPERATION_DESCRIPTION);
        opDescrReq.get(Util.NAME).set(operationName);

        final ModelNode outcome;
        try {
            outcome = client.execute(opDescrReq);
        } catch(Exception e) {
            throw new CommandFormatException("Failed to perform " + Util.READ_OPERATION_DESCRIPTION + " to validate the request: " + e.getLocalizedMessage());
        }
        if (!Util.isSuccess(outcome)) {
            throw new CommandFormatException("Failed to get the list of the operation properties: \"" + Util.getFailureDescription(outcome) + '\"');
        }

        if(!outcome.has(Util.RESULT)) {
            throw new CommandFormatException("Failed to perform " + Util.READ_OPERATION_DESCRIPTION + " to validate the request: result is not available.");
        }
        final ModelNode result = outcome.get(Util.RESULT);
        if(!result.hasDefined(Util.REQUEST_PROPERTIES)) {
            if(!(keys.size() == 3 && keys.contains(Util.OPERATION_HEADERS))) {
                throw new CommandFormatException("Operation '" + operationName + "' does not expect any property.");
            }
        } else {
            if(!result.hasDefined(Util.REQUEST_PROPERTIES)) {
                if(!(keys.size() == 3 && keys.contains(Util.OPERATION_HEADERS))) {
                    throw new CommandFormatException("Operation '" + operationName + "' does not expect any property.");
                }
            }
            final ModelNode definedProps = result.get(Util.REQUEST_PROPERTIES);
            int skipped = 0;
            Set<String> missingParameters = listMissingRequiredParameters(definedProps, keys);
            if(!missingParameters.isEmpty()) {
                 throw new CommandFormatException("The following parameters '" + String.join(", ", missingParameters) + "' are required");
            }
            for(String prop : keys) {
                if(skipped < 2 && (prop.equals(Util.ADDRESS) || prop.equals(Util.OPERATION))) {
                    ++skipped;
                    continue;
                }
                if(!definedProps.has(prop)) {
                    if(!Util.OPERATION_HEADERS.equals(prop)) {
                        throw new CommandFormatException("'" + prop + "' is not found among the supported properties: " + definedProps);
                    }
                }
            }
        }
        return outcome;
    }
    private Set<String> listMissingRequiredParameters(ModelNode definedProps, Set<String> keys) {
        return definedProps.asPropertyList().stream()
                .filter((Property prop) -> prop.getValue().hasDefined(Util.REQUIRED) && prop.getValue().get(Util.REQUIRED).asBoolean() && !keys.contains(prop.getName()))
                .map((Property prop) -> prop.getName()).collect(Collectors.toSet());
    }

    // For any request params that are of type BYTES, replace the file path with the bytes from the file
    private void replaceFilePathsWithBytes(ModelNode request, ModelNode opDescOutcome)  throws CommandFormatException {
        ModelNode requestProps = opDescOutcome.get("result", "request-properties");
        for (Property prop : requestProps.asPropertyList()) {
            ModelNode typeDesc = prop.getValue().get("type");
            if (typeDesc.getType() == ModelType.TYPE &&
                    typeDesc.asType() == ModelType.BYTES &&
                    request.hasDefined(prop.getName())) {
                String filePath = request.get(prop.getName()).asString();
                File localFile = new File(filePath);
                if (!localFile.exists()) continue;
                try {
                    request.get(prop.getName()).set(Util.readBytes(localFile));
                } catch (OperationFormatException e) {
                    throw new CommandFormatException(e);
                }
            }
        }
    }
}
