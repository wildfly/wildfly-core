/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.RequirementServiceBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.SimpleResource;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * A list attribute definition whose elements (of type {@link ModelType#STRING}) reference a capability.
 * Resolves directly to a {@link ServiceDependency} via {@link #resolve(OperationContext, ModelNode)}.
 */
public class CapabilityReferenceListAttributeDefinition<T> extends StringListAttributeDefinition implements ResourceModelResolver<ServiceDependency<List<T>>> {

    private final CapabilityReferenceResolver<T> resolver;

    CapabilityReferenceListAttributeDefinition(Builder<T> builder) {
        super(builder);
        this.resolver = builder.resolver;
    }

    @Override
    public ServiceDependency<List<T>> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        List<ModelNode> values = this.resolveModelAttribute(context, model).asListOrEmpty();
        if (!values.isEmpty()) {
            List<ServiceDependency<T>> dependencies = new ArrayList<>(values.size());
            for (ModelNode value : values) {
                Map.Entry<String, String[]> resolved = this.resolver.resolve(context, new SimpleResource(model), value.asString());
                if (resolved != null) {
                    dependencies.add(ServiceDependency.on(resolved.getKey(), this.resolver.getRequirement().getType(), resolved.getValue()));
                }
            }
            if (!dependencies.isEmpty()) {
                return new ServiceDependency<>() {
                    @Override
                    public void accept(RequirementServiceBuilder<?> builder) {
                        for (ServiceDependency<T> dependency : dependencies) {
                            dependency.accept(builder);
                        }
                    }

                    @Override
                    public List<T> get() {
                        List<T> values = new ArrayList<>();
                        for (ServiceDependency<T> dependency : dependencies) {
                            values.add(dependency.get());
                        }
                        return values;
                    }
                };
            }
        }
        return ServiceDependency.of(List.of());
    }

    public static class Builder<T> extends ListAttributeDefinition.Builder<Builder<T>, CapabilityReferenceListAttributeDefinition<T>> {

        final CapabilityReferenceResolver<T> resolver;

        public Builder(String attributeName, CapabilityReference<T> reference) {
            super(attributeName);
            this.setAttributeParser(AttributeParser.STRING_LIST);
            this.setAttributeMarshaller(AttributeMarshaller.STRING_LIST);
            this.setElementValidator(new ModelTypeValidator(ModelType.STRING));
            // Capability references never allow expressions
            this.setAllowExpression(false);
            this.setCapabilityReference(reference);
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.resolver = reference;
        }

        public Builder(String attributeName, CapabilityReferenceListAttributeDefinition<T> basis) {
            super(attributeName, basis);
            this.resolver = basis.resolver;
        }

        /**
         * Capability references should never define a default value.
         * @throws UnsupportedOperationException if caller attempts to define a default value for this attribute.
         */
        @Override
        public Builder<T> setDefaultValue(ModelNode defaultValue) {
            // A capability reference must not specify a default value
            if ((defaultValue != null) && defaultValue.isDefined()) {
                throw new UnsupportedOperationException();
            }
            return this;
        }

        /**
         * Capability references should never allow expressions.
         * @throws UnsupportedOperationException if caller attempts to enable expressions for this attribute.
         */
        @Override
        public Builder<T> setAllowExpression(boolean allowExpression) {
            // A capability reference must not allow expressions
            if (allowExpression) {
                throw new UnsupportedOperationException();
            }
            return this;
        }

        @Override
        public CapabilityReferenceListAttributeDefinition<T> build() {
            return new CapabilityReferenceListAttributeDefinition<>(this);
        }
    }
}
