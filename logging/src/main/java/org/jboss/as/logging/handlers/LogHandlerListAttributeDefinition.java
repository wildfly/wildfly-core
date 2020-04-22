/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.handlers;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleListAttributeDefinition;
import org.jboss.as.logging.ConfigurationProperty;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.resolvers.HandlerResolver;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.PropertyConfigurable;

/**
 * Date: 13.10.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogHandlerListAttributeDefinition extends SimpleListAttributeDefinition implements ConfigurationProperty<Set<String>> {
    private final String propertyName;
    private final HandlerResolver resolver = HandlerResolver.INSTANCE;

    private LogHandlerListAttributeDefinition(final Builder builder, final AttributeDefinition valueType) {
        super(builder, valueType);
        this.propertyName = builder.propertyName;
    }

    @Override
    public ModelNodeResolver<Set<String>> resolver() {
        return resolver;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public Set<String> resolvePropertyValue(final OperationContext context, final ModelNode model) throws OperationFailedException {
        Set<String> result = Collections.emptySet();
        final ModelNode value = resolveModelAttribute(context, model);
        if (value.isDefined()) {
            result = resolver.resolveValue(context, value);
        }
        return result;
    }

    @Override
    public void setPropertyValue(final OperationContext context, final ModelNode model, final PropertyConfigurable configuration) {
        throw LoggingLogger.ROOT_LOGGER.unsupportedMethod("setPropertyValue", getClass().getName());
    }

    public static class Builder extends ListAttributeDefinition.Builder<Builder, LogHandlerListAttributeDefinition> {

        private final AttributeDefinition valueType;
        private String propertyName;


        Builder(final AttributeDefinition valueType, final String name) {
            super(name);
            this.valueType = valueType;
            setElementValidator(valueType.getValidator());
        }

        /**
         * Creates a builder for {@link LogHandlerListAttributeDefinition}.
         *
         * @param name the name of the attribute
         *
         * @return the builder
         */
        public static Builder of(final String name, final AttributeDefinition valueType) {
            return new Builder(valueType, name);
        }

        public LogHandlerListAttributeDefinition build() {
            if (propertyName == null) propertyName = getName();
            if (getAttributeMarshaller() == null) {
                setAttributeMarshaller(new HandlersAttributeMarshaller(valueType));
            }
            return new LogHandlerListAttributeDefinition(this, valueType);
        }

        public Builder setPropertyName(final String propertyName) {
            this.propertyName = propertyName;
            return this;
        }
    }
}
