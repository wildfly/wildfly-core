/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.logmanager.config.PropertyConfigurable;

/**
 * Defines an attribute with a property name.
 */
public class PropertyAttributeDefinition extends SimpleAttributeDefinition implements ConfigurationProperty<String> {
    private final ModelNodeResolver<String> resolver;
    private final String propertyName;

    private PropertyAttributeDefinition(final Builder builder) {
        super(builder);
        this.propertyName = builder.propertyName;
        this.resolver = builder.resolver;
    }

    @Override
    public void setPropertyValue(final OperationContext context, final ModelNode model, final PropertyConfigurable configuration) throws OperationFailedException {
        final String value = resolvePropertyValue(context, model);
        if (value == null) {
            configuration.removeProperty(propertyName);
        } else {
            // Only change the property if the two resolved values do not match. We currently don't set expressions.
            final String currentValue = configuration.getPropertyValueString(propertyName);
            if (!value.equals(currentValue)) {
                configuration.setPropertyValueString(propertyName, value);
            }
        }
    }

    @Override
    public ModelNodeResolver<String> resolver() {
        return resolver;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public String resolvePropertyValue(final OperationContext context, final ModelNode model) throws OperationFailedException {
        String result = null;
        final ModelNode value = resolveModelAttribute(context, model);
        if (value.isDefined()) {
            if (resolver == null) {
                result = value.asString();
            } else {
                result = resolver.resolveValue(context, value);
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int hash = 17;
        hash = prime * hash + (propertyName == null ? 0 : propertyName.hashCode());
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PropertyAttributeDefinition)) {
            return false;
        }
        final PropertyAttributeDefinition other = (PropertyAttributeDefinition) obj;
        return (propertyName == null ? other.propertyName == null : propertyName.equals(other.propertyName));
    }

    @Override
    public String toString() {
        return String.format("%s{propertyName=%s,attributeName=%s}", getClass().getName(), propertyName, getName());
    }

    public static class Builder extends AbstractAttributeDefinitionBuilder<Builder, PropertyAttributeDefinition> {

        private ModelNodeResolver<String> resolver;
        private String propertyName;

        Builder(final String name, final ModelType type) {
            super(name, type);
        }

        /**
         * Creates a builder for {@link PropertyAttributeDefinition}.
         *
         * @param name the name of the attribute
         * @param type the attribute type
         *
         * @return a builder
         */
        public static Builder of(final String name, final ModelType type) {
            return new Builder(name, type);
        }

        /**
         * Creates a builder for {@link PropertyAttributeDefinition}.
         *
         * @param name      the name of the attribute
         * @param type      the attribute type
         * @param allowNull {@code true} if {@code null} is allowed, otherwise {@code false}
         *
         * @return a builder
         */
        public static Builder of(final String name, final ModelType type, final boolean allowNull) {
            return new Builder(name, type).setRequired(!allowNull);
        }

        public PropertyAttributeDefinition build() {
            if (propertyName == null) propertyName = getName();
            return new PropertyAttributeDefinition(this);
        }

        public Builder setPropertyName(final String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        public Builder setResolver(final ModelNodeResolver<String> resolver) {
            this.resolver = resolver;
            return this;
        }
    }
}
