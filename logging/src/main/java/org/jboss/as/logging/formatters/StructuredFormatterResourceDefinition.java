/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.formatters;

import static org.jboss.as.logging.Logging.createOperationFailure;

import java.util.Iterator;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.logmanager.PropertyValues;
import org.wildfly.core.logmanager.config.FormatterConfiguration;
import org.wildfly.core.logmanager.config.HandlerConfiguration;
import org.wildfly.core.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.formatters.StructuredFormatter;

/**
 * An abstract resource definition for {@link org.jboss.logmanager.formatters.StructuredFormatter}'s.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("Convert2Lambda")
public abstract class StructuredFormatterResourceDefinition extends SimpleResourceDefinition {

    public static final PropertyAttributeDefinition DATE_FORMAT = PropertyAttributeDefinition.Builder.of("date-format", ModelType.STRING, true)
            .setAllowExpression(true)
            .setPropertyName("dateFormat")
            .build();

    public static final PropertyAttributeDefinition EXCEPTION_OUTPUT_TYPE = PropertyAttributeDefinition.Builder.of("exception-output-type", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("detailed"))
            .setPropertyName("exceptionOutputType")
            .setResolver(new ModelNodeResolver<String>() {
                @Override
                public String resolveValue(final OperationContext context, final ModelNode value) throws OperationFailedException {
                    final String exceptionType = value.asString();
                    if ("detailed".equals(exceptionType)) {
                        return StructuredFormatter.ExceptionOutputType.DETAILED.name();
                    } else if ("formatted".equals(exceptionType)) {
                        return StructuredFormatter.ExceptionOutputType.FORMATTED.name();
                    } else if ("detailed-and-formatted".equals(exceptionType)) {
                        return StructuredFormatter.ExceptionOutputType.DETAILED_AND_FORMATTED.name();
                    }
                    // Should never be hit
                    throw LoggingLogger.ROOT_LOGGER.invalidExceptionOutputType(exceptionType);
                }
            })
            .setValidator(new StringAllowedValuesValidator("detailed", "formatted", "detailed-and-formatted"))
            .build();

    private static final SimpleAttributeDefinition EXCEPTION = SimpleAttributeDefinitionBuilder.create("exception", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_CAUSED_BY = SimpleAttributeDefinitionBuilder.create("exception-caused-by", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_CIRCULAR_REFERENCE = SimpleAttributeDefinitionBuilder.create("exception-circular-reference", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME = SimpleAttributeDefinitionBuilder.create("exception-frame", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_CLASS = SimpleAttributeDefinitionBuilder.create("exception-frame-class", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_LINE = SimpleAttributeDefinitionBuilder.create("exception-frame-line", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAME_METHOD = SimpleAttributeDefinitionBuilder.create("exception-frame-method", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_FRAMES = SimpleAttributeDefinitionBuilder.create("exception-frames", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_MESSAGE = SimpleAttributeDefinitionBuilder.create("exception-message", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_REFERENCE_ID = SimpleAttributeDefinitionBuilder.create("exception-reference-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_SUPPRESSED = SimpleAttributeDefinitionBuilder.create("exception-suppressed", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition EXCEPTION_TYPE = SimpleAttributeDefinitionBuilder.create("exception-type", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition HOST_NAME = SimpleAttributeDefinitionBuilder.create("host-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LEVEL = SimpleAttributeDefinitionBuilder.create("level", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LOGGER_CLASS_NAME = SimpleAttributeDefinitionBuilder.create("logger-class-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition LOGGER_NAME = SimpleAttributeDefinitionBuilder.create("logger-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition MDC = SimpleAttributeDefinitionBuilder.create("mdc", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition MESSAGE = SimpleAttributeDefinitionBuilder.create("message", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition NDC = SimpleAttributeDefinitionBuilder.create("ndc", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition PROCESS_ID = SimpleAttributeDefinitionBuilder.create("process-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition PROCESS_NAME = SimpleAttributeDefinitionBuilder.create("process-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition RECORD = SimpleAttributeDefinitionBuilder.create("record", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SEQUENCE = SimpleAttributeDefinitionBuilder.create("sequence", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_CLASS_NAME = SimpleAttributeDefinitionBuilder.create("source-class-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_FILE_NAME = SimpleAttributeDefinitionBuilder.create("source-file-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_LINE_NUMBER = SimpleAttributeDefinitionBuilder.create("source-line-number", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_METHOD_NAME = SimpleAttributeDefinitionBuilder.create("source-method-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_MODULE_NAME = SimpleAttributeDefinitionBuilder.create("source-module-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition SOURCE_MODULE_VERSION = SimpleAttributeDefinitionBuilder.create("source-module-version", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition STACK_TRACE = SimpleAttributeDefinitionBuilder.create("stack-trace", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition THREAD_ID = SimpleAttributeDefinitionBuilder.create("thread-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition THREAD_NAME = SimpleAttributeDefinitionBuilder.create("thread-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();
    private static final SimpleAttributeDefinition TIMESTAMP = SimpleAttributeDefinitionBuilder.create("timestamp", ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    public static final ObjectTypeAttributeDefinition KEY_OVERRIDES = ObjectTypeAttributeDefinition.create("key-overrides",
            EXCEPTION,
            EXCEPTION_CAUSED_BY,
            EXCEPTION_CIRCULAR_REFERENCE,
            EXCEPTION_FRAME,
            EXCEPTION_FRAME_CLASS,
            EXCEPTION_FRAME_LINE,
            EXCEPTION_FRAME_METHOD,
            EXCEPTION_FRAMES,
            EXCEPTION_MESSAGE,
            EXCEPTION_REFERENCE_ID,
            EXCEPTION_SUPPRESSED,
            EXCEPTION_TYPE,
            HOST_NAME,
            LEVEL,
            LOGGER_CLASS_NAME,
            LOGGER_NAME,
            MDC,
            MESSAGE,
            NDC,
            PROCESS_ID,
            PROCESS_NAME,
            RECORD,
            SEQUENCE,
            SOURCE_CLASS_NAME,
            SOURCE_FILE_NAME,
            SOURCE_LINE_NUMBER,
            SOURCE_METHOD_NAME,
            SOURCE_MODULE_NAME,
            SOURCE_MODULE_VERSION,
            STACK_TRACE,
            THREAD_ID,
            THREAD_NAME,
            TIMESTAMP
    )
            // This is done as the StructuredFormatter will need to be reconstructed even though there is no real
            // service. This could be done without requiring a restart, but making a change to a key name may not be
            // desired until both the target consumer of the log messages and the container are restarted.
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    public static final SimpleMapAttributeDefinition META_DATA = new SimpleMapAttributeDefinition.Builder("meta-data", ModelType.STRING, true)
            .setAttributeMarshaller(new AttributeMarshallers.PropertiesAttributeMarshaller("meta-data", "property", true))
            .build();

    public static final PropertyAttributeDefinition PRETTY_PRINT = PropertyAttributeDefinition.Builder.of("pretty-print", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setPropertyName("prettyPrint")
            .build();

    public static final PropertyAttributeDefinition PRINT_DETAILS = PropertyAttributeDefinition.Builder.of("print-details", ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setPropertyName("printDetails")
            .build();

    public static final PropertyAttributeDefinition RECORD_DELIMITER = PropertyAttributeDefinition.Builder.of("record-delimiter", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("\n"))
            .setPropertyName("recordDelimiter")
            .build();

    public static final PropertyAttributeDefinition ZONE_ID = PropertyAttributeDefinition.Builder.of("zone-id", ModelType.STRING, true)
            .setAllowExpression(true)
            .setPropertyName("zoneId")
            .build();

    private static final AttributeDefinition[] DEFAULT_ATTRIBUTES = {
            DATE_FORMAT,
            EXCEPTION_OUTPUT_TYPE,
            KEY_OVERRIDES,
            META_DATA,
            PRETTY_PRINT,
            PRINT_DETAILS,
            RECORD_DELIMITER,
            ZONE_ID,
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

    private final AttributeDefinition[] attributes;
    private final OperationStepHandler writeHandler;

    StructuredFormatterResourceDefinition(final PathElement pathElement, final String descriptionPrefix,
                                          final Class<? extends StructuredFormatter> type) {
        this(pathElement, descriptionPrefix, type, new AttributeDefinition[0]);
    }

    StructuredFormatterResourceDefinition(final PathElement pathElement, final String descriptionPrefix,
                                          final Class<? extends StructuredFormatter> type, final AttributeDefinition... additionalAttributes) {
        super(
                new Parameters(pathElement, LoggingExtension.getResourceDescriptionResolver(descriptionPrefix))
                        .setAddHandler(new AddStructuredFormatterStepHandler(type, Logging.join(DEFAULT_ATTRIBUTES, additionalAttributes)))
                        .setRemoveHandler(REMOVE)
                        .setCapabilities(Capabilities.FORMATTER_CAPABILITY)
        );
        attributes = Logging.join(DEFAULT_ATTRIBUTES, additionalAttributes);
        writeHandler = new WriteStructuredFormatterStepHandler(attributes);
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : attributes) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }

    @Override
    public void registerChildren(final ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
    }

    static class StructuredFormatterTransformerDefinition extends TransformerResourceDefinition {

        public StructuredFormatterTransformerDefinition(PathElement pathElement) {
            super(pathElement);
        }

        @Override
        public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
            switch (modelVersion) {
                case VERSION_5_0_0:
                    rootResourceBuilder.rejectChildResource(getPathElement());
                    loggingProfileBuilder.rejectChildResource(getPathElement());
                    break;
            }
        }
    }

    private static String modelValueToMetaData(final ModelNode metaData) {
        if (metaData.getType() != ModelType.OBJECT) {
            return null;
        }
        final List<Property> properties = metaData.asPropertyList();
        final StringBuilder result = new StringBuilder();
        final Iterator<Property> iterator = properties.iterator();
        while (iterator.hasNext()) {
            final Property property = iterator.next();
            PropertyValues.escapeKey(result, property.getName());
            result.append('=');
            final ModelNode value = property.getValue();
            if (value.isDefined()) {
                PropertyValues.escapeValue(result, value.asString());
            }
            if (iterator.hasNext()) {
                result.append(',');
            }
        }
        return result.toString();
    }

    private static class AddStructuredFormatterStepHandler extends LoggingOperations.LoggingAddOperationStepHandler {
        private final Class<? extends StructuredFormatter> type;

        private AddStructuredFormatterStepHandler(final Class<? extends StructuredFormatter> type, final AttributeDefinition[] attributes) {
            super(attributes);
            this.type = type;
        }

        @SuppressWarnings({"OverlyStrongTypeCast", "StatementWithEmptyBody"})
        @Override
        public void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            String keyOverrides = "";
            if (model.hasDefined(KEY_OVERRIDES.getName())) {
                keyOverrides = modelValueToMetaData(KEY_OVERRIDES.resolveModelAttribute(context, model));
            }

            final String name = context.getCurrentAddressValue();
            if (name.endsWith(PatternFormatterResourceDefinition.DEFAULT_FORMATTER_SUFFIX)) {
                throw LoggingLogger.ROOT_LOGGER.illegalFormatterName();
            }
            FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(name);
            final String className = type.getName();

            if (configuration == null) {
                LoggingLogger.ROOT_LOGGER.tracef("Adding formatter '%s' at '%s'", name, context.getCurrentAddress());
                if (keyOverrides == null) {
                    configuration = logContextConfiguration.addFormatterConfiguration(null, className, name);
                } else {
                    configuration = logContextConfiguration.addFormatterConfiguration(null, className, name, "keyOverrides");
                    configuration.setPropertyValueString("keyOverrides", keyOverrides);
                }
            } else if (!isSamePropertyValue(configuration, "keyOverrides", keyOverrides)) {
                LoggingLogger.ROOT_LOGGER.tracef("Removing then adding formatter '%s' at '%s'", name, context.getCurrentAddress());
                logContextConfiguration.removeFormatterConfiguration(name);
                configuration = logContextConfiguration.addFormatterConfiguration(null, className, name, "keyOverrides");
                configuration.setPropertyValueString("keyOverrides", keyOverrides);
                // We need to trigger a re-add of the formatter to the handlers
                logContextConfiguration.getHandlerNames().forEach(handlerName -> {
                    final HandlerConfiguration handlerConfiguration = logContextConfiguration.getHandlerConfiguration(handlerName);
                    handlerConfiguration.setFormatterName(name);
                });
            }

            // Process the attributes
            for (AttributeDefinition attribute : attributes) {
                if (attribute == META_DATA) {
                    final String metaData = modelValueToMetaData(META_DATA.resolveModelAttribute(context, model));
                    if (metaData != null) {
                        if (!isSamePropertyValue(configuration, "metaData", metaData)) {
                            configuration.setPropertyValueString("metaData", metaData);
                        }
                    } else {
                        configuration.removeProperty("metaData");
                    }
                } else if (attribute == KEY_OVERRIDES) {
                    // Ignore the key-overrides as it was already taken care of
                } else {
                    if (attribute instanceof PropertyAttributeDefinition) {
                        ((PropertyAttributeDefinition) attribute).setPropertyValue(context, model, configuration);
                    } else {
                        final ModelNode value = attribute.resolveModelAttribute(context, model);
                        if (value.isDefined()) {
                            if (!isSamePropertyValue(configuration, attribute.getName(), value.asString())) {
                                configuration.setPropertyValueString(attribute.getName(), value.asString());
                            }
                        } else {
                            configuration.removeProperty(attribute.getName());
                        }
                    }
                }
            }
        }

        private static boolean isSamePropertyValue(final FormatterConfiguration configuration, final String name, final String value) {
            final String currentValue = configuration.getPropertyValueString(name);
            if (currentValue == null) {
                return value == null;
            }
            return currentValue.equals(value);
        }
    }

    private static class WriteStructuredFormatterStepHandler extends LoggingOperations.LoggingWriteAttributeHandler {

        WriteStructuredFormatterStepHandler(final AttributeDefinition[] attributes) {
            super(attributes);
        }

        @Override
        protected boolean applyUpdate(final OperationContext context, final String attributeName, final String addressName,
                                      final ModelNode value, final LogContextConfiguration logContextConfiguration) throws OperationFailedException {
            final FormatterConfiguration configuration = logContextConfiguration.getFormatterConfiguration(addressName);
            if (attributeName.equals(META_DATA.getName())) {
                final String metaData = modelValueToMetaData(value);
                if (metaData != null) {
                    configuration.setPropertyValueString("metaData", metaData);
                } else {
                    configuration.removeProperty("metaData");
                }
            } else if (attributeName.equals(KEY_OVERRIDES.getName())) {
                // Require a restart of the resource
                return true;
            } else {
                for (AttributeDefinition attribute : DEFAULT_ATTRIBUTES) {
                    if (attribute.getName().equals(attributeName)) {
                        if (attribute instanceof PropertyAttributeDefinition) {
                            final PropertyAttributeDefinition propertyAttribute = (PropertyAttributeDefinition) attribute;
                            if (value.isDefined()) {
                                final ModelNodeResolver<String> resolver = propertyAttribute.resolver();
                                String resolvedValue = value.asString();
                                if (resolver != null) {
                                    resolvedValue = resolver.resolveValue(context, value);
                                }
                                configuration.setPropertyValueString(propertyAttribute.getPropertyName(), resolvedValue);
                            } else {
                                configuration.removeProperty(propertyAttribute.getPropertyName());
                            }
                        } else {
                            if (value.isDefined()) {
                                configuration.setPropertyValueString(attributeName, value.asString());
                            } else {
                                configuration.removeProperty(attributeName);
                            }
                        }
                        break;
                    }
                }
            }
            return false;
        }
    }
}
