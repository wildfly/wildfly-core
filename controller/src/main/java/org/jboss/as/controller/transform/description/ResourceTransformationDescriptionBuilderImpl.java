/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ResourceTransformationDescriptionBuilderImpl extends AbstractTransformationDescriptionBuilder implements ResourceTransformationDescriptionBuilder {

    private final List<String> discardedOperations = new LinkedList<String>();
    private DiscardPolicy discardPolicy = DiscardPolicy.NEVER;
    private final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry registry = new AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry();

    protected ResourceTransformationDescriptionBuilderImpl(final PathElement pathElement) {
        this(pathElement, PathAddressTransformer.DEFAULT);
    }

    protected ResourceTransformationDescriptionBuilderImpl(final PathElement pathElement, final PathAddressTransformer pathAddressTransformer) {
        this(pathElement, pathAddressTransformer, null);
    }

    protected ResourceTransformationDescriptionBuilderImpl(PathElement pathElement, DynamicDiscardPolicy dynamicDiscardPolicy) {
        this(pathElement, PathAddressTransformer.DEFAULT, dynamicDiscardPolicy);
    }

    protected ResourceTransformationDescriptionBuilderImpl(final PathElement pathElement,
            final PathAddressTransformer pathAddressTransformer, DynamicDiscardPolicy dynamicDiscardPolicy) {
        super(pathElement, pathAddressTransformer, ResourceTransformer.DEFAULT, OperationTransformer.DEFAULT,
                dynamicDiscardPolicy);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildResource(final PathElement pathElement) {
        return addChildResource(pathElement, null);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildResource(PathElement pathElement, DynamicDiscardPolicy dynamicDiscardPolicy) {
        final ResourceTransformationDescriptionBuilderImpl builder = new ResourceTransformationDescriptionBuilderImpl(
                pathElement, dynamicDiscardPolicy);
        children.add(builder);
        return builder;
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildResource(final ResourceDefinition definition) {
        return addChildResource(definition.getPathElement(), null);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildResource(ResourceDefinition definition, DynamicDiscardPolicy dynamicDiscardPolicy) {
        return addChildResource(definition.getPathElement(), dynamicDiscardPolicy);
    }

    @Override
    public DiscardTransformationDescriptionBuilder discardChildResource(final PathElement pathElement) {
        final DiscardTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createDiscardInstance(pathElement);
        children.add(builder);
        return builder;
    }

    @Override
    public RejectTransformationDescriptionBuilder rejectChildResource(PathElement pathElement) {
        final RejectTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createRejectInstance(pathElement);
        children.add(builder);
        return builder;
    }


    @Override
    public ResourceTransformationDescriptionBuilder addChildRedirection(final PathElement current, final PathElement legacy) {
        return addChildRedirection(current, legacy, null);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildRedirection(PathElement current, PathElement legacy, DynamicDiscardPolicy dynamicDiscardPolicy) {
        final PathAddressTransformer transformation;
        if (legacy.isWildcard()) {
            assert current.isWildcard() : "legacy is wildcard while current is not";
            transformation = new PathAddressTransformer.ReplaceElementKey(legacy.getKey());
        } else {
            assert !current.isWildcard() : "legacy is fixed while current is not";
            transformation = new PathAddressTransformer.BasicPathAddressTransformer(legacy);
        }
        return addChildRedirection(current, transformation, dynamicDiscardPolicy);

    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildRedirection(final PathElement oldAddress, final PathAddressTransformer pathAddressTransformer) {
        return addChildRedirection(oldAddress, pathAddressTransformer, null);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildRedirection(PathElement oldAddress, PathAddressTransformer pathAddressTransformer, DynamicDiscardPolicy dynamicDiscardPolicy) {
        final ResourceTransformationDescriptionBuilderImpl builder = new ResourceTransformationDescriptionBuilderImpl(oldAddress, pathAddressTransformer, dynamicDiscardPolicy);
        children.add(builder);
        return builder;
    }


    @Override
    public ResourceTransformationDescriptionBuilder addChildBuilder(TransformationDescriptionBuilder builder) {
        children.add(builder);
        return this;
    }

    @Override
    public ResourceTransformationDescriptionBuilder setCustomResourceTransformer(final ResourceTransformer resourceTransformer) {
        super.setResourceTransformer(resourceTransformer);
        return this;
    }

    @Override
    public TransformationDescription build() {
        return buildDefault(discardPolicy, false, registry, discardedOperations);
    }

    @Override
    public OperationTransformationOverrideBuilder addOperationTransformationOverride(final String operationName) {
        final OperationTransformationOverrideBuilderImpl transformationBuilder = new OperationTransformationOverrideBuilderImpl(operationName, this);
        addOperationTransformerEntry(operationName, new OperationTransformationEntry() {
            @Override
            OperationTransformer getOperationTransformer(AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry resourceRegistry) {
                return transformationBuilder.createTransformer(resourceRegistry);
            }
        });
        return transformationBuilder;
    }

    @Override
    public ResourceTransformationDescriptionBuilder addRawOperationTransformationOverride(final String operationName, final OperationTransformer operationTransformer) {
        addOperationTransformerEntry(operationName, new OperationTransformationEntry() {
            @Override
            OperationTransformer getOperationTransformer(AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry resourceRegistry) {
                return operationTransformer;
            }
        });
        return this;
    }

    @Override
    public ConcreteAttributeTransformationDescriptionBuilder getAttributeBuilder() {
        return new ConcreteAttributeTransformationDescriptionBuilder(this, registry);
    }

    @Override
    public ResourceTransformationDescriptionBuilder discardOperations(String... operationNames) {
        Collections.addAll(discardedOperations, operationNames);
        return this;
    }
}
