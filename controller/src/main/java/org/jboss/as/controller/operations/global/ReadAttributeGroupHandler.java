/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_GROUP_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_ALIASES;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_DEFAULTS;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.INCLUDE_RUNTIME;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.NAME;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers.AbstractMultiTargetHandler;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} returning the attributes of a resource for a given attribute-group.
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class ReadAttributeGroupHandler extends AbstractMultiTargetHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(READ_ATTRIBUTE_GROUP_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(NAME, INCLUDE_RUNTIME, INCLUDE_DEFAULTS, INCLUDE_ALIASES)
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.PROPERTY)
            .build();

    private static final SimpleAttributeDefinition RESOLVE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.RESOLVE_EXPRESSIONS, ModelType.BOOLEAN)
            .setRequired(false)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    public static final OperationDefinition RESOLVE_DEFINITION = new SimpleOperationDefinitionBuilder(READ_ATTRIBUTE_GROUP_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(NAME, RESOLVE, INCLUDE_RUNTIME, INCLUDE_DEFAULTS, INCLUDE_ALIASES)
            .setReadOnly()
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.PROPERTY)
            .build();

    static OperationStepHandler INSTANCE = new ReadAttributeGroupHandler();
    public static OperationStepHandler RESOLVE_INSTANCE = new ReadAttributeGroupHandler(true);

    private final ParametersValidator validator = new ParametersValidator() {

        @Override
        public void validate(ModelNode operation) throws OperationFailedException {
            super.validate(operation);
            for (AttributeDefinition def : DEFINITION.getParameters()) {
                def.validateOperation(operation);
            }
            if( operation.hasDefined(ModelDescriptionConstants.RESOLVE_EXPRESSIONS)){
                if(operation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).asBoolean(false) && !resolvable){
                    throw ControllerLogger.ROOT_LOGGER.unableToResolveExpressions();
                }
            }
        }
    };

    private final boolean resolvable;

    public ReadAttributeGroupHandler() {
        this(false);
    }

    public ReadAttributeGroupHandler(boolean resolvable) {
        this.resolvable = resolvable;
    }

    private void addReadAttributeStep(OperationContext context, PathAddress address, boolean defaults, boolean resolve,
                                      FilteredData localFilteredData,
            ImmutableManagementResourceRegistration registry,
            AttributeDefinition.NameAndGroup attributeKey, Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> responseMap) {
        // See if there was an override registered for the standard :read-attribute handling (unlikely!!!)
        OperationStepHandler overrideHandler = registry.getOperationHandler(PathAddress.EMPTY_ADDRESS, READ_ATTRIBUTE_OPERATION);
        if (overrideHandler != null
                && (overrideHandler == ReadAttributeHandler.INSTANCE || overrideHandler == ReadAttributeHandler.RESOLVE_INSTANCE)) {
            // not an override
            overrideHandler = null;
        }

        OperationStepHandler readAttributeHandler = new ReadAttributeHandler(localFilteredData, overrideHandler, (resolve && resolvable));

        final ModelNode attributeOperation = Util.getReadAttributeOperation(address, attributeKey.getName());
        attributeOperation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(defaults);
        attributeOperation.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(resolve);

        final ModelNode attrResponse = new ModelNode();
        GlobalOperationHandlers.AvailableResponse availableResponse = new GlobalOperationHandlers.AvailableResponse(attrResponse);
        responseMap.put(attributeKey, availableResponse);

        GlobalOperationHandlers.AvailableResponseWrapper wrapper = new GlobalOperationHandlers.AvailableResponseWrapper(readAttributeHandler, availableResponse);

        context.addStep(attrResponse, attributeOperation, wrapper, OperationContext.Stage.MODEL, true);
    }

    @Override
    void doExecute(OperationContext context, ModelNode operation, FilteredData filteredData, boolean ignoreMissingResource) throws OperationFailedException {
        validator.validate(operation);
        final PathAddress address = context.getCurrentAddress();

        final boolean includeRutime = operation.get(ModelDescriptionConstants.INCLUDE_RUNTIME).asBoolean(false);
        final boolean aliases = operation.get(ModelDescriptionConstants.INCLUDE_ALIASES).asBoolean(false);
        final boolean defaults = operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).asBoolean(true);
        final boolean resolve = RESOLVE.resolveModelAttribute(context, operation).asBoolean();
        final ModelNode groupNameNode = NAME.resolveModelAttribute(context, operation);
        final String groupName = groupNameNode.isDefined() ? groupNameNode.asString() : null;
        final FilteredData localFilteredData = new FilteredData(address);

        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics = includeRutime
                ? new HashMap<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse>()
                : Collections.<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse>emptyMap();
        final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes = new HashMap<>();

        final ReadAttributeGroupAssemblyHandler assemblyHandler = new ReadAttributeGroupAssemblyHandler(metrics, otherAttributes, filteredData, ignoreMissingResource);
        context.addStep(assemblyHandler, includeRutime ? OperationContext.Stage.VERIFY : OperationContext.Stage.MODEL, true);

        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        final Set<String> attributeNames = registry != null ? registry.getAttributeNames(PathAddress.EMPTY_ADDRESS) : Collections.<String>emptySet();
        for (final String attributeName : attributeNames) {
            final AttributeAccess access = registry.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
            final AttributeDefinition ad = access.getAttributeDefinition();
            if ((aliases || !access.getFlags().contains(AttributeAccess.Flag.ALIAS))
                    && (includeRutime || access.getStorageType() == AttributeAccess.Storage.CONFIGURATION)
                    && (groupName == null || groupName.equals(ad.getAttributeGroup()))) {
                Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> responseMap = access.getAccessType() == AttributeAccess.AccessType.METRIC ? metrics : otherAttributes;
                AttributeDefinition.NameAndGroup nag = ad == null ? new AttributeDefinition.NameAndGroup(attributeName) : new AttributeDefinition.NameAndGroup(ad);
                addReadAttributeStep(context, address, defaults, resolve, localFilteredData, registry, nag, responseMap);
            }
        }
    }

    /**
     * Assembles the response to a read-attribute request from the components gathered by earlier steps.
     */
    private static class ReadAttributeGroupAssemblyHandler implements OperationStepHandler {

        private final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics;
        private final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes;
        private final FilteredData filteredData;
        private final boolean ignoreMissingResource;

        /**
         * Creates a ReadAttributeGroupAssemblyHandler that will assemble the response using the contents of the given
         * maps.
         *  @param metrics map of attributes of AccessType.METRIC. Keys are the attribute names, values are the full
         * read-attribute response from invoking the attribute's read handler. Will not be {@code null}
         * @param otherAttributes map of attributes not of AccessType.METRIC that have a read handler registered. Keys
         * are the attribute names, values are the full read-attribute response from invoking the attribute's read
         * handler. Will not be {@code null}
         * @param filteredData information about resources and attributes that were filtered
         * @param ignoreMissingResource {@code true} if we should ignore occasions when the targeted resource
         *                                          does not exist; {@code false} if we should throw
         *                                          {@link org.jboss.as.controller.registry.Resource.NoSuchResourceException}
         *                                          in such cases
         */
        private ReadAttributeGroupAssemblyHandler(final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics,
                                                  final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes,
                                                  final FilteredData filteredData, final boolean ignoreMissingResource) {
            this.metrics = metrics;
            this.otherAttributes = otherAttributes;
            this.filteredData = filteredData;
            this.ignoreMissingResource = ignoreMissingResource;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            Map<AttributeDefinition.NameAndGroup, ModelNode> sortedAttributes = new TreeMap<>();
            boolean failed = false;
            for (Map.Entry<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> entry : otherAttributes.entrySet()) {
                GlobalOperationHandlers.AvailableResponse ar = entry.getValue();
                if (ar.unavailable) {
                    // Our target resource has disappeared
                    handleMissingResource(context);
                    return;
                }
                ModelNode value = ar.response;
                if (!value.has(FAILURE_DESCRIPTION)) {
                    sortedAttributes.put(entry.getKey(), value.get(RESULT));
                } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    failed = true;
                    break;
                }
            }
            if (!failed) {
                for (Map.Entry<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metric : metrics.entrySet()) {
                    GlobalOperationHandlers.AvailableResponse ar = metric.getValue();
                    if (ar.unavailable) {
                        // Our target resource has disappeared
                        handleMissingResource(context);
                        return;
                    }
                    ModelNode value = ar.response;
                    if (!value.has(FAILURE_DESCRIPTION)) {
                        sortedAttributes.put(metric.getKey(), value.get(RESULT));
                    }
                }
                final ModelNode result = context.getResult();
                result.setEmptyObject();
                for (Map.Entry<AttributeDefinition.NameAndGroup, ModelNode> entry : sortedAttributes.entrySet()) {
                    result.get(entry.getKey().getName()).set(entry.getValue());
                }
                if (filteredData!= null && filteredData.hasFilteredData()) {
                    context.getResponseHeaders().get(ACCESS_CONTROL).set(filteredData.toModelNode());
                }
            }
        }

        private void handleMissingResource(OperationContext context) {
            // Our target resource has disappeared
            if (context.hasResult()) {
                context.getResult().set(new ModelNode());
            }
            if (!ignoreMissingResource) {
                throw ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(context.getCurrentAddress());
            }
        }
    }
}
