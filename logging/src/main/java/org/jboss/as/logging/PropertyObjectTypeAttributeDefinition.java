/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import org.jboss.as.controller.AbstractAttributeDefinitionBuilder;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.operations.validation.ObjectTypeValidator;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.PropertyConfigurable;

/**
 * Date: 15.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PropertyObjectTypeAttributeDefinition extends ObjectTypeAttributeDefinition implements ConfigurationProperty<String> {
    private final ModelNodeResolver<String> resolver;
    private final String propertyName;

    private PropertyObjectTypeAttributeDefinition(final Builder builder) {
        super(builder, builder.suffix, builder.valueTypes);
        this.propertyName = builder.propertyName;
        this.resolver = builder.resolver;
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
    public void setPropertyValue(final OperationContext context, final ModelNode model, final PropertyConfigurable configuration) throws OperationFailedException {
        final String value = resolvePropertyValue(context, model);
        if (value == null) {
            configuration.removeProperty(propertyName);
        } else {
            configuration.setPropertyValueString(propertyName, value);
        }
    }

    public static class Builder extends AbstractAttributeDefinitionBuilder<Builder, PropertyObjectTypeAttributeDefinition> {
        private ModelNodeResolver<String> resolver;
        private String propertyName;
        private String suffix;
        private final AttributeDefinition[] valueTypes;

        public Builder(final String name, final AttributeDefinition... valueTypes) {
            super(name, ModelType.OBJECT, true);
            this.valueTypes = valueTypes;
        }

        public static Builder of(final String name, final AttributeDefinition... valueTypes) {
            return new Builder(name, valueTypes);
        }

        @Override
        public PropertyObjectTypeAttributeDefinition build() {
            if (getValidator() == null) {
                setValidator(new ObjectTypeValidator(isNillable(), valueTypes));
            }
            return new PropertyObjectTypeAttributeDefinition(this);
        }

        public Builder setSuffix(final String suffix) {
            this.suffix = suffix;
            return this;
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
