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
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.DefaultFilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.handlers.WindowsFilenameTabCompleter;
import org.jboss.as.cli.impl.AttributeNamePathCompleter;
import org.jboss.as.cli.impl.DeploymentItemCompleter;
import org.jboss.as.cli.impl.ValueTypeCompleter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestHeader;
import org.jboss.as.cli.operation.ParsedCommandLine;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultOperationCandidatesProvider implements OperationCandidatesProvider {

    private static final SimpleTabCompleter NO_CANDIDATES_COMPLETER = new SimpleTabCompleter(new String[]{});

    private static final CommandLineCompleter BOOLEAN_HEADER_COMPLETER = new CommandLineCompleter(){

        private final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();

        @Override
        public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
            try {
                ParserUtil.parseHeaders(buffer, parsedOp);
            } catch (CommandFormatException e) {
                //e.printStackTrace();
                return -1;
            }
            if(parsedOp.endsOnSeparator()) {
                candidates.add(Util.FALSE);
                candidates.add(Util.TRUE);
                return buffer.length();
            }
            if(parsedOp.getLastHeader() == null) {
                candidates.add("=");
                return buffer.length();
            }
            int result = SimpleTabCompleter.BOOLEAN.complete(ctx, buffer.substring(parsedOp.getLastChunkIndex()), cursor, candidates);
            // Special case when the value is already complete, add a separator.
            if (candidates.size() == 1) {
                if (candidates.get(0).equals(buffer.substring(parsedOp.getLastChunkIndex()))) {
                    candidates.clear();
                    candidates.add(buffer.substring(parsedOp.getLastChunkIndex()) + ";");
                }
            }// No value...
            if(result < 0) {
                return result;
            }
            return parsedOp.getLastChunkIndex() + result;
        }};

    private static final CommandLineCompleter INT_HEADER_COMPLETER = new CommandLineCompleter(){

        private final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();

        @Override
        public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
            try {
                ParserUtil.parseHeaders(buffer, parsedOp);
            } catch (CommandFormatException e) {
                //e.printStackTrace();
                return -1;
            }
            if(parsedOp.endsOnSeparator()) {
                return buffer.length();
            }
            if(parsedOp.getLastHeader() == null) {
                candidates.add("=");
                return buffer.length();
            }
            return buffer.length();
        }};

    private static final Map<String, OperationRequestHeader> HEADERS;
    static {
        HEADERS = new HashMap<String, OperationRequestHeader>();
        HEADERS.put(RolloutPlanRequestHeader.INSTANCE.getName(), RolloutPlanRequestHeader.INSTANCE);

        addBooleanHeader(Util.ALLOW_RESOURCE_SERVICE_RESTART);
        addBooleanHeader(Util.ROLLBACK_ON_RUNTIME_FAILURE);
        addIntHeader(Util.BLOCKING_TIMEOUT);
    }

    private static void addBooleanHeader(final String name) {
        OperationRequestHeader header = new OperationRequestHeader(){
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CommandLineCompleter getCompleter() {
                return BOOLEAN_HEADER_COMPLETER;
            }};
        HEADERS.put(header.getName(), header);
    }

    private static void addIntHeader(final String name) {
        OperationRequestHeader header = new OperationRequestHeader(){
            @Override
            public String getName() {
                return name;
            }

            @Override
            public CommandLineCompleter getCompleter() {
                return INT_HEADER_COMPLETER;
            }};
        HEADERS.put(header.getName(), header);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CandidatesProvider#getNodeNames(org.jboss.as.cli.Prefix)
     */
    @Override
    public List<String> getNodeNames(CommandContext ctx, OperationRequestAddress prefix) {

        ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return Collections.emptyList();
        }

        if(prefix.isEmpty()) {
            throw new IllegalArgumentException("The prefix must end on a type but it's empty.");
        }

        if(!prefix.endsOnType()) {
            throw new IllegalArgumentException("The prefix doesn't end on a type.");
        }

        final ModelNode request;
        DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder(prefix);
        try {
            builder.setOperationName(Util.READ_CHILDREN_NAMES);
            builder.addProperty(Util.CHILD_TYPE, prefix.getNodeType());
            builder.addProperty(Util.INCLUDE_SINGLETONS, "true");
            request = builder.buildRequest();
        } catch (OperationFormatException e1) {
            throw new IllegalStateException("Failed to build operation", e1);
        }

        List<String> result;
        try {
            ModelNode outcome = client.execute(request);
            if (!Util.isSuccess(outcome)) {
                // TODO logging... exception?
                result = Collections.emptyList();
            } else {
                result = Util.getList(outcome);
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CandidatesProvider#getNodeTypes(org.jboss.as.cli.Prefix)
     */
    @Override
    public List<String> getNodeTypes(CommandContext ctx, OperationRequestAddress prefix) {
        return Util.getNodeTypes(ctx.getModelControllerClient(), prefix);
    }

    @Override
    public List<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix) {
        return Util.getOperationNames(ctx, prefix);
    }

    @Override
    public List<CommandArgument> getProperties(CommandContext ctx, String operationName, OperationRequestAddress address) {

        final ModelControllerClient client = ctx.getModelControllerClient();
        if(client == null) {
            return Collections.emptyList();
        }

/*        if(address.endsOnType()) {
            throw new IllegalArgumentException("The prefix isn't expected to end on a type.");
        }
*/
        final ModelNode request;
        final DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder(address);
        try {
            builder.setOperationName(Util.READ_OPERATION_DESCRIPTION);
            builder.addProperty(Util.NAME, operationName);
            request = builder.buildRequest();
        } catch (OperationFormatException e1) {
            throw new IllegalStateException("Failed to build operation", e1);
        }

        final Map<String,CommandLineCompleterFactory> globalOpProps = globalOpPropCompleters.get(operationName);

        List<CommandArgument> result;
        try {
            ModelNode outcome = client.execute(request);
            if (!Util.isSuccess(outcome)) {
                result = Collections.emptyList();
            } else {
                final ModelNode resultNode = outcome.get(Util.RESULT);
                if(!resultNode.isDefined()) {
                    return Collections.emptyList();
                }
                final ModelNode reqProps = resultNode.get(Util.REQUEST_PROPERTIES);
                if(!reqProps.isDefined()) {
                    return Collections.emptyList();
                }
                final List<Property> propList = reqProps.asPropertyList();
                result = new ArrayList<CommandArgument>(propList.size());
                for(final Property prop : propList) {
                    final CommandLineCompleter completer = getCompleter(globalOpProps, prop, ctx, operationName, address);
                    result.add(new CommandArgument(){
                        final String argName = prop.getName();
                        @Override
                        public String getFullName() {
                            return argName;
                        }

                        @Override
                        public String getShortName() {
                            return null;
                        }

                        @Override
                        public int getIndex() {
                            return -1;
                        }

                        @Override
                        public boolean isPresent(ParsedCommandLine args) throws CommandFormatException {
                            return args.hasProperty(argName);
                        }

                        @Override
                        public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                            return !isPresent(ctx.getParsedCommandLine());
                        }

                        @Override
                        public String getValue(ParsedCommandLine args) throws CommandFormatException {
                            return args.getPropertyValue(argName);
                        }

                        @Override
                        public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
                            if(!isPresent(args)) {
                                throw new CommandFormatException("Property '" + argName + "' is missing required value.");
                            }
                            return args.getPropertyValue(argName);
                        }

                        @Override
                        public boolean isValueComplete(ParsedCommandLine args) throws CommandFormatException {
                            if(!isPresent(args)) {
                                return false;
                            }
                            if(argName.equals(args.getLastParsedPropertyName())) {
                                return false;
                            }
                            return true;
                        }

                        @Override
                        public boolean isValueRequired() {
                            boolean required = true;
                            ModelNode mn = prop.getValue().get("type");
                            if (mn != null) {
                                // No value required for boolean
                                required = mn.asType() != ModelType.BOOLEAN;
                            }
                            return required;
                        }

                        @Override
                        public CommandLineCompleter getValueCompleter() {
                            return completer;
                        }});
                }
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    private CommandLineCompleter getCompleter(final Map<String, CommandLineCompleterFactory> globalOpProps, final Property prop, CommandContext ctx, String operationName, OperationRequestAddress address) throws IllegalArgumentException {
        CommandLineCompleter propCompleter = null;
        final CommandLineCompleterFactory factory = globalOpProps == null ? null : globalOpProps.get(prop.getName());
        if (factory != null) {
            propCompleter = factory.createCompleter(ctx, address);
        }
        if (propCompleter == null) {
            ModelNode attrDescr = prop.getValue();
            final ModelNode typeNode = attrDescr.get(Util.TYPE);
            if (typeNode.isDefined() && ModelType.BOOLEAN.equals(typeNode.asType())) {
                return SimpleTabCompleter.BOOLEAN;
            }
            if (attrDescr.has(Util.VALUE_TYPE)) {
                final ModelNode valueTypeNode = attrDescr.get(Util.VALUE_TYPE);
                if (typeNode.isDefined() && ModelType.LIST.equals(typeNode.asType())) {
                    return new ValueTypeCompleter(attrDescr, address);
                }
                try {
                    // the logic is: if value-type is set to a specific type
                    // (i.e. doesn't describe a custom structure)
                    // then if allowed is specified, use it.
                    // it might be broken but so far this is not looking clear to me
                    valueTypeNode.asType();
                    if (attrDescr.has(Util.ALLOWED)) {
                        return getAllowedCompleter(prop);
                    }
                    // Possibly a Map.
                    if (typeNode.isDefined() && ModelType.OBJECT.equals(typeNode.asType())) {
                        return new ValueTypeCompleter(attrDescr, address);
                    }
                } catch (IllegalArgumentException e) {
                    // TODO this means value-type describes a custom structure
                    return new ValueTypeCompleter(attrDescr, address);
                }
            }
            if (attrDescr.has(Util.FILESYSTEM_PATH) && attrDescr.get(Util.FILESYSTEM_PATH).asBoolean()) {
                return Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
            }
            if (attrDescr.has(Util.RELATIVE_TO) && attrDescr.get(Util.RELATIVE_TO).asBoolean()) {
                return new DeploymentItemCompleter(address);
            }
            if (attrDescr.has(Util.ALLOWED)) {
                return getAllowedCompleter(prop);
            }
            if (attrDescr.has(Util.CAPABILITY_REFERENCE)) {
                return new CapabilityReferenceCompleter(address,
                        attrDescr.get(Util.CAPABILITY_REFERENCE).asString());
            }
        }
        return propCompleter;
    }

    private CommandLineCompleter getAllowedCompleter(final Property prop) {
        final ModelNode allowedNode = prop.getValue().get(Util.ALLOWED);
        if(allowedNode.isDefined()) {
            final List<ModelNode> nodeList = allowedNode.asList();
            final String[] values = new String[nodeList.size()];
            for(int i = 0; i < values.length; ++i) {
                values[i] = nodeList.get(i).asString();
            }
            return new SimpleTabCompleter(values);
        }
        return null;
    }

    @Override
    public Map<String, OperationRequestHeader> getHeaders(CommandContext ctx) {
        return HEADERS;
    }

    private static final Map<String, Map<String, CommandLineCompleterFactory>> globalOpPropCompleters = new HashMap<String, Map<String, CommandLineCompleterFactory>>();
    static void addGlobalOpPropCompleter(String op, String prop, CommandLineCompleterFactory factory) {
        Map<String, CommandLineCompleterFactory> propMap = globalOpPropCompleters.get(op);
        if(propMap == null) {
            propMap = new HashMap<String,CommandLineCompleterFactory>();
            globalOpPropCompleters.put(op, propMap);
        }
        propMap.put(prop, factory);
    }
    static CommandLineCompleterFactory getGlobalOpPropCompleter(String op, String prop) {
        final Map<String, CommandLineCompleterFactory> propMap = globalOpPropCompleters.get(op);
        return propMap == null ? null : propMap.get(prop);
    }

    static {
        final CommandLineCompleterFactory attrNameCompleter = new CommandLineCompleterFactory(){
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address);
            }};
        addGlobalOpPropCompleter(Util.UNDEFINE_ATTRIBUTE, Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter(Util.READ_ATTRIBUTE, Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter(Util.WRITE_ATTRIBUTE, Util.NAME, new CommandLineCompleterFactory(){
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address, true);
            }});
        addGlobalOpPropCompleter(Util.WRITE_ATTRIBUTE, Util.VALUE, new CommandLineCompleterFactory(){
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                final String propName = ctx.getParsedCommandLine().getPropertyValue(Util.NAME);
                if(propName == null) {
                    return NO_CANDIDATES_COMPLETER;
                }

                final ModelNode req = new ModelNode();
                final ModelNode addrNode = req.get(Util.ADDRESS);
                for (OperationRequestAddress.Node node : address) {
                    addrNode.add(node.getType(), node.getName());
                }
                req.get(Util.OPERATION).set(Util.READ_RESOURCE_DESCRIPTION);
                final ModelNode response;
                try {
                    response = ctx.getModelControllerClient().execute(req);
                } catch (Exception e) {
                    return NO_CANDIDATES_COMPLETER;
                }
                final ModelNode result = response.get(Util.RESULT);
                if (!result.isDefined()) {
                    return NO_CANDIDATES_COMPLETER;
                }
                final ModelNode attrs = result.get(Util.ATTRIBUTES);
                if(!attrs.isDefined()) {
                    return NO_CANDIDATES_COMPLETER;
                }
                final ModelNode attrDescr = attrs.get(propName);
                if(!attrDescr.isDefined()) {
                    return NO_CANDIDATES_COMPLETER;
                }

                if (attrDescr.has(Util.FILESYSTEM_PATH) && attrDescr.get(Util.FILESYSTEM_PATH).asBoolean()) {
                    return Util.isWindows() ? new WindowsFilenameTabCompleter(ctx) : new DefaultFilenameTabCompleter(ctx);
                }

                return new AttributeTypeDescrValueCompleter(attrDescr, address);
            }});
        addGlobalOpPropCompleter(Util.READ_OPERATION_DESCRIPTION, Util.NAME, new CommandLineCompleterFactory(){
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new OperationNameCompleter(address);
            }});

        final CommandLineCompleterFactory childTypeCompleter = new CommandLineCompleterFactory(){
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new ChildTypeCompleter(address);
            }};
        addGlobalOpPropCompleter(Util.READ_CHILDREN_NAMES, Util.CHILD_TYPE, childTypeCompleter);
        addGlobalOpPropCompleter(Util.READ_CHILDREN_RESOURCES, Util.CHILD_TYPE, childTypeCompleter);

        addGlobalOpPropCompleter("map-put", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("map-remove", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("map-get", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("map-clear", Util.NAME, attrNameCompleter);

        addGlobalOpPropCompleter("list-add", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("list-remove", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("list-get", Util.NAME, attrNameCompleter);
        addGlobalOpPropCompleter("list-clear", Util.NAME, attrNameCompleter);
    }
    interface CommandLineCompleterFactory {
        CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address);
    }
}
