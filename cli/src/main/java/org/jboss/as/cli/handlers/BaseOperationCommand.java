/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.jboss.as.cli.Attachments;

import org.jboss.as.cli.CliEvent;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.OperationCommand;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.HeadersArgumentValueConverter;
import org.jboss.as.cli.impl.RequestParameterArgument;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.HeadersCompleter;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Alexey Loubyansky
 */
public abstract class BaseOperationCommand extends CommandHandlerWithHelp implements OperationCommand, CliEventListener {

    protected List<RequestParameterArgument> params = new ArrayList<RequestParameterArgument>();
    protected OperationRequestAddress requiredAddress;

    private boolean dependsOnProfile;
    private Boolean available;
    private String requiredType;

    protected final ArgumentWithValue headers;

    protected AccessRequirement accessRequirement;

    public BaseOperationCommand(CommandContext ctx, String command, boolean connectionRequired) {
        this(ctx, command, connectionRequired, true);
    }

    public BaseOperationCommand(CommandContext ctx, String command, boolean connectionRequired, boolean needHeaders) {
        super(command, connectionRequired);
        ctx.addEventListener(this);
        if (needHeaders) {
            headers = new ArgumentWithValue(this, HeadersCompleter.INSTANCE, HeadersArgumentValueConverter.INSTANCE, "--headers");
        } else {
            headers = null;
        }
        accessRequirement = setupAccessRequirement(ctx);
    }

    protected AccessRequirement setupAccessRequirement(CommandContext ctx) {
        return AccessRequirement.NONE;
    }

    /**
     * Adds a node path which is required to exist before the command can be used.
     * @param requiredPath  node path which is required to exist before the command can be used.
     */
    protected void addRequiredPath(String requiredPath) {
        checkNotNullParam("requiredPath", requiredPath);
        DefaultOperationRequestAddress requiredAddress = new DefaultOperationRequestAddress();
        CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(requiredAddress);
        try {
            ParserUtil.parseOperationRequest(requiredPath, handler);
        } catch (CommandFormatException e) {
            throw new IllegalArgumentException("Failed to parse nodeType: " + e.getMessage());
        }
        addRequiredPath(requiredAddress);
    }

    /**
     * Adds a node path which is required to exist before the command can be used.
     * @param requiredPath  node path which is required to exist before the command can be used.
     */
    protected void addRequiredPath(OperationRequestAddress requiredPath) {
        checkNotNullParam("requiredPath", requiredPath);
        // there perhaps could be more but for now only one is allowed
        if(requiredAddress != null) {
            throw new IllegalStateException("Only one required address is allowed, atm.");
        }
        requiredAddress = requiredPath;

        final Iterator<Node> iterator = requiredAddress.iterator();
        if(iterator.hasNext()) {
            final String firstType = iterator.next().getType();
            dependsOnProfile = Util.SUBSYSTEM.equals(firstType) || Util.PROFILE.equals(firstType);
        }
        if(requiredAddress.endsOnType()) {
            requiredType = requiredAddress.toParentNode().getType();
        }
    }

    protected boolean isDependsOnProfile() {
        return dependsOnProfile;
    }

    protected OperationRequestAddress getRequiredAddress() {
        return requiredAddress;
    }

    protected String getRequiredType() {
        return requiredType;
    }

    @Override
    public boolean isAvailable(CommandContext ctx) {
        if(!super.isAvailable(ctx)) {
            return false;
        }
        if(requiredAddress == null) {
            return ctx.getConfig().isAccessControl() ? accessRequirement.isSatisfied(ctx) : true;
        }

        if(dependsOnProfile && ctx.isDomainMode()) { // not checking address in all the profiles
            return ctx.getConfig().isAccessControl() ? accessRequirement.isSatisfied(ctx) : true;
        }

        if(available != null) {
            return available.booleanValue();
        }

        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return false;
        }

        // caching the results of an address validation may cause a problem:
        // the address may become valid/invalid during the session
        // the change won't have an effect until the cache is cleared
        // which happens on reconnect/disconnect
        if(requiredType == null) {
            available = isAddressValid(ctx);
        } else {
            final ModelNode request = new ModelNode();
            final ModelNode address = request.get(Util.ADDRESS);
            for(OperationRequestAddress.Node node : requiredAddress) {
                address.add(node.getType(), node.getName());
            }
            request.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            ModelNode result;
            try {
                result = ctx.getModelControllerClient().execute(request);
            } catch (IOException e) {
                return false;
            }
            available = Util.listContains(result, requiredType);
        }

        if(ctx.getConfig().isAccessControl()) {
            available = available && accessRequirement.isSatisfied(ctx);
        }
        return available;
    }

    protected boolean isAddressValid(CommandContext ctx) {
        final ModelNode request = new ModelNode();
        final ModelNode address = request.get(Util.ADDRESS);
        address.setEmptyList();
        request.get(Util.OPERATION).set(Util.VALIDATE_ADDRESS);
        final ModelNode addressValue = request.get(Util.VALUE);
        for(OperationRequestAddress.Node node : requiredAddress) {
            addressValue.add(node.getType(), node.getName());
        }
        final ModelNode response;
        try {
            response = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            return false;
        }
        final ModelNode result = response.get(Util.RESULT);
        if(!result.isDefined()) {
            return false;
        }
        final ModelNode valid = result.get(Util.VALID);
        if(!valid.isDefined()) {
            return false;
        }
        return valid.asBoolean();
    }

    @Override
    public void cliEvent(CliEvent event, CommandContext ctx) {
        if(event == CliEvent.DISCONNECTED) {
            available = null;
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ModelNode request = buildRequest(ctx);
        Attachments attachments = getAttachments(ctx);
        OperationBuilder builder = new OperationBuilder(request, true);
        for (String path : attachments.getAttachedFiles()) {
            builder.addFileAsAttachment(new File(path));
        }
        final ModelControllerClient client = ctx.getModelControllerClient();
        final OperationResponse operationResponse;
        try {
            operationResponse = client.executeOperation(builder.build(), OperationMessageHandler.DISCARD);
        } catch (Exception e) {
            throw new CommandLineException("Failed to perform operation", e);
        }
        try {
            final ModelNode response = operationResponse.getResponseNode();
            if (!Util.isSuccess(response)) {
                throw new CommandLineException(Util.getFailureDescription(response));
            }
            handleResponse(ctx, operationResponse, Util.COMPOSITE.equals(request.get(Util.OPERATION).asString()));
            operationResponse.close();
        } catch (IOException ex) {
            throw new CommandLineException("Failed to perform operation", ex);
        }
    }

    @Override
    public ModelNode buildRequest(CommandContext ctx) throws CommandFormatException {
        recognizeArguments(ctx);
        return buildRequestWOValidation(ctx);
    }

    protected Attachments getAttachments(CommandContext ctx) {
        return Attachments.IMMUTABLE_ATTACHMENTS;
    }

    protected ModelNode buildRequestWOValidation(CommandContext ctx) throws CommandFormatException {
        final ModelNode request = buildRequestWithoutHeaders(ctx);
        addHeaders(ctx, request);
        return request;
    }

    protected abstract ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException;

    protected void addHeaders(CommandContext ctx, ModelNode request) throws CommandFormatException {
        if(headers == null || !headers.isPresent(ctx.getParsedCommandLine())) {
            return;
        }
        final ModelNode headersNode = headers.toModelNode(ctx);
        if (headersNode != null && headersNode.getType() != ModelType.OBJECT) {
            throw new CommandFormatException("--headers option has wrong value '"+headersNode+"'");
        }
        final ModelNode opHeaders = request.get(Util.OPERATION_HEADERS);
        opHeaders.set(headersNode);
    }

    protected void handleResponse(CommandContext ctx, OperationResponse response, boolean composite) throws CommandLineException {
        handleResponse(ctx, response.getResponseNode(), composite);
        handleAttachedFile(ctx, response);
    }

    protected void handleAttachedFile(CommandContext ctx, OperationResponse operationResponse) throws CommandLineException {
    }

    protected void handleResponse(CommandContext ctx, ModelNode response, boolean composite) throws CommandLineException {
        displayResponseHeaders(ctx, response);
    }

    protected void displayResponseHeaders(CommandContext ctx, ModelNode response) {
        if(response.has(Util.RESPONSE_HEADERS)) {
            final ModelNode headers = response.get(Util.RESPONSE_HEADERS);
            final Set<String> keys = headers.keys();
            final SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
            for (String key : keys) {
                table.addLine(new String[] { key + ':', headers.get(key).asString() });
            }
            final StringBuilder buf = new StringBuilder();
            table.append(buf, true);
            ctx.printLine(buf.toString());
        }
    }

    @Override
    public void addArgument(CommandArgument arg) {
        super.addArgument(arg);
        if(arg instanceof RequestParameterArgument) {
            params.add((RequestParameterArgument) arg);
        }
    }

    protected void setParams(CommandContext ctx, ModelNode request) throws CommandFormatException {
        for(RequestParameterArgument arg : params) {
            arg.set(ctx.getParsedCommandLine(), request);
        }
    }
}
