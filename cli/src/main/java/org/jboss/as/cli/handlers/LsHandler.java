/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jboss.as.cli.CommandArgument;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.ArgumentWithoutValue;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestAddress.Node;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.as.cli.util.SimpleTable;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logging.Logger;

/**
 *
 * @author Alexey Loubyansky
 */
public class LsHandler extends BaseOperationCommand {

    private final ArgumentWithValue nodePath;
    private final ArgumentWithoutValue l;
    private final ArgumentWithoutValue resolve;
    private final Logger log = Logger.getLogger(LsHandler.class);

    public LsHandler(CommandContext ctx) {
        this(ctx, "ls");
    }

    public LsHandler(CommandContext ctx, String command) {
        super(ctx, command, true);
        l = new ArgumentWithoutValue(this, "-l");
        nodePath = new ArgumentWithValue(this, OperationRequestCompleter.ARG_VALUE_COMPLETER, 0, "--node-path");
        resolve = new ArgumentWithoutValue(this, "--resolve-expressions") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                if(!super.canAppearNext(ctx)) {
                    return false;
                }
                ModelNode op = new ModelNode();
                op.get("operation").set("read-operation-description");
                op.get("name").set("read-attribute");
                OperationRequestAddress address = getOperationRequestAddress(ctx);
                op = getAddressNode(ctx, address, op);

                ModelNode returnVal = new ModelNode();
                try {
                    returnVal = ctx.getModelControllerClient().execute(op);
                } catch (IOException e) {
                    throw new CommandFormatException("Failed to read resource: "
                            + e.getLocalizedMessage(), e);
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
        if(resolve.isPresent(parsedCmd)) {
            ModelNode op = new ModelNode();
            op.get("operation").set("read-operation-description");
            op.get("name").set("read-attribute");
            OperationRequestAddress address = getOperationRequestAddress(ctx);
            op = getAddressNode(ctx, address, op);

            ModelNode returnVal = new ModelNode();
            try {
                if (ctx.getModelControllerClient() != null) {
                    returnVal = ctx.getModelControllerClient().execute(op);
                }
            } catch (IOException e) {
                throw new CommandFormatException("Failed to read resource: "
                        + e.getLocalizedMessage(), e);
            }

            if (returnVal.hasDefined("outcome") && returnVal.get("outcome").asString().equals("success")) {
                ModelNode result = returnVal.get("result");
                if (result.hasDefined("request-properties")) {
                    ModelNode properties = result.get("request-properties");
                    if (!properties.hasDefined("resolve-expressions") && resolve.isPresent(parsedCmd)) {
                        throw new OperationFormatException("Resolve Expression argument not available at this location.");
                    }
                }
            }
        }
    }

    @Override
    protected void handleResponse(CommandContext ctx, ModelNode outcome, boolean composite) throws CommandLineException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        if(!composite) {
            final List<ModelNode> nodeList = outcome.get(Util.RESULT).asList();
            if(!nodeList.isEmpty()) {
                final List<String> list = new ArrayList<String>(nodeList.size());
                for(ModelNode node : nodeList) {
                    list.add(node.asString());
                }
                printList(ctx, list, l.isPresent(parsedCmd));
            }
            return;
        }

        List<String> additionalProps = Collections.emptyList();
        if (l.isPresent(parsedCmd)) {
            final Set<String> argNames = parsedCmd.getPropertyNames();
            final boolean resolvePresent = resolve.isPresent(parsedCmd);
            final int recognizedArgs = 1 + (resolvePresent ? 1 : 0);
            if (argNames.size() > recognizedArgs) {
                additionalProps = new ArrayList<String>(argNames.size() - recognizedArgs);
                int i = 0;
                for (String arg : argNames) {
                    if (arg.equals(l.getFullName()) || resolvePresent && arg.equals(resolve.getFullName())) {
                        continue;
                    }
                    final String prop;
                    if (arg.length() > 1 && arg.charAt(0) == '-') {
                        if (arg.charAt(1) == '-') {
                            prop = arg.substring(2);
                        } else {
                            prop = arg.substring(1);
                        }
                    } else {
                        prop = arg;
                    }
                    additionalProps.add(prop);
                }
            }
        }

        ModelNode resultNode = outcome.get(Util.RESULT);

        ModelNode attrDescriptions = null;
        ModelNode childDescriptions = null;
        if (resultNode.hasDefined(Util.STEP_3)) {
            final ModelNode stepOutcome = resultNode.get(Util.STEP_3);
            if (Util.isSuccess(stepOutcome)) {
                if (stepOutcome.hasDefined(Util.RESULT)) {
                    final ModelNode descrResult = stepOutcome.get(Util.RESULT);
                    if (descrResult.hasDefined(Util.ATTRIBUTES)) {
                        attrDescriptions = descrResult.get(Util.ATTRIBUTES);
                    }
                    if (descrResult.hasDefined(Util.CHILDREN)) {
                        childDescriptions = descrResult.get(Util.CHILDREN);
                    }
                } else {
                    throw new CommandFormatException("Result is not available for read-resource-description request: " + outcome);
                }
            } else {
                throw new CommandFormatException("Failed to get resource description: " + outcome);
            }
        }

        List<String> names = null;
        List<String> typeNames = null;
        if (resultNode.hasDefined(Util.STEP_1)) {
            ModelNode typesOutcome = resultNode.get(Util.STEP_1);
            if (Util.isSuccess(typesOutcome)) {
                if (typesOutcome.hasDefined(Util.RESULT)) {
                    final ModelNode resourceResult = typesOutcome.get(Util.RESULT);
                    final List<ModelNode> types = resourceResult.asList();
                    if (!types.isEmpty()) {
                        typeNames = new ArrayList<String>();
                        for (ModelNode type : types) {
                            typeNames.add(type.asString());
                        }
                        if (childDescriptions == null && attrDescriptions == null) {
                            names = typeNames;
                        }
                    }
                } else {
                    throw new CommandFormatException("Result is not available for read-children-types request: " + outcome);
                }
            } else {
                throw new CommandFormatException("Failed to fetch type names: " + outcome);
            }
        } else {
            throw new CommandFormatException("The result for children type names is not available: " + outcome);
        }

        if (resultNode.hasDefined(Util.STEP_2)) {
            ModelNode resourceOutcome = resultNode.get(Util.STEP_2);
            if (Util.isSuccess(resourceOutcome)) {
                if (resourceOutcome.hasDefined(Util.RESULT)) {
                    final ModelNode resourceResult = resourceOutcome.get(Util.RESULT);
                    final List<Property> props = resourceResult.asPropertyList();
                    if (!props.isEmpty()) {
                        final SimpleTable attrTable;
                        if (attrDescriptions == null) {
                            attrTable = null;
                        } else {
                            if (!additionalProps.isEmpty()) {
                                String[] headers = new String[3 + additionalProps.size()];
                                headers[0] = "ATTRIBUTE";
                                headers[1] = "VALUE";
                                headers[2] = "TYPE";
                                int i = 3;
                                for (String additional : additionalProps) {
                                    headers[i++] = additional.toUpperCase(Locale.ENGLISH);
                                }
                                attrTable = new SimpleTable(headers, ctx.getTerminalWidth());
                            } else {
                                attrTable = new SimpleTable(new String[] { "ATTRIBUTE", "VALUE", "TYPE" }, ctx.getTerminalWidth());
                            }
                        }
                        SimpleTable childrenTable = childDescriptions == null ? null : new SimpleTable(new String[] { "CHILD", "MIN-OCCURS", "MAX-OCCURS" }, ctx.getTerminalWidth());
                        if (typeNames == null && attrTable == null && childrenTable == null) {
                            typeNames = new ArrayList<String>();
                            names = typeNames;
                        }

                        for (Property prop : props) {
                            final StringBuilder buf = new StringBuilder();
                            if (typeNames == null || !typeNames.contains(prop.getName())) {
                                if (attrDescriptions == null) {
                                    buf.append(prop.getName());
                                    buf.append('=');
                                    buf.append(prop.getValue().asString());
                                    // TODO the value should be formatted nicer but the current
                                    // formatter uses new lines for complex value which doesn't work here
                                    // final ModelNode value = prop.getValue();
                                    // ModelNodeFormatter.Factory.forType(value.getType()).format(buf, 0, value);
                                    typeNames.add(buf.toString());
                                    buf.setLength(0);
                                } else {
                                    final String[] line = new String[attrTable.columnsTotal()];
                                    line[0] = prop.getName();
                                    line[1] = prop.getValue().asString();
                                    if (attrDescriptions.hasDefined(prop.getName())) {
                                        final ModelNode attrDescr = attrDescriptions.get(prop.getName());
                                        line[2] = getAsString(attrDescr, Util.TYPE);
                                        if (!additionalProps.isEmpty()) {
                                            int i = 3;
                                            for (String additional : additionalProps) {
                                                line[i++] = getAsString(attrDescr, additional);
                                            }
                                        }
                                    } else {
                                        for (int i = 2; i < line.length; ++i) {
                                            line[i] = "n/a";
                                        }
                                    }
                                    attrTable.addLine(line);
                                }
                            } else if (childDescriptions != null) {
                                if (childDescriptions.hasDefined(prop.getName())) {
                                    final ModelNode childDescr = childDescriptions.get(prop.getName());
                                    final Integer maxOccurs = getAsInteger(childDescr, Util.MAX_OCCURS);
                                    childrenTable.addLine(new String[] {prop.getName(), getAsString(childDescr, Util.MIN_OCCURS), maxOccurs == null ? "n/a"
                                                            : (maxOccurs == Integer.MAX_VALUE ? "unbounded" : maxOccurs.toString()) });
                                } else {
                                    childrenTable.addLine(new String[] {prop.getName(), "n/a", "n/a" });
                                }
                            }
                        }

                        StringBuilder buf = null;
                        if (attrTable != null && !attrTable.isEmpty()) {
                            buf = new StringBuilder();
                            attrTable.append(buf, true);
                        }
                        if (childrenTable != null
                                && !childrenTable.isEmpty()) {
                            if (buf == null) {
                                buf = new StringBuilder();
                            } else {
                                buf.append("\n\n");
                            }
                            childrenTable.append(buf, true);
                        }
                        if (buf != null) {
                            ctx.printLine(buf.toString());
                        }
                    }
                } else {
                    throw new CommandFormatException("Result is not available for read-resource request: " + outcome);
                }
            } else {
                throw new CommandFormatException("Failed to fetch attributes: " + outcome);
            }
        } else {
            throw new CommandFormatException("The result for attributes is not available: " + outcome);
        }

        if(names != null) {
            printList(ctx, names, l.isPresent(parsedCmd));
        }
    }

    @Override
    protected ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws CommandFormatException {

        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();

        final OperationRequestAddress address = getOperationRequestAddress(ctx);
        if(address.endsOnType()) {
            final String type = address.getNodeType();
            address.toParentNode();
            final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder(address);
            try {
                builder.setOperationName(Util.READ_CHILDREN_NAMES);
                builder.addProperty(Util.CHILD_TYPE, type);
                return builder.buildRequest();
            } catch (OperationFormatException e) {
                throw new IllegalStateException("Failed to build operation", e);
            }
        }

        final ModelNode composite = new ModelNode();
        composite.get(Util.OPERATION).set(Util.COMPOSITE);
        composite.get(Util.ADDRESS).setEmptyList();
        final ModelNode steps = composite.get(Util.STEPS);

        {
            ModelNode typesRequest = new ModelNode();
            typesRequest.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);
            typesRequest = getAddressNode(ctx, address, typesRequest);
            steps.add(typesRequest);
        }

        {
            ModelNode resourceRequest = new ModelNode();
            resourceRequest.get(Util.OPERATION).set(Util.READ_RESOURCE);
            resourceRequest = getAddressNode(ctx, address, resourceRequest);
            resourceRequest.get(Util.INCLUDE_RUNTIME).set(Util.TRUE);
            if( resolve.isPresent(parsedCmd) ){
                resourceRequest.get(Util.RESOLVE_EXPRESSIONS).set(Util.TRUE);
            }
            steps.add(resourceRequest);
        }

        if (l.isPresent(parsedCmd)) {
            steps.add(Util.buildRequest(ctx, address, Util.READ_RESOURCE_DESCRIPTION));
        }
        return composite;
    }

    @Override
    protected Map<String, CommandArgument> getArgumentsMap(CommandContext ctx) {
        Map<String, CommandArgument> ret = new HashMap<>(super.getArgumentsMap(ctx));
        try {
            Map<String, CommandArgument> dyns = getDynamicOptions(ctx);
            ret.putAll(dyns);
        } catch (CommandFormatException ex) {
            logLsException(ex);

        }
        return ret;
    }

    @Override
    public Collection<CommandArgument> getArguments(CommandContext ctx) {
        List<CommandArgument> args = new ArrayList<>(super.getArguments(ctx));
        try {
            args.addAll(getDynamicOptions(ctx).values());
        } catch (CommandFormatException ex) {
            logLsException(ex);
        }
        return args;
    }

    private void logLsException(CommandFormatException ex) {
        // XXX OK will not break CLI
        log.trace("Exception while retreiving ls "
                + "description properties", ex);
    }

    private Map<String, CommandArgument> getDynamicOptions(CommandContext ctx) throws CommandFormatException {
        if (ctx.getModelControllerClient() == null) {
            return Collections.emptyMap();
        }
        final OperationRequestAddress address = getOperationRequestAddress(ctx);
        if (address.endsOnType()) {
            return Collections.emptyMap();
        }
        final ModelNode req = new ModelNode();
        if (address.isEmpty()) {
            req.get(Util.ADDRESS).setEmptyList();
        } else {
            final ModelNode addrNode = req.get(Util.ADDRESS);
            for (OperationRequestAddress.Node node : address) {
                addrNode.add(node.getType(), node.getName());
            }
        }
        req.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
        Map<String, CommandArgument> options = Collections.emptyMap();
        try {
            final ModelNode response = ctx.getModelControllerClient().execute(req);
            if (Util.isSuccess(response)) {
                if (response.hasDefined(Util.RESULT)) {
                    final ModelNode result = response.get(Util.RESULT);
                    if (result.hasDefined(Util.ATTRIBUTES)) {
                        options = new TreeMap<>();
                        ModelNode attributes = result.get(Util.ATTRIBUTES);
                        for (String key : attributes.keys()) {
                            ModelNode attribute = attributes.get(key);
                            for (String k : attribute.keys()) {
                                ArgumentWithoutValue wv = new ArgumentWithoutValue(new CommandHandlerWithArguments() {
                                    @Override
                                    public boolean isAvailable(CommandContext ctx) {
                                        return LsHandler.this.isAvailable(ctx);
                                    }

                                    @Override
                                    public boolean isBatchMode(CommandContext ctx) {
                                        return LsHandler.this.isBatchMode(ctx);
                                    }

                                    @Override
                                    public void handle(CommandContext ctx) throws CommandLineException {
                                        LsHandler.this.handle(ctx);
                                    }

                                    @Override
                                    public void addArgument(CommandArgument arg) {
                                        // Noop.
                                    }
                                }, "--" + k);
                                wv.addRequiredPreceding(l);
                                options.put("--" + k, wv);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return options;
    }

    protected String getAsString(final ModelNode attrDescr, String name) {
        if(attrDescr == null) {
            return "n/a";
        }
        return attrDescr.has(name) ? attrDescr.get(name).asString() : "n/a";
    }

    protected Integer getAsInteger(final ModelNode attrDescr, String name) {
        if(attrDescr == null) {
            return null;
        }
        return attrDescr.has(name) ? attrDescr.get(name).asInt() : null;
    }

    private OperationRequestAddress getOperationRequestAddress(CommandContext ctx) throws CommandFormatException{
        final ParsedCommandLine parsedCmd = ctx.getParsedCommandLine();
        String nodePathString = nodePath.getValue(parsedCmd);
        final OperationRequestAddress address;
        if (nodePathString != null) {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());

            // this is for correct parsing of escaped characters
            nodePathString = ctx.getArgumentsString(); // this method could return null
            // it would be better to call parsedCmd.getOriginalLine() instead
            // but this method is useful to handle command lines with line breaks
            if(nodePathString == null) {
                nodePathString = parsedCmd.getOriginalLine();
                int cmdNameLength = parsedCmd.getOperationName().length();
                if (nodePathString.length() == cmdNameLength) {
                    return address;
                } else {
                    nodePathString = nodePathString.substring(cmdNameLength + 1);
                }
            }

            nodePathString = getNodePath(nodePathString);

            final CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(address);
            ctx.getCommandLineParser().parse(nodePathString, handler);
        } else {
            address = new DefaultOperationRequestAddress(ctx.getCurrentNodePath());
        }

        return address;
    }

    protected static String getNodePath(String nodePathString) {
        int i = 0;
        while(i < nodePathString.length()) {
            i = nodePathString.indexOf('-', i);
            if(i < 0) {
                break;
            }
            final StringBuilder buf;
            if(i > 0) {
                if(!Character.isWhitespace(nodePathString.charAt(i - 1))) {
                    ++i;
                    continue;
                }
                buf = new StringBuilder(nodePathString.length());
                buf.append(nodePathString.substring(0, i - 1));
            } else {
                buf = new StringBuilder(nodePathString.length());
            }

            int end = i + 1;
            while(end < nodePathString.length()) {
                if(Character.isWhitespace(nodePathString.charAt(end))) {
                    break;
                }
                ++end;
            }
            if(end < nodePathString.length()) {
                buf.append(nodePathString.substring(end));
            }
            nodePathString = buf.toString();
        }
        return nodePathString.trim();
    }

    private ModelNode getAddressNode(CommandContext ctx, OperationRequestAddress address, ModelNode op) throws CommandFormatException{
        ModelNode addressNode = op.get(Util.ADDRESS);

        if (address.isEmpty()) {
            addressNode.setEmptyList();
        } else {
            Iterator<Node> iterator = address.iterator();
            while (iterator.hasNext()) {
                OperationRequestAddress.Node node = iterator.next();
                if (node.getName() != null) {
                    if (node.getName().equals("*")) {
                        throw new CommandFormatException("* is not supported in node path argument");
                    }
                    addressNode.add(node.getType(), node.getName());
                } else if (iterator.hasNext()) {
                    throw new OperationFormatException(
                            "Expected a node name for type '"
                                    + node.getType()
                                    + "' in path '"
                                    + ctx.getNodePathFormatter().format(
                                    address) + "'");
                }
            }
        }

        return op;
    }
}
