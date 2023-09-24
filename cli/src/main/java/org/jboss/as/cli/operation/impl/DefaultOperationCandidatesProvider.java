/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.handlers.FilenameTabCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.impl.AttributeNamePathCompleter;
import org.jboss.as.cli.impl.BytesCompleter;
import org.jboss.as.cli.impl.DeploymentItemCompleter;
import org.jboss.as.cli.impl.ValueTypeCompleter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestHeader;
import org.jboss.as.cli.operation.ParsedCommandLine;
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
                // The input buffer can be a full command or headers, parseOperation
                // can parse both.
                parsedOp.parseOperation(null, buffer, ctx);
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
            int result = SimpleTabCompleter.BOOLEAN.complete(ctx, buffer.substring(parsedOp.getLastChunkOriginalIndex()), cursor, candidates);
            // Special case when the value is already complete, add a separator.
            if (candidates.size() == 1) {
                if (candidates.get(0).equals(buffer.substring(parsedOp.getLastChunkOriginalIndex()))) {
                    candidates.clear();
                    candidates.add(buffer.substring(parsedOp.getLastChunkOriginalIndex()) + ";");
                }
            }// No value...
            if(result < 0) {
                return result;
            }
            return parsedOp.getOriginalOffset(parsedOp.getLastChunkIndex() + result);
        }};

    private static final CommandLineCompleter INT_HEADER_COMPLETER = new CommandLineCompleter(){

        private final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();

        @Override
        public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
            try {
                // The input buffer can be a full command or headers, parseOperation
                // can parse both.
                parsedOp.parseOperation(null, buffer, ctx);
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

    /**
     * Class that establishes completion visibility of a property.
     * If all (could be double TAB), hidden properties (due to alternatives/requires)
     * are shown.
     */
    public static class PropertyVisibility {
        private static final String REQUIRED_PROPERTY = "*";
        private final Set<String> presentProperties;
        private final String radical;
        private final boolean multiple;
        private final Set<String> invalidProperties = new HashSet<>();
        private final List<Property> propList;
        private final Set<String> required = new HashSet<>();
        public PropertyVisibility(List<Property> propList,
                Set<String> presentProperties, String radical) {
            this.propList = propList;
            this.presentProperties = Collections.unmodifiableSet(presentProperties);
            this.radical = radical;

            // Create some maps to speedup lookup done further down the road.
            Map<String, Set<String>> requiresMap = new HashMap<>();
            Map<String, Set<String>> alternativesMap = new HashMap<>();
            for (Property prop : propList) {
                // Present property, hides alternatives.
                if (presentProperties.contains(prop.getName())) {
                    if (prop.getValue().hasDefined(Util.ALTERNATIVES)) {
                        List<ModelNode> alternatives = prop.getValue().get(Util.ALTERNATIVES).asList();
                        for (ModelNode alt : alternatives) {
                            invalidProperties.add(alt.asString());
                        }
                    }
                }
                if (prop.getValue().hasDefined(Util.ALTERNATIVES)) {
                    Set<String> set = new HashSet<>();
                    alternativesMap.put(prop.getName(), set);
                    List<ModelNode> alternatives = prop.getValue().get(Util.ALTERNATIVES).asList();
                    for (ModelNode alt : alternatives) {
                        set.add(alt.asString());
                    }
                }
                if (prop.getValue().hasDefined(Util.REQUIRES)) {
                    Set<String> set = new HashSet<>();
                    requiresMap.put(prop.getName(), set);
                    List<ModelNode> requires = prop.getValue().get(Util.REQUIRES).asList();
                    for (ModelNode req : requires) {
                        set.add(req.asString());
                    }
                }
            }
            // At this point all alternatives have been hidden.
            // All present non hidden properties must have their requires computed.
            for (Property prop : propList) {
                if (presentProperties.contains(prop.getName())) {
                    if (!invalidProperties.contains(prop.getName())) {
                        Set<String> requires = requiresMap.get(prop.getName());
                        if (requires != null) {
                            for (String req : requires) {
                                // A require could be hidden because it is in alternatives with a present property.
                                if (!invalidProperties.contains(req)) {
                                    required.add(req);
                                }
                                // A require that has no alternatives inside the requires list
                                // means that this require is really required. Any alternative with it must be hidden
                                Set<String> alternatives = alternativesMap.get(req);
                                if (alternatives != null) {
                                    boolean found = false;
                                    for (String req2 : requires) {
                                        if (alternatives.contains(req2)) {
                                            found = true;
                                            break;
                                        }
                                    }
                                    if (!found) {
                                        // No alternatives in the requires, hide all alternatives of this require.
                                        for (String alt : alternatives) {
                                            invalidProperties.add(alt);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // A not present properties that requires only invalid alternative
                    // must be hidden.
                    if (prop.getValue().hasDefined(Util.REQUIRES)) {
                        List<ModelNode> requires = prop.getValue().get(Util.REQUIRES).asList();
                        boolean invalid = true;
                        for (ModelNode req : requires) {
                            invalid &= invalidProperties.contains(req.asString());
                        }
                        if (invalid) {
                            invalidProperties.add(prop.getName());
                        }
                    }
                }
            }

            // The number of candidates can only be computed once visibility has
            // been established for all properties.
            // When a single candidate is to be returned name are not decorated.
            int i = 0;
            for (Property prop : propList) {
                // Check visibility of property.
                if (canAppearNext(prop)) {
                    i += 1;
                }
                if (i > 1) {
                    break;
                }
            }
            this.multiple = i > 1;
        }

        private String getName(Property prop) {
            StringBuilder builder = new StringBuilder();
            builder.append(prop.getName());
            if (multiple && isRequired(prop)) {
                builder.append(REQUIRED_PROPERTY);
            }

            return builder.toString();
        }

        private boolean isRequired(Property prop) {
            boolean actuallyRequired = (!prop.getValue().hasDefined(Util.REQUIRED)
                    || (prop.getValue().hasDefined(Util.REQUIRED)
                    && prop.getValue().get(Util.REQUIRED).asBoolean()));
            if (actuallyRequired) {
                return true;
            }
            // If it is required by another present property
            // makes it required.
            return required.contains(prop.getName());
        }

        public void addCandidates(List<String> candidates) {
            for (Property prop : propList) {
                if (canAppearNext(prop)) {
                    candidates.add(multiple ? getName(prop) : prop.getName());
                }
            }
            Collections.sort(candidates);
        }

        public boolean hasMore() {
            for (Property prop : propList) {
                if (!presentProperties.contains(prop.getName())
                        && !invalidProperties.contains(prop.getName())) {
                    return true;
                }
            }
            return false;
        }

        private boolean canAppearNext(Property prop) {
            if (presentProperties.contains(prop.getName())) {
                return false;
            }

            // If user typed something, complete if possible.
            // Invalid properties will be exposed in this case.
            if (radical != null && !radical.isEmpty()) {
                return prop.getName().startsWith(radical);
            }

            // The invalid alternatives
            if (invalidProperties.contains(prop.getName())) {
                return false;
            }

            return true;
        }
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
                result = getPropertiesFromPropList(propList, ctx, operationName, address);
            }
        } catch (Exception e) {
            result = Collections.emptyList();
        }
        return result;
    }

    protected List<CommandArgument> getPropertiesFromPropList(List<Property> propList, CommandContext ctx, String operationName, OperationRequestAddress address){
        final Map<String,CommandLineCompleterFactory> globalOpProps = globalOpPropCompleters.get(operationName);

        List<CommandArgument> result = new ArrayList<CommandArgument>(propList.size());

        String radical = null;
        if(ctx.getParsedCommandLine().getLastParsedPropertyValue() == null) {
            radical = ctx.getParsedCommandLine().getLastParsedPropertyName();
            //Check if the property is completely specified and is negated
            if(ctx.getParsedCommandLine().isLastPropertyNegated()) {
                for (Property prop : propList) {
                    if(radical.equals(prop.getName())){
                        radical = null;
                        break;
                    }
                }
            }
        }

        final PropertyVisibility visibility
                = new PropertyVisibility(propList,
                ctx.getParsedCommandLine().getPropertyNames(),
                radical);

        for(final Property prop : propList) {
            final CommandLineCompleter completer = getCompleter(globalOpProps, prop, ctx, operationName, address);
            result.add(new CommandArgument(){
                final String argName = prop.getName();
                @Override
                public String getFullName() {
                    return argName;
                }

                @Override
                public String getDecoratedName() {
                    return visibility.getName(prop);
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
                    return visibility.canAppearNext(prop);
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
                }
            });
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
            propCompleter = getCompleter(prop, ctx, address);
        }
        return propCompleter;
    }

    static CommandLineCompleter getCompleter(final Property prop, CommandContext ctx, OperationRequestAddress address) {
        ModelNode attrDescr = prop.getValue();
        final ModelNode typeNode = attrDescr.get(Util.TYPE);
        if (typeNode.isDefined() && ModelType.BOOLEAN.equals(typeNode.asType())) {
            return SimpleTabCompleter.BOOLEAN;
        }
        if (typeNode.isDefined() && ModelType.BYTES.equals(typeNode.asType())) {
            return BytesCompleter.INSTANCE;
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
            return FilenameTabCompleter.newCompleter(ctx);
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
        return null;
    }

    private static CommandLineCompleter getAllowedCompleter(final Property prop) {
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
                Property prop = getProperty(propName, attrs);
                if (prop == null) {
                    return NO_CANDIDATES_COMPLETER;
                }
                return getCompleter(prop, ctx, address);
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

        final CommandLineCompleterFactory mapAttrNameCompleter = new CommandLineCompleterFactory() {
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address, false,
                        AttributeNamePathCompleter.MAP_FILTER);
            }
        };

        final CommandLineCompleterFactory mapOnlyWritableAttrNameCompleter = new CommandLineCompleterFactory() {
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address, true,
                        AttributeNamePathCompleter.MAP_FILTER);
            }
        };

        final CommandLineCompleterFactory listAttrNameCompleter = new CommandLineCompleterFactory() {
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address,
                        AttributeNamePathCompleter.LIST_FILTER);
            }
        };

        final CommandLineCompleterFactory listOnlyWritableAttrNameCompleter = new CommandLineCompleterFactory() {
            @Override
            public CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address) {
                return new AttributeNamePathCompleter(address, true,
                        AttributeNamePathCompleter.LIST_FILTER);
            }
        };

        addGlobalOpPropCompleter("map-put", Util.NAME, mapOnlyWritableAttrNameCompleter);
        addGlobalOpPropCompleter("map-remove", Util.NAME, mapOnlyWritableAttrNameCompleter);
        addGlobalOpPropCompleter("map-get", Util.NAME, mapAttrNameCompleter);
        addGlobalOpPropCompleter("map-clear", Util.NAME, mapOnlyWritableAttrNameCompleter);

        addGlobalOpPropCompleter("list-add", Util.NAME, listOnlyWritableAttrNameCompleter);
        addGlobalOpPropCompleter("list-remove", Util.NAME, listOnlyWritableAttrNameCompleter);
        addGlobalOpPropCompleter("list-get", Util.NAME, listAttrNameCompleter);
        addGlobalOpPropCompleter("list-clear", Util.NAME, listOnlyWritableAttrNameCompleter);
    }
    interface CommandLineCompleterFactory {
        CommandLineCompleter createCompleter(CommandContext ctx, OperationRequestAddress address);
    }

    // package for testing purpose
    static Property getProperty(String propName, ModelNode attrs) {
        String[] arr = propName.split("\\.");
        ModelNode attrDescr = attrs;
        for (String item : arr) {
            // Remove list part.
            if (item.endsWith("]")) {
                int i = item.indexOf("[");
                if (i < 0) {
                    return null;
                }
                item = item.substring(0, i);
            }
            ModelNode descr = attrDescr.get(item);
            if (!descr.isDefined()) {
                if (attrDescr.has(Util.VALUE_TYPE)) {
                    ModelNode vt = attrDescr.get(Util.VALUE_TYPE);
                    if (vt.has(item)) {
                        attrDescr = vt.get(item);
                        continue;
                    }
                }
                return null;
            }
            attrDescr = descr;
        }
        return new Property(propName, attrDescr);
    }
}
