/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.formatters;

import static org.jboss.as.logging.CommonAttributes.CLASS;
import static org.jboss.as.logging.CommonAttributes.MODULE;
import static org.jboss.as.logging.CommonAttributes.PROPERTIES;
import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.LoggingOperations.LoggingWriteAttributeHandler;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class CustomFormatterResourceDefinition extends SimpleResourceDefinition {
    public static final String NAME = "custom-formatter";

    public static final ObjectTypeAttributeDefinition CUSTOM_FORMATTER = ObjectTypeAttributeDefinition.Builder.of(NAME, CLASS, MODULE, PROPERTIES)
            .setAllowExpression(false)
            .setRequired(false)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        MODULE.marshallAsAttribute(resourceModel, writer);
                        CLASS.marshallAsAttribute(resourceModel, writer);
                        if (resourceModel.hasDefined(PROPERTIES.getName())) {
                            PROPERTIES.marshallAsElement(resourceModel, writer);
                        }
                        writer.writeEndElement();
                    }
                }

                @Override
                public boolean isMarshallable(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault) {
                    return resourceModel.hasDefined(CLASS.getName());
                }
            })
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final AttributeDefinition[] ATTRIBUTES = {
            CLASS,
            MODULE,
            PROPERTIES
    };


    /**
     * A step handler to add a custom formatter
     */
    private static final OperationStepHandler ADD = new LoggingOperations.LoggingAddOperationStepHandler(ATTRIBUTES) {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(name);
            final String className = CLASS.resolveModelAttribute(context, model).asString();
            final ModelNode moduleNameNode = MODULE.resolveModelAttribute(context, model);
            final String moduleName = moduleNameNode.isDefined() ? moduleNameNode.asString() : null;
            final ModelNode properties = PROPERTIES.resolveModelAttribute(context, model);
            if (configuration != null) {
                if (!className.equals(configuration.getClassName()) || (moduleName == null ? configuration.getModuleName() != null : !moduleName.equals(configuration.getModuleName()))) {
                    LoggingLogger.ROOT_LOGGER.tracef("Replacing formatter '%s' at '%s'", name, context.getCurrentAddress());
                    logContextConfiguration.removeFormatterConfiguration(name);
                    configuration = logContextConfiguration.addFormatterConfiguration(moduleName, className, name);
                }
            } else {
                LoggingLogger.ROOT_LOGGER.tracef("Adding formatter '%s' at '%s'", name, context.getCurrentAddress());
                configuration = logContextConfiguration.addFormatterConfiguration(moduleName, className, name);
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
            final FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(addressName);
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

            // Writing a class attribute or module will require the previous formatter to be removed and a new formatter
            // added. It's best to require a restart.
            return CLASS.getName().equals(attributeName) || MODULE.getName().equals(attributeName);
        }
    };

    /**
     * A step handler to remove
     */
    private static final OperationStepHandler REMOVE = new LoggingOperations.LoggingRemoveOperationStepHandler() {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            final FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(name);
            if (configuration == null) {
                throw createOperationFailure(LoggingLogger.ROOT_LOGGER.formatterNotFound(name));
            }
            logContextConfiguration.removeFormatterConfiguration(name);
        }
    };

    public static final CustomFormatterResourceDefinition INSTANCE = new CustomFormatterResourceDefinition();

    private CustomFormatterResourceDefinition() {
        super(new Parameters(PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setCapabilities(Capabilities.FORMATTER_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, WRITE);
        }
    }

    public static final class TransformerDefinition extends TransformerResourceDefinition {

        public TransformerDefinition() {
            super(PATH);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }
}
