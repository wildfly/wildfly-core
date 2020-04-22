/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.filters;

import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.LoggingOperations.LoggingWriteAttributeHandler;
import org.jboss.as.logging.PropertyAttributeMarshaller;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logmanager.config.FilterConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * The resource definition for {@code /subsystem=logging/filter=*}.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FilterResourceDefinition extends TransformerResourceDefinition {
    public static final String NAME = "filter";

    public static final SimpleMapAttributeDefinition CONSTRUCTOR_PROPERTIES = new SimpleMapAttributeDefinition.Builder("constructor-properties", true)
            .setAllowExpression(true)
            .setAttributeMarshaller(PropertyAttributeMarshaller.INSTANCE)
            .setXmlName("constructor-properties")
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = {
            CLASS,
            MODULE,
            CONSTRUCTOR_PROPERTIES,
            PROPERTIES,
    };


    /**
     * A step handler to add a custom filter
     */
    private static final OperationStepHandler ADD = new LoggingOperations.LoggingAddOperationStepHandler(ATTRIBUTES) {
        private final List<String> reservedNames = Arrays.asList(
                "accept",
                "deny",
                "not",
                "all",
                "any",
                "levelChange",
                "levels",
                "levelRange",
                "match",
                "substitute",
                "substituteAll"
        );

        @Override
        protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
            // Check the name isn't a reserved filter name
            final String name = context.getCurrentAddressValue();
            if (reservedNames.contains(name)) {
                throw LoggingLogger.ROOT_LOGGER.reservedFilterName(name, reservedNames);
            }
            // Check the name has no special characters
            if (!Character.isJavaIdentifierStart(name.charAt(0))) {
                throw LoggingLogger.ROOT_LOGGER.invalidFilterNameStart(name, name.charAt(0));
            }
            for (char c : name.toCharArray()) {
                if (!Character.isJavaIdentifierPart(c)) {
                    throw LoggingLogger.ROOT_LOGGER.invalidFilterName(name, c);
                }
            }
            super.populateModel(context, operation, resource);
        }

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            FilterConfiguration configuration = logContextConfiguration.getFilterConfiguration(name);
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final ModelNode moduleNameNode = MODULE.resolveModelAttribute(context, model);
            final String moduleName = moduleNameNode.isDefined() ? moduleNameNode.asString() : null;
            final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
            final ModelNode constructorProperties = CONSTRUCTOR_PROPERTIES.resolveModelAttribute(context, model);

            final Map<String, String> constProps = new LinkedHashMap<>();
            if (constructorProperties.isDefined()) {
                for (Property property : constructorProperties.asPropertyList()) {
                    constProps.put(property.getName(), property.getValue().asString());
                }
            }

            boolean replaceConfiguration = false;
            if (configuration != null) {
                if (!className.equals(configuration.getClassName()) || (moduleName == null ? configuration.getModuleName() != null : !moduleName.equals(configuration.getModuleName()))) {
                    replaceConfiguration = true;
                }
                final List<String> configuredConstProps = configuration.getConstructorProperties();
                for (Map.Entry<String, String> entry : constProps.entrySet()) {
                    if (configuredConstProps.contains(entry.getKey())) {
                        if (!configuration.getPropertyValueString(entry.getKey()).equals(entry.getValue())) {
                            replaceConfiguration = true;
                            break;
                        }
                    } else {
                        replaceConfiguration = true;
                        break;
                    }
                }
            } else {
                LoggingLogger.ROOT_LOGGER.tracef("Adding filter '%s' at '%s'", name, context.getCurrentAddress());
                configuration = logContextConfiguration.addFilterConfiguration(moduleName, className, name, constProps.keySet().toArray(new String[0]));
            }
            if (replaceConfiguration) {
                LoggingLogger.ROOT_LOGGER.tracef("Replacing filter '%s' at '%s'", name, context.getCurrentAddress());
                logContextConfiguration.removeFilterConfiguration(name);
                configuration = logContextConfiguration.addFilterConfiguration(moduleName, className, name, constProps.keySet().toArray(new String[0]));
            }
            for (Map.Entry<String, String> entry : constProps.entrySet()) {
                configuration.setPropertyValueString(entry.getKey(), entry.getValue());
            }
            if (properties.isDefined()) {
                for (Property property : properties.asPropertyList()) {
                    configuration.setPropertyValueString(property.getName(), property.getValue().asString());
                }
            }
        }
    };

    private static final OperationStepHandler WRITE = new LoggingWriteAttributeHandler(ATTRIBUTES) {

        @Override
        protected boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName, final ModelNode value, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final FilterConfiguration configuration = logContextConfiguration.getFilterConfiguration(addressName);
            String modelClass = CLASS.resolveModelAttribute(context, context.readResource(PathAddress.EMPTY_ADDRESS).getModel()).asString();
            if (PROPERTIES.getName().equals(attributeName) && configuration.getClassName().equals(modelClass)) {
                if (value.isDefined()) {
                    for (Property property : value.asPropertyList()) {
                        configuration.setPropertyValueString(property.getName(), property.getValue().asString());
                    }
                } else {
                    // Remove all current properties
                    final List<String> names = configuration.getPropertyNames();
                    for (String name : names) {
                        configuration.removeProperty(name);
                    }
                }
            }

            // Writing a class attribute or module will require the previous filter to be removed and a new filter
            // added. This also would require each logger or handler that has the filter assigned to reassign the
            // filter. The configuration API does not handle this so a reload will be required.
            return CLASS.getName().equals(attributeName) || MODULE.getName().equals(attributeName) ||
                    CONSTRUCTOR_PROPERTIES.getName().equals(attributeName);
        }
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new LoggingOperations.LoggingRemoveOperationStepHandler() {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final FilterConfiguration configuration = logContextConfiguration.getFilterConfiguration(name);
            if (configuration == null) {
                throw LoggingLogger.ROOT_LOGGER.filterNotFound(name);
            }
            logContextConfiguration.removeFilterConfiguration(name);
        }
    };

    public static final FilterResourceDefinition INSTANCE = new FilterResourceDefinition();

    private FilterResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .addCapabilities(Capabilities.FILTER_CAPABILITY)
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, WRITE);
        }
    }

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        if (modelVersion == KnownModelVersion.VERSION_8_0_0) {
            rootResourceBuilder.rejectChildResource(getPathElement());
            loggingProfileBuilder.rejectChildResource(getPathElement());
        }
    }
}
