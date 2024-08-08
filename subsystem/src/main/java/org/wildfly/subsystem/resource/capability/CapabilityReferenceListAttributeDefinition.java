/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource.capability;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * An list attribute definition referencing a capability.
 */
public class CapabilityReferenceListAttributeDefinition<T> extends PrimitiveListAttributeDefinition implements NaryCapabilityReferenceAttributeDefinitionProvider<T> {

    private final CapabilityReferenceResolver<T> resolver;

    CapabilityReferenceListAttributeDefinition(Builder<T> builder) {
        super(builder, ModelType.STRING);
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

    public static class Builder<T> extends ListAttributeDefinition.Builder<Builder<T>, CapabilityReferenceListAttributeDefinition<T>> {

        final CapabilityReferenceResolver<T> resolver;

        public Builder(String attributeName, CapabilityReference<T> reference) {
            super(attributeName);
            // Capability references never allow expressions
            this.setAllowExpression(false);
            this.setAttributeMarshaller(AttributeMarshaller.STRING_LIST);
            this.setAttributeParser(AttributeParser.STRING_LIST);
            this.setCapabilityReference(reference);
            this.setElementValidator(new ModelTypeValidator(ModelType.STRING));
            this.setFlags(Flag.RESTART_RESOURCE_SERVICES);
            this.resolver = reference;
        }

        public Builder(String attributeName, CapabilityReferenceListAttributeDefinition<T> basis) {
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
        public CapabilityReferenceListAttributeDefinition<T> build() {
            return new CapabilityReferenceListAttributeDefinition<>(this);
        }
    }
}
