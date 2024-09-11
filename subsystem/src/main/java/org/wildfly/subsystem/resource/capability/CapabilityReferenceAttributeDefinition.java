/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import java.util.Map;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.subsystem.resource.SimpleResource;
import org.wildfly.subsystem.resource.ResourceModelResolver;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * An attribute definition referencing some capability.
 * Resolves directly to a {@link ServiceDependency} via {@link #resolve(OperationContext, ModelNode)}.
 */
public class CapabilityReferenceAttributeDefinition<T> extends SimpleAttributeDefinition implements ResourceModelResolver<ServiceDependency<T>> {

    private final CapabilityReferenceResolver<T> resolver;

    CapabilityReferenceAttributeDefinition(Builder<T> builder) {
        super(builder);
        this.resolver = builder.resolver;
    }

    @Override
    public ServiceDependency<T> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        String value = this.resolveModelAttribute(context, model).asStringOrNull();
        Map.Entry<String, String[]> resolved = this.resolver.resolve(context, new SimpleResource(model), value);
        return (resolved != null) ? ServiceDependency.on(resolved.getKey(), this.resolver.getRequirement().getType(), resolved.getValue()) : ServiceDependency.empty();
    }

    public static class Builder<T> extends AbstractAttributeDefinitionBuilder<Builder<T>, CapabilityReferenceAttributeDefinition<T>> {

        final CapabilityReferenceResolver<T> resolver;

        public Builder(String attributeName, CapabilityReference<T> reference) {
            super(attributeName, ModelType.STRING);
            // Capability references never allow expressions
            this.setAllowExpression(false);
            this.setAttributeParser(AttributeParser.SIMPLE);
            this.setCapabilityReference(reference);
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.setValidator(new StringLengthValidator(1));
            this.resolver = reference;
        }

        public Builder(String attributeName, CapabilityReferenceAttributeDefinition<T> basis) {
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
        public CapabilityReferenceAttributeDefinition<T> build() {
            return new CapabilityReferenceAttributeDefinition<>(this);
        }
    }
}
