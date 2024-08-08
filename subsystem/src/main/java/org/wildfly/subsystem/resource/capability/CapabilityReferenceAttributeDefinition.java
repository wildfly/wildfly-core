/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An attribute definition referencing some capability.
 */
public class CapabilityReferenceAttributeDefinition<T> extends SimpleAttributeDefinition implements UnaryCapabilityReferenceAttributeDefinitionProvider<T> {

    private final CapabilityReferenceResolver<T> resolver;

    CapabilityReferenceAttributeDefinition(Builder<T> builder) {
        super(builder);
        this.resolver = builder.resolver;
    }

    @Override
    public AttributeDefinition get() {
        return this;
    }

    @Override
    public CapabilityReferenceResolver<T> getCapabilityReferenceResolver() {
        return this.resolver;
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

        @Override
        public Builder<T> setDefaultValue(ModelNode defaultValue) {
            // A capability reference must not specify a default value
            if ((defaultValue != null) && defaultValue.isDefined()) {
                throw new UnsupportedOperationException();
            }
            return this;
        }

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
