/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.version.Stability;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ResourceTransformationDescriptionBuilderImpl extends AbstractTransformationDescriptionBuilder implements ResourceTransformationDescriptionBuilder {

    private final List<String> discardedOperations = new LinkedList<String>();
    private DiscardPolicy discardPolicy = DiscardPolicy.NEVER;
    private final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry registry = new AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry();

    protected ResourceTransformationDescriptionBuilderImpl(Stability stability, final PathElement pathElement) {
        this(stability, pathElement, PathAddressTransformer.DEFAULT);
    }

    protected ResourceTransformationDescriptionBuilderImpl(Stability stability, final PathElement pathElement, final PathAddressTransformer pathAddressTransformer) {
        this(stability, pathElement, pathAddressTransformer, null);
    }

    protected ResourceTransformationDescriptionBuilderImpl(Stability stability, PathElement pathElement, DynamicDiscardPolicy dynamicDiscardPolicy) {
        this(stability, pathElement, PathAddressTransformer.DEFAULT, dynamicDiscardPolicy);
    }

    protected ResourceTransformationDescriptionBuilderImpl(Stability stability, final PathElement pathElement,
            final PathAddressTransformer pathAddressTransformer, DynamicDiscardPolicy dynamicDiscardPolicy) {
        super(stability, pathElement, pathAddressTransformer, ResourceTransformer.DEFAULT, OperationTransformer.DEFAULT,
                dynamicDiscardPolicy);
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildResource(ResourceRegistration registration, DynamicDiscardPolicy dynamicDiscardPolicy) {
        final ResourceTransformationDescriptionBuilderImpl builder = new ResourceTransformationDescriptionBuilderImpl(this.getStability(), registration.getPathElement(), dynamicDiscardPolicy);
        if (this.enables(registration)) {
            this.children.add(builder);
        }
        return builder;
    }

    @Override
    public DiscardTransformationDescriptionBuilder discardChildResource(ResourceRegistration registration) {
        final DiscardTransformationDescriptionBuilder builder = new DiscardTransformationDescriptionBuilder(this.getStability(), registration.getPathElement());
        if (this.enables(registration)) {
            this.children.add(builder);
        }
        return builder;
    }

    @Override
    public RejectTransformationDescriptionBuilder rejectChildResource(ResourceRegistration registration) {
        final RejectTransformationDescriptionBuilder builder = new RejectTransformationDescriptionBuilder(this.getStability(), registration.getPathElement());
        if (this.enables(registration)) {
            this.children.add(builder);
        }
        return builder;
    }

    @Override
    public ResourceTransformationDescriptionBuilder addChildRedirection(ResourceRegistration registration, PathAddressTransformer pathAddressTransformer, DynamicDiscardPolicy dynamicDiscardPolicy) {
        final ResourceTransformationDescriptionBuilderImpl builder = new ResourceTransformationDescriptionBuilderImpl(this.getStability(), registration.getPathElement(), pathAddressTransformer, dynamicDiscardPolicy);
        if (this.enables(registration)) {
            this.children.add(builder);
        }
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
