/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.version.Stability;

/**
 * @author Emanuel Muckenhuber
 */
abstract class AbstractTransformationDescriptionBuilder implements TransformationDescriptionBuilder {

    private final Stability stability;
    protected final PathElement pathElement;

    protected PathAddressTransformer pathAddressTransformer;
    protected ResourceTransformer resourceTransformer;
    protected OperationTransformer operationTransformer;

    protected final Map<String, OperationTransformationEntry> operationTransformers = new HashMap<String, OperationTransformationEntry>();
    protected final List<TransformationDescriptionBuilder> children = new ArrayList<TransformationDescriptionBuilder>();
    protected  final DynamicDiscardPolicy dynamicDiscardPolicy;

    protected AbstractTransformationDescriptionBuilder(Stability stability, PathElement pathElement, PathAddressTransformer pathAddressTransformer,
                                             ResourceTransformer resourceTransformer,
                                             OperationTransformer operationTransformer,
                                             DynamicDiscardPolicy dynamicDiscardPolicy) {
        this.stability = stability;
        this.pathElement = pathElement;
        this.pathAddressTransformer = pathAddressTransformer;
        this.resourceTransformer = resourceTransformer;
        this.operationTransformer = operationTransformer;
        this.dynamicDiscardPolicy = dynamicDiscardPolicy;
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    public TransformationDescriptionBuilder setResourceTransformer(ResourceTransformer resourceTransformer) {
        this.resourceTransformer = resourceTransformer;
        return this;
    }

    void addOperationTransformerEntry(String operationName, OperationTransformationEntry transformer) {
        operationTransformers.put(operationName, transformer);
    }

    protected TransformationDescription buildDefault(final DiscardPolicy discardPolicy, boolean inherited, final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry registry) {
        return buildDefault(discardPolicy, inherited, registry, Collections.<String>emptyList());
    }

    /**
     * Build the default transformation description.
     *
     * @param discardPolicy the discard policy to use
     * @param inherited whether the definition is inherited
     * @param registry the attribute transformation rules for the resource
     * @param discardedOperations the discarded operations
     * @return the transformation description
     */
    protected TransformationDescription buildDefault(final DiscardPolicy discardPolicy, boolean inherited, final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry registry, List<String> discardedOperations) {
        // Build attribute rules
        final Map<String, AttributeTransformationDescription> attributes = registry.buildAttributes();
        // Create operation transformers
        final Map<String, OperationTransformer> operations = buildOperationTransformers(registry);
        // Process children
        final List<TransformationDescription> children = buildChildren();

        if (discardPolicy == DiscardPolicy.NEVER) {
            // TODO override more global operations?
            if(! operations.containsKey(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION)) {
                operations.put(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION, OperationTransformationRules.createWriteOperation(attributes));
            }
            if(! operations.containsKey(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION)) {
                operations.put(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION, OperationTransformationRules.createUndefinedOperation(attributes));
            }
        }
        // Create the description
        Set<String> discarded = new HashSet<>();
        discarded.addAll(discardedOperations);
        return new TransformingDescription(pathElement, pathAddressTransformer, discardPolicy, inherited,
                resourceTransformer, attributes, operations, children, discarded, dynamicDiscardPolicy);
    }

    /**
     * Build the operation transformers.
     *
     * @param registry the shared resource registry
     * @return the operation transformers
     */
    protected Map<String, OperationTransformer> buildOperationTransformers(AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry registry) {
        final Map<String, OperationTransformer> operations = new HashMap<String, OperationTransformer>();
        for(final Map.Entry<String, OperationTransformationEntry> entry: operationTransformers.entrySet()) {
            final OperationTransformer transformer = entry.getValue().getOperationTransformer(registry);
            operations.put(entry.getKey(), transformer);
        }
        return operations;
    }

    /**
     * Build all children.
     *
     * @return the child descriptions
     */
    protected List<TransformationDescription> buildChildren() {
        if(children.isEmpty()) {
            return Collections.emptyList();
        }
        final List<TransformationDescription> children = new ArrayList<TransformationDescription>();
        for(final TransformationDescriptionBuilder builder : this.children) {
            children.add(builder.build());
        }
        return children;
    }

    abstract static class OperationTransformationEntry {

        /**
         * Get the operation transformer.
         *
         * @param resourceRegistry the attribute transformation description for the resource
         * @return the operation transformer
         */
        abstract OperationTransformer getOperationTransformer(AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry resourceRegistry);

    }

}
