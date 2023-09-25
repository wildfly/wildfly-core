/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandHandler;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.CommandRegistry;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.impl.DefaultCompleter;
import org.jboss.as.cli.impl.DefaultCompleter.CandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 *
 * @author Alexey Loubyansky
 */
public class CommandCommandHandler extends CommandHandlerWithHelp {

    private final ArgumentWithValue action = new ArgumentWithValue(this, new SimpleTabCompleter(new String[]{"add", "list", "remove"}), 0, "--action");
    private final ArgumentWithValue nodePath;
    private final ArgumentWithValue nodeChild;
    private final ArgumentWithValue idProperty;
    private final ArgumentWithValue commandName;

    private final CommandRegistry cmdRegistry;

    private DefaultCallbackHandler callback;

    public CommandCommandHandler(CommandRegistry cmdRegistry) {
        super("command", true);
        this.cmdRegistry = cmdRegistry;

        action.addCantAppearAfter(helpArg);

        nodePath = new ArgumentWithValue(this, new CommandLineCompleter(){
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                int offset = 0;
                int result = OperationRequestCompleter.ARG_VALUE_COMPLETER.complete(ctx, buffer, cursor + offset, candidates) - offset;
                return result;
            }}, "--node-type") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                String nChild = nodeChild.getValue(ctx.getParsedCommandLine(), false);
                return "add".equals(action.getValue(ctx.getParsedCommandLine())) && super.canAppearNext(ctx) && nChild == null;
            }
        };

        nodeChild = new ArgumentWithValue(this, new CommandLineCompleter() {
            @Override
            public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
                int offset = 0;
                int result = OperationRequestCompleter.ARG_VALUE_COMPLETER.complete(ctx, buffer, cursor + offset, candidates) - offset;
                return result;
            }
        }, "--node-child") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                String ntype = nodePath.getValue(ctx.getParsedCommandLine(), false);
                return "add".equals(action.getValue(ctx.getParsedCommandLine())) && super.canAppearNext(ctx) && ntype == null;
            }
        };
        idProperty = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){
            @Override
            public List<String> getAllCandidates(CommandContext ctx) {
                List<Property> props;
                try {
                    props = getNodeProperties(ctx);
                } catch (CommandFormatException e) {
                    return Collections.emptyList();
                }

                final List<String> candidates = new ArrayList<String>();
                for(Property prop : props) {
                    final ModelNode value = prop.getValue();
                    if(value.has("access-type") && "read-only".equals(value.get("access-type").asString())) {
                        candidates.add(prop.getName());
                    }
                }
                return candidates;
            }}), "--property-id");
        idProperty.addRequiredPreceding(nodePath);

        commandName = new ArgumentWithValue(this, new DefaultCompleter(new CandidatesProvider(){

            private final DefaultCallbackHandler callback = new DefaultCallbackHandler();

            @Override
            public List<String> getAllCandidates(CommandContext ctx) {

                final String actionName = action.getValue(ctx.getParsedCommandLine());
                if(actionName == null) {
                    return Collections.emptyList();
                }

                if (actionName.equals("add")) {
                    boolean isChildPath = false;
                    String thePath = nodePath.getValue(ctx.getParsedCommandLine());
                   if (thePath == null) {
                       thePath = nodeChild.getValue(ctx.getParsedCommandLine());
                       isChildPath = thePath != null;
                    }
                    if (thePath == null) {
                        return Collections.emptyList();
                    }
                   callback.reset();
                   try {
                       ParserUtil.parseOperationRequest(thePath, callback);
                   } catch (CommandFormatException e) {
                       return Collections.emptyList();
                   }

                    OperationRequestAddress typeAddress = callback.getAddress();
                    if (!typeAddress.endsOnType()) {
                        if (!isChildPath) {
                            return Collections.emptyList();
                        }
                    }
                    if (typeAddress.endsOnType()) {
                        return Collections.singletonList(typeAddress.getNodeType());
                    } else {
                        return Collections.singletonList(typeAddress.getNodeName());
                    }

               }

                if (actionName.equals("remove")) {
                    return getExistingCommands();
                }
                return Collections.emptyList();
            }}), "--command-name") {
            @Override
            public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                ParsedCommandLine args = ctx.getParsedCommandLine();
                if(isPresent(args)) {
                    return false;
                }
                final String actionStr = action.getValue(args);
                if(actionStr == null) {
                    return false;
                }
                if("add".equals(actionStr)) {
                    return nodePath.isValueComplete(args) || nodeChild.isValueComplete(args);
                }
                if("remove".equals(actionStr)) {
                    return true;
                }
                return false;
            }
        };
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.handlers.CommandHandlerWithHelp#doHandle(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected void doHandle(CommandContext ctx) throws CommandLineException {

        final ParsedCommandLine args = ctx.getParsedCommandLine();
        final String action = this.action.getValue(args);
        if(action == null) {
            throw new CommandFormatException("Command is missing.");
        }

        if(action.equals("list")) {
            ctx.printColumns(getExistingCommands());
            return;
        }

        if(action.equals("add")) {
            final String nodePath = this.nodePath.getValue(args, false);
            final String nodeChild = this.nodeChild.getValue(args, false);
            if (nodePath == null && nodeChild == null) {
                throw new CommandFormatException(this.nodePath.getFullName() + " or " + this.nodeChild.getFullName() + " must be defined");
            }
            if (nodePath != null && nodeChild != null) {
                throw new CommandFormatException("Only one of " + this.nodePath.getFullName() + " or " + this.nodeChild.getFullName() + " can be defined");
            }
            boolean isChildPath = nodeChild != null;
            String path = isChildPath ? nodeChild : nodePath;
            final String propName = this.idProperty.getValue(args, false);
            final String cmdName = this.commandName.getValue(args, true);
            validateInput(ctx, path, propName, isChildPath);

            if(cmdRegistry.getCommandHandler(cmdName) != null) {
                throw new CommandFormatException("Command '" + cmdName + "' already registered.");
            }
            cmdRegistry.registerHandler(new GenericTypeOperationHandler(cmdName, ctx, path, propName, isChildPath), cmdName);
            return;
        }

        if(action.equals("remove")) {
            final String cmdName = this.commandName.getValue(args, true);
            CommandHandler handler = cmdRegistry.getCommandHandler(cmdName);
            if(!(handler instanceof GenericTypeOperationHandler)) {
                throw new CommandFormatException("Command '" + cmdName + "' is not a generic type command.");
            }
            cmdRegistry.remove(cmdName);
            return;
        }

        throw new CommandFormatException("Unexpected action: " + action);
    }

    protected List<String> getExistingCommands() {
        final List<String> commands = new ArrayList<String>();
        for(String cmd : cmdRegistry.getTabCompletionCommands()) {
            if(cmdRegistry.getCommandHandler(cmd) instanceof GenericTypeOperationHandler) {
                commands.add(cmd);
            }
        }
        return commands;
    }

    protected List<Property> getNodeProperties(CommandContext ctx) throws CommandFormatException {
        final ModelNode request = initRequest(ctx);
        request.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
        ModelNode result;
        try {
            result = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        if(!result.hasDefined(Util.RESULT)) {
            return Collections.emptyList();
        }
        result = result.get(Util.RESULT);
        if(!result.hasDefined(Util.ATTRIBUTES)) {
            return Collections.emptyList();
        }
        return result.get(Util.ATTRIBUTES).asPropertyList();
    }

    protected ModelNode initRequest(CommandContext ctx) throws CommandFormatException {
        ModelNode request = new ModelNode();
        ModelNode address = request.get(Util.ADDRESS);

        final String type = nodePath.getValue(ctx.getParsedCommandLine());
        if(callback == null) {
            callback = new DefaultCallbackHandler();
        } else {
            callback.reset();
        }
        ParserUtil.parseOperationRequest(type, callback);

        OperationRequestAddress typeAddress = callback.getAddress();
        if(!typeAddress.endsOnType()) {
            return null;
        }

        final String typeName = typeAddress.toParentNode().getType();
        for(OperationRequestAddress.Node node : typeAddress) {
            address.add(node.getType(), node.getName());
        }
        address.add(typeName, "?");
        return request;
    }

    protected void validateInput(CommandContext ctx, String typePath, String propertyName, boolean isChildType) throws CommandFormatException {

        ModelNode request = new ModelNode();
        ModelNode address = request.get(Util.ADDRESS);

        if(callback == null) {
            callback = new DefaultCallbackHandler();
        } else {
            callback.reset();
        }

        try {
            ParserUtil.parseOperationRequest(typePath, callback);
        } catch (CommandFormatException e) {
            throw new CommandFormatException("Failed to validate input: " + e.getLocalizedMessage());
        }

        OperationRequestAddress typeAddress = callback.getAddress();
        if (!typeAddress.endsOnType() && !isChildType) {
            throw new CommandFormatException("Node path '" + typePath + "' doesn't appear to end on a type.");
        }

        if (typeAddress.endsOnType() && isChildType) {
            throw new CommandFormatException("Child path '" + typePath + "' appears to end on a type.");
        }

        ModelNode result;
        if (isChildType) {
            for (OperationRequestAddress.Node node : typeAddress) {
                address.add(node.getType(), node.getName());
            }
            request.get(Util.OPERATION).set(Util.READ_RESOURCE);
        } else {
            final String typeName = typeAddress.toParentNode().getType();
            for (OperationRequestAddress.Node node : typeAddress) {
                address.add(node.getType(), node.getName());
            }

            request.get(Util.OPERATION).set(Util.READ_CHILDREN_TYPES);

            try {
                result = ctx.getModelControllerClient().execute(request);
            } catch (IOException e) {
                throw new CommandFormatException("Failed to validate input: " + e.getLocalizedMessage());
            }
            if (!result.hasDefined(Util.RESULT)) {
                throw new CommandFormatException("Failed to validate input: operation response doesn't contain result info.");
            }

            boolean pathValid = false;
            for (ModelNode typeNode : result.get(Util.RESULT).asList()) {
                if (typeNode.asString().equals(typeName)) {
                    pathValid = true;
                    break;
                }
            }
            if (!pathValid) {
                throw new CommandFormatException("Type '" + typeName + "' not found among child types of '" + ctx.getNodePathFormatter().format(typeAddress) + "'");
            }
            address.add(typeName, "?");
            request.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
        }

        try {
            result = ctx.getModelControllerClient().execute(request);
        } catch (IOException e) {
            throw new CommandFormatException(e.getLocalizedMessage());
        }

        if (isChildType) {
            if (!Util.isSuccess(result)) {
                throw new CommandFormatException("Failure retrieving resource " + typePath + ". Check that the resource already exists.");
            }
        } else {
            if (!result.hasDefined(Util.RESULT)) {
                throw new CommandFormatException("Failed to validate input: operation response doesn't contain result info.");
            }
            result = result.get(Util.RESULT);
            if (!result.hasDefined("attributes")) {
                throw new CommandFormatException("Failed to validate input: description of attributes is missing for " + typePath);
            }

            if (propertyName != null) {
                for (Property prop : result.get(Util.ATTRIBUTES).asPropertyList()) {
                    if (prop.getName().equals(propertyName)) {
                        ModelNode value = prop.getValue();
                        if (value.has(Util.ACCESS_TYPE) && Util.READ_ONLY.equals(value.get(Util.ACCESS_TYPE).asString())) {
                            return;
                        }
                        throw new CommandFormatException("Property " + propertyName + " is not read-only.");
                    }
                }
            } else {
                return;
            }
            throw new CommandFormatException("Property '" + propertyName + "' wasn't found among the properties of " + typePath);
        }
    }
}
