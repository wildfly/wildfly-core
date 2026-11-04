/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WARNINGS;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Assembles the response to a read-resource request from the components gathered by earlier steps.
 */
public final class ReadResourceAssemblyHandler implements OperationStepHandler {

    private final PathAddress address;
    private final Map<String, ModelNode> directChildren;
    private final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics;
    private final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes;
    private final Map<PathElement, ModelNode> childResources;
    private final Set<String> nonExistentChildTypes;
    private final boolean reportFilteredData;
    private final FilteredData filteredData;
    private final boolean ignoreMissingResource;

    /**
     * Creates a ReadResourceAssemblyHandler that will assemble the response for a resource with no child types
     * using the contents of the given maps.
     *
     * @param address            address of the resource
     * @param metrics            map of attributes of AccessType.METRIC. Keys are the attribute names, values are the full
     *                           read-attribute response from invoking the attribute's read handler. Will not be {@code null}
     * @param otherAttributes    map of attributes not of AccessType.METRIC that have a read handler registered. Keys
     *                           are the attribute names, values are the full read-attribute response from invoking the
     *                           attribute's read handler. Will not be {@code null}
     * @param reportFilteredData {@code true} if this handler should include the filtered data details in its repsonse;
     *                           {@code false} if a handler for a parent resource will handle this reporting.
     * @param filteredData       information about resources and attributes that were filtered
     */
    public ReadResourceAssemblyHandler(final PathAddress address,
                                       final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics,
                                       final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes,
                                       final boolean reportFilteredData,
                                       final FilteredData filteredData) {
        this(address, metrics, otherAttributes, Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(),
                reportFilteredData, filteredData, false);
    }

    // NOTE: This constructor is package protected because the only known use case for this handler where the resource type
    // supports children is in ReadResourceHandler.doExecuteInternal().

    /**
     * Creates a ReadResourceAssemblyHandler that will assemble the response using the contents
     * of the given maps.
     *
     * @param address               address of the resource
     * @param metrics               map of attributes of AccessType.METRIC. Keys are the attribute names, values are the full
     *                              read-attribute response from invoking the attribute's read handler. Will not be {@code null}
     * @param otherAttributes       map of attributes not of AccessType.METRIC that have a read handler registered. Keys
     *                              are the attribute names, values are the full read-attribute response from invoking the
     *                              attribute's read handler. Will not be {@code null}
     * @param directChildren        Children names read directly from the parent resource where we didn't call read-resource
     *                              to gather data. We wouldn't call read-resource if the recursive=false
     * @param childResources        read-resource response from child resources, where the key is the PathAddress
     *                              relative to the address of the operation this handler is handling and the
     *                              value is the full read-resource response. Will not be {@code null}
     * @param nonExistentChildTypes names of child types where no data is available
     * @param reportFilteredData    {@code true} if this handler should include the filtered data details in its repsonse;
     *                              {@code false} if a handler for a parent resource will handle this reporting.
     * @param filteredData          information about resources and attributes that were filtered
     * @param ignoreMissingResource {@code true} if we should ignore occasions when the targeted resource
     *                              does not exist; {@code false} if we should throw
     *                              {@link org.jboss.as.controller.registry.Resource.NoSuchResourceException}
     *                              in such cases
     */
    ReadResourceAssemblyHandler(final PathAddress address,
                                final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> metrics,
                                final Map<AttributeDefinition.NameAndGroup, GlobalOperationHandlers.AvailableResponse> otherAttributes,
                                final Map<String, ModelNode> directChildren,
                                final Map<PathElement, ModelNode> childResources,
                                final Set<String> nonExistentChildTypes,
                                final boolean reportFilteredData,
                                final FilteredData filteredData,
                                final boolean ignoreMissingResource) {
        this.address = address;
        this.metrics = metrics;
        this.otherAttributes = otherAttributes;
        this.directChildren = directChildren;
        this.childResources = childResources;
        this.nonExistentChildTypes = nonExistentChildTypes;
        this.reportFilteredData = reportFilteredData;
        this.filteredData = filteredData;
        this.ignoreMissingResource = ignoreMissingResource;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        Map<AttributeDefinition.NameAndGroup, ModelNode> sortedAttributes = new TreeMap<>();
        Map<String, ModelNode> sortedChildren = new TreeMap<String, ModelNode>();
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
                addWarning(value, context);
            } else if (value.hasDefined(FAILURE_DESCRIPTION)) {
                context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                failed = true;
                break;
            }
        }
        // Allow prompt gc
        otherAttributes.clear();
        if (!failed) {
            // We make a copy of the ModelNode tree here, so use an iterator and remove promptly
            // to reduce peak memory use ASAP in large reads
            for (Iterator<Map.Entry<PathElement, ModelNode>> iter = childResources.entrySet().iterator(); iter.hasNext(); ) {
                Map.Entry<PathElement, ModelNode> entry = iter.next();
                PathElement path = entry.getKey();
                ModelNode value = entry.getValue();
                iter.remove();
                if (!value.has(FAILURE_DESCRIPTION)) {
                    addWarning(value, context);
                    if (value.hasDefined(RESULT)) {
                        ModelNode childTypeNode = sortedChildren.get(path.getKey());
                        if (childTypeNode == null) {
                            childTypeNode = new ModelNode();
                            sortedChildren.put(path.getKey(), childTypeNode);
                        }
                        childTypeNode.get(path.getValue()).set(value.get(RESULT));
                    } else {
                        // A child did not produce a response. We don't know if the definition
                        // of our resource indicates the child that has disappeared must be
                        // present, so we don't want to produce a response for our resource
                        // without the child if our resource is now gone as well.
                        // So, see if our resource has disappeared as well.
                        if (!filteredData.isAddressFiltered(address, path)) {
                            // Wasn't filtered. Confirm our resource still exists
                            try {
                                context.readResourceFromRoot(address, false);
                            } catch (Resource.NoSuchResourceException e) {
                                handleMissingResource(context);
                                return;
                            }
                        } // else there's no result because it was just filtered
                    }
                } else if (!failed && value.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(value.get(FAILURE_DESCRIPTION));
                    failed = true;
                }
            }
        }
        // Allow prompt gc
        childResources.clear();
        if (!failed) {
            for (Map.Entry<String, ModelNode> directChild : directChildren.entrySet()) {
                sortedChildren.put(directChild.getKey(), directChild.getValue());
            }
            // Allow prompt gc
            directChildren.clear();
            for (String nonExistentChildType : nonExistentChildTypes) {
                sortedChildren.put(nonExistentChildType, new ModelNode());
            }
            // Allow prompt gc
            nonExistentChildTypes.clear();
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
                // we ignore metric failures
                // TODO how to prevent the metric failure screwing up the overall context?
            }
            // Allow prompt gc
            metrics.clear();

            final ModelNode result = context.getResult();
            result.setEmptyObject();
            for (Map.Entry<AttributeDefinition.NameAndGroup, ModelNode> entry : sortedAttributes.entrySet()) {
                result.get(entry.getKey().getName()).set(entry.getValue());
            }
            // Allow prompt gc
            sortedAttributes.clear();

            // We make a copy of the ModelNode tree here, so use an iterator and remove promptly
            // to reduce peak memory use ASAP in large reads
            for (Iterator<Map.Entry<String, ModelNode>> iter = sortedChildren.entrySet().iterator(); iter.hasNext(); ) {
                Map.Entry<String, ModelNode> entry = iter.next();
                String type = entry.getKey();
                ModelNode value = entry.getValue();
                iter.remove();
                if (!entry.getValue().isDefined()) {
                    result.get(type).set(value);
                } else {
                    ModelNode childTypeNode = new ModelNode();
                    for (Property property : value.asPropertyList()) {
                        PathElement pe = PathElement.pathElement(type, property.getName());
                        if (!filteredData.isFilteredResource(address, pe)) {
                            childTypeNode.get(property.getName()).set(property.getValue());
                        }
                    }
                    result.get(type).set(childTypeNode);
                }
            }
            // Allow prompt gc
            sortedChildren.clear();

            if (reportFilteredData && filteredData.hasFilteredData()) {
                context.getResponseHeaders().get(ACCESS_CONTROL).set(filteredData.toModelNode());
            }
        }
    }

    private void addWarning(ModelNode value, OperationContext context) {
        if (value.hasDefined(RESPONSE_HEADERS, WARNINGS)) {
            for (ModelNode response : value.get(RESPONSE_HEADERS).get(WARNINGS).asList()) {
                String level = response.get("level").asString();
                context.addResponseWarning(Level.parse(level), response.get(WARNING));
            }
        }
    }

    private void handleMissingResource(OperationContext context) {
        // Our target resource has disappeared
        if (context.hasResult()) {
            context.getResult().set(new ModelNode());
        }
        if (!ignoreMissingResource) {
            throw ControllerLogger.MGMT_OP_LOGGER.managementResourceNotFound(address);
        }
    }
}
