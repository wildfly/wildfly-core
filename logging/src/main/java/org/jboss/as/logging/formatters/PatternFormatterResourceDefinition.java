/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.formatters;

import static org.jboss.as.logging.Logging.createOperationFailure;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.LoggingOperations.LoggingWriteAttributeHandler;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.validators.RegexValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.formatters.PatternFormatter;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PatternFormatterResourceDefinition extends SimpleResourceDefinition {

    private static final String COLOR_MAP_VALIDATION_PATTERN = "^((severe|fatal|error|warn|warning|info|debug|trace|config|fine|finer|finest|):(clear|black|green|red|yellow|blue|magenta|cyan|white|brightblack|brightred|brightgreen|brightblue|brightyellow|brightmagenta|brightcyan|brightwhite|)(,(?!$)|$))*$";

    public static final String NAME = "pattern-formatter";

    public static final String DEFAULT_FORMATTER_SUFFIX = "-wfcore-pattern-formatter";

    public static String getDefaultFomatterName(String name) {
        return name + DEFAULT_FORMATTER_SUFFIX;
    }

    // Pattern formatter options
    public static final PropertyAttributeDefinition COLOR_MAP = PropertyAttributeDefinition.Builder.of("color-map", ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(false)
            .setPropertyName("colors")
            .setValidator(new RegexValidator(ModelType.STRING, true, true, COLOR_MAP_VALIDATION_PATTERN))
            .build();

    public static final PropertyAttributeDefinition PATTERN = PropertyAttributeDefinition.Builder.of("pattern", ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n"))
            .build();

    public static final ObjectTypeAttributeDefinition PATTERN_FORMATTER = ObjectTypeAttributeDefinition.Builder.of(NAME, PATTERN, COLOR_MAP)
            .setAllowExpression(false)
            .setRequired(false)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    // We always want to marshal the element
                    writer.writeStartElement(attribute.getXmlName());
                    // We also need to always marshal the pattern has it's a required attribute in the XML.
                    final String pattern;
                    if (resourceModel.hasDefined(PATTERN.getName())) {
                        pattern = resourceModel.get(PATTERN.getName()).asString();
                    } else {
                        pattern = PATTERN.getDefaultValue().asString();
                    }
                    writer.writeAttribute(PATTERN.getXmlName(), pattern);
                    // Only marshal the color-map if defined as this is a newer attribute.
                    if (resourceModel.hasDefined(COLOR_MAP.getName())) {
                        final String colorMap = resourceModel.get(COLOR_MAP.getName()).asString();
                        writer.writeAttribute(COLOR_MAP.getXmlName(), colorMap);
                    }
                    writer.writeEndElement();
                }
            })
            .build();

    private static final PathElement PATH = PathElement.pathElement(NAME);

    private static final PropertyAttributeDefinition[] ATTRIBUTES = {
            COLOR_MAP,
            PATTERN,
    };


    /**
     * A step handler to add a pattern formatter
     */
    private static final OperationStepHandler ADD = new LoggingOperations.LoggingAddOperationStepHandler(ATTRIBUTES) {

        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final String name = context.getCurrentAddressValue();
            if (name.endsWith(DEFAULT_FORMATTER_SUFFIX)) {
                throw LoggingLogger.ROOT_LOGGER.illegalFormatterName();
            }
            FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(name);
            if (configuration == null) {
                LoggingLogger.ROOT_LOGGER.tracef("Adding formatter '%s' at '%s'", name, context.getCurrentAddress());
                configuration = logContextConfiguration.addFormatterConfiguration(null, PatternFormatter.class.getName(), name);
            }

            for (PropertyAttributeDefinition attribute : ATTRIBUTES) {
                attribute.setPropertyValue(context, model, configuration);
            }
        }
    };

    private static final OperationStepHandler WRITE = new LoggingWriteAttributeHandler(ATTRIBUTES) {

        @Override
        protected boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName, final ModelNode value, final LogContextConfiguration logContextConfiguration) {
            final FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(addressName);
            for (PropertyAttributeDefinition attribute : ATTRIBUTES) {
                if (attribute.getName().equals(attributeName)) {
                    configuration.setPropertyValueString(attribute.getPropertyName(), value.asString());
                    break;
                }
            }
            return false;
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

    public static final PatternFormatterResourceDefinition INSTANCE = new PatternFormatterResourceDefinition();

    public PatternFormatterResourceDefinition() {
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
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            // do nothing by default
        }
    }
}
