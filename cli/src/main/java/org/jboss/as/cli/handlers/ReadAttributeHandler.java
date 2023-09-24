/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.ModelNodeFormatter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.impl.AttributeNamePathCompleter;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class ReadAttributeHandler extends BaseOperationCommand {

    private final ArgumentWithValue node;
    private final ArgumentWithValue name;
    private final ArgumentWithValue includeDefaults;
    private final ArgumentWithoutValue verbose;
    private final ArgumentWithoutValue resolve;

    public ReadAttributeHandler(CommandContext ctx) {
        super(ctx, "read-attribute", true);

        node = new ArgumentWithValue(this, OperationRequestCompleter.ARG_VALUE_COMPLETER, "--node");

        name = new ArgumentWithValue(this,
                new CommandLineCompleter() {
                    @Override
                    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                        try {
                        final OperationRequestAddress address = getAddress(ctx, true);
                        final ModelNode req = new ModelNode();
                        if(address.isEmpty()) {
                            req.get(Util.ADDRESS).setEmptyList();
                        } else {
                            if(address.endsOnType()) {
                                return -1;
                            }
                            final ModelNode addrNode = req.get(Util.ADDRESS);
                            for(OperationRequestAddress.Node node : address) {
                                addrNode.add(node.getType(), node.getName());
                            }
                        }
                        req.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
                        try {
                            final ModelNode response = ctx.getModelControllerClient().execute(req);
                            if(Util.isSuccess(response)) {
                                if(response.hasDefined(Util.RESULT)) {
                                    final ModelNode result = response.get(Util.RESULT);
                                    if(result.hasDefined(Util.ATTRIBUTES)) {
                                        return AttributeNamePathCompleter.INSTANCE.complete(buffer, candidates, result.get(Util.ATTRIBUTES));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (CommandFormatException e) {
                        return -1;
                    }
                    return -1;
                    }}, 0, "--name");

        includeDefaults = new ArgumentWithValue(this, SimpleTabCompleter.BOOLEAN, "--include-defaults");

        verbose = new ArgumentWithoutValue(this, "--verbose", "-v");

        resolve = new ArgumentWithoutValue(this, "--resolve-expressions") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!super.canAppearNext(ctx)) {
                    return false;
                }
                ModelNode op = new ModelNode();
                op.get("operation").set("read-operation-description");
                op.get("name").set("read-attribute");
                OperationRequestAddress address = getAddress(ctx, true);
                if(address.isEmpty()) {
                    op.get(Util.ADDRESS).setEmptyList();
                } else {
                    final ModelNode addrNode = op.get(Util.ADDRESS);
                    for (OperationRequestAddress.Node node : address) {
                        if (node.getName() == null) {
                            addrNode.add(node.getType(), "*");
                        } else {
                            addrNode.add(node.getType(), node.getName());
                        }
                    }
                }

                ModelNode returnVal = new ModelNode();
                try {
                    returnVal = ctx.getModelControllerClient().execute(op);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if( returnVal.hasDefined("outcome") && returnVal.get("outcome").asString().equals("success")){
                    ModelNode result = returnVal.get("result");
                    if(result.hasDefined("request-properties")){
                        ModelNode properties = result.get("request-properties");
                        if( properties.hasDefined("resolve-expressions")) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    @Override
    protected void recognizeArguments(CommandContext ctx) throws CommandFormatException {
        super.recognizeArguments(ctx);

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        if(resolve.isPresent(parsedCmd)){
            ModelNode op = new ModelNode();
            op.get("operation").set("read-operation-description");
            op.get("name").set("read-attribute");
            OperationRequestAddress address = getAddress(ctx, false);
            if (address.isEmpty()) {
                op.get(Util.ADDRESS).setEmptyList();
            } else {
                final ModelNode addrNode = op.get(Util.ADDRESS);
                for (OperationRequestAddress.Node node : address) {
                    addrNode.add(node.getType(), node.getName());
                }
            }

            ModelNode returnVal = new ModelNode();
            try {
                if (ctx.getModelControllerClient() != null) {
                    returnVal = ctx.getModelControllerClient().execute(op);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (returnVal.hasDefined("outcome") && returnVal.get("outcome").asString().equals("success")) {
                ModelNode result = returnVal.get("result");
                if (result.hasDefined("request-properties")) {
                    ModelNode properties = result.get("request-properties");
                    if (!properties.hasDefined("resolve-expressions")) {
                        throw new OperationFormatException("Resolve Expression argument not available at this location.");
                    }
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.OperationCommand#buildRequest(org.jboss.as.cli.CommandContext)
     */
    @Override
    public ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        final String name = this.name.getValue(parsedCmd);
        if(name == null || name.isEmpty()) {
            throw new CommandFormatException("Required argument " + this.name.getFullName() + " is not specified.");
        }

        final OperationRequestAddress address = getAddress(ctx, false);
        ModelNode req = Util.buildRequest(ctx, address, Util.READ_ATTRIBUTE);
        req.get(Util.NAME).set(name);
        if( resolve.isPresent(parsedCmd)){
            req.get(Util.RESOLVE_EXPRESSIONS).set(true);
        }

        final String includeDefaults = this.includeDefaults.getValue(parsedCmd);
        if(includeDefaults != null && !includeDefaults.isEmpty()) {
            req.get(Util.INCLUDE_DEFAULTS).set(includeDefaults);
        }

        if(verbose.isPresent(parsedCmd)) {
            final ModelNode composite = new ModelNode();
            composite.get(Util.OPERATION).set(Util.COMPOSITE);
            composite.get(Util.ADDRESS).setEmptyList();
            final ModelNode steps = composite.get(Util.STEPS);
            steps.add(req);
            steps.add(Util.buildRequest(ctx, address, Util.READ_RESOURCE_DESCRIPTION));
            req = composite;
        }

        return req;
    }

    @Override
    protected void handleResponse(CommandContext ctx, ModelNode response, boolean composite) throws CommandFormatException {
        if (!Util.isSuccess(response)) {
            throw new CommandFormatException(Util.getFailureDescription(response));
        }
        if(!response.hasDefined(Util.RESULT)) {
            return;
        }

        final ModelNode result = response.get(Util.RESULT);

        if(composite) {
            final SimpleTable table = new SimpleTable(2, ctx.getTerminalWidth());
            final StringBuilder valueBuf = new StringBuilder();
            if(result.hasDefined(Util.STEP_1)) {
                final ModelNode stepOutcome = result.get(Util.STEP_1);
                if(Util.isSuccess(stepOutcome)) {
                    if(stepOutcome.hasDefined(Util.RESULT)) {
                        final ModelNode valueResult = stepOutcome.get(Util.RESULT);
                        final ModelNodeFormatter formatter = ModelNodeFormatter.Factory.forType(valueResult.getType());
                        formatter.format(valueBuf, 0, valueResult);
                    } else {
                        valueBuf.append("n/a");
                    }
                    table.addLine(new String[]{"value", valueBuf.toString()});
                } else {
                    throw new CommandFormatException("Failed to get resource description: " + response);
                }
            }

            if(result.hasDefined(Util.STEP_2)) {
                final ModelNode stepOutcome = result.get(Util.STEP_2);
                if(Util.isSuccess(stepOutcome)) {
                    if(stepOutcome.hasDefined(Util.RESULT)) {
                        final ModelNode descrResult = stepOutcome.get(Util.RESULT);
                        if(descrResult.hasDefined(Util.ATTRIBUTES)) {
                            ModelNode attributes = descrResult.get(Util.ATTRIBUTES);
                            final String name = this.name.getValue(ctx.getParsedCommandLine());
                            if(name == null) {
                                throw new CommandFormatException("Attribute name is not available in handleResponse.");
                            } else if(attributes.hasDefined(name)) {
                                final ModelNode descr = attributes.get(name);
                                for(String prop : descr.keys()) {
                                    table.addLine(new String[]{prop, descr.get(prop).asString()});
                                }
                            } else {
                                throw new CommandFormatException("Attribute description is not available.");
                            }
                        } else {
                            throw new CommandFormatException("The resource doesn't provide attribute descriptions.");
                        }
                    } else {
                        throw new CommandFormatException("Result is not available for read-resource-description request: " + response);
                    }
                } else {
                    throw new CommandFormatException("Failed to get resource description: " + response);
                }
            }
            ctx.printLine(table.toString(true));
        } else {
            final ModelNodeFormatter formatter = ModelNodeFormatter.Factory.forType(result.getType());
            final StringBuilder buf = new StringBuilder();
            formatter.format(buf, 0, result);
            ctx.printLine(buf.toString());
        }
    }

    protected StringBuilder formatResponse(CommandContext ctx, ModelNode opResponse, boolean composite, StringBuilder buf) throws CommandFormatException {
        if(!opResponse.hasDefined(Util.RESULT)) {
            return null;
        }
        final ModelNode result = opResponse.get(Util.RESULT);
        if(composite) {
            final Set<String> keys;
            try {
                keys = result.keys();
            } catch(Exception e) {
                throw new CommandFormatException("Failed to get step results from a composite operation response " + opResponse);
            }
            for(String key : keys) {
                final ModelNode stepResponse = result.get(key);
                buf = formatResponse(ctx, stepResponse, false, buf); // TODO nested composite ops aren't expected for now
            }
        } else {
            final ModelNodeFormatter formatter = ModelNodeFormatter.Factory.forType(result.getType());
            if(buf == null) {
                buf = new StringBuilder();
            }
            formatter.format(buf, 0, result);
        }
        return buf;
    }

    protected OperationRequestAddress getAddress(CommandContext ctx, boolean completion) throws CommandFormatException {
        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final OperationRequestAddress address;
        if (node.isPresent(args)) {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
            CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(address);

            // this is for correct parsing of escaped characters
            String nodePath = args.getSubstitutedLine();
            int nodeArgInd = nodePath.indexOf(" --node=");
            if (nodeArgInd < 0) {
                if (completion) {
                    // Under completion.
                    nodeArgInd = nodePath.indexOf(" --node");
                    if (nodeArgInd >= 0) {
                        return new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
                    }
                }
                throw new CommandFormatException("Couldn't locate ' --node=' in the line: '" + nodePath + "'");
            }

            int nodeArgEndInd = nodeArgInd + 8;
            do {
                nodeArgEndInd = nodePath.indexOf(' ', nodeArgEndInd);
                if(nodeArgEndInd < 0) {
                    nodeArgEndInd = nodePath.length();
                } else if(nodePath.charAt(nodeArgEndInd - 1) == '\\') {
                    ++nodeArgEndInd;
                } else {
                    break;
                }
            } while(nodeArgEndInd < nodePath.length());
            nodePath = nodePath.substring(nodeArgInd + 8, nodeArgEndInd);
            ctx.getCommandLineParser().parse(nodePath, handler);
        } else {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
        }
        return address;
    }
}
