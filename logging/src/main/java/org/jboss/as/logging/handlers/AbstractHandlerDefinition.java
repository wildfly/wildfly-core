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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DISABLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLE;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.ENCODING;
import static org.jboss.as.logging.CommonAttributes.FILTER;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.NAME;

import java.util.logging.Handler;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReadResourceNameOperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.ConfigurationProperty;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.LoggingOperations;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.logmanager.PropertySorter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public abstract class AbstractHandlerDefinition extends TransformerResourceDefinition {

    public static final String UPDATE_OPERATION_NAME = "update-properties";
    public static final String CHANGE_LEVEL_OPERATION_NAME = "change-log-level";

    public static final PropertyAttributeDefinition FILTER_SPEC = PropertyAttributeDefinition.Builder.of("filter-spec", ModelType.STRING, true)
            .addAlternatives("filter")
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.HANDLER_FILTER_REFERENCE_RECORDER)
            .build();

    public static final PropertyAttributeDefinition FORMATTER = PropertyAttributeDefinition.Builder.of("formatter", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives("named-formatter")
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        writer.writeStartElement(PatternFormatterResourceDefinition.PATTERN_FORMATTER.getXmlName());
                        final String pattern = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(PatternFormatterResourceDefinition.PATTERN.getXmlName(), pattern);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }
            })
            .setDefaultValue(new ModelNode("%d{HH:mm:ss,SSS} %-5p [%c] (%t) %s%e%n"))
            .build();

    public static final SimpleAttributeDefinition NAMED_FORMATTER = SimpleAttributeDefinitionBuilder.create("named-formatter", ModelType.STRING, true)
            .setAllowExpression(false)
            .setAlternatives("formatter")
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(FORMATTER.getXmlName());
                        writer.writeStartElement(attribute.getXmlName());
                        String content = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(CommonAttributes.NAME.getName(), content);
                        writer.writeEndElement();
                        writer.writeEndElement();
                    }
                }
            })
            .setCapabilityReference(Capabilities.HANDLER_FORMATTER_REFERENCE_RECORDER)
            .build();

    static final AttributeDefinition[] DEFAULT_ATTRIBUTES = {
            LEVEL,
            ENABLED,
            ENCODING,
            FORMATTER,
            FILTER_SPEC,
    };

    static final AttributeDefinition[] LEGACY_ATTRIBUTES = {
            FILTER,
    };

    private final OperationStepHandler writeHandler;
    private final AttributeDefinition[] writableAttributes;
    private final AttributeDefinition[] readOnlyAttributes;
    private final PropertySorter propertySorter;
    private final boolean registerLegacyOps;

    protected AbstractHandlerDefinition(final PathElement path,
                                        final Class<? extends Handler> type,
                                        final AttributeDefinition[] attributes) {
        this(createParameters(path, type, PropertySorter.NO_OP, attributes), true, PropertySorter.NO_OP, null, attributes);
    }

    protected AbstractHandlerDefinition(final PathElement path,
                                        final boolean registerLegacyOps,
                                        final Class<? extends Handler> type,
                                        final PropertySorter propertySorter,
                                        final AttributeDefinition[] attributes) {
        this(createParameters(path, type, propertySorter, attributes), registerLegacyOps, propertySorter, null, attributes);
    }

    protected AbstractHandlerDefinition(final PathElement path,
                                        final Class<? extends Handler> type,
                                        final AttributeDefinition[] attributes,
                                        final ConfigurationProperty<?>... constructionProperties) {
        this(createParameters(path, type, PropertySorter.NO_OP, attributes, constructionProperties),
                true, PropertySorter.NO_OP, null, attributes);
    }

    protected AbstractHandlerDefinition(final Parameters parameters,
                                        final boolean registerLegacyOps,
                                        final PropertySorter propertySorter,
                                        final AttributeDefinition[] readOnlyAttributes,
                                        final AttributeDefinition[] writableAttributes) {
        super(parameters);
        this.registerLegacyOps = registerLegacyOps;
        this.writableAttributes = writableAttributes;
        writeHandler = new HandlerOperations.LogHandlerWriteAttributeHandler(propertySorter, this.writableAttributes);
        this.readOnlyAttributes = readOnlyAttributes;
        this.propertySorter = propertySorter;
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : writableAttributes) {
            // Filter requires a special reader
            if (def.getName().equals(FILTER.getName())) {
                resourceRegistration.registerReadWriteAttribute(def, LoggingOperations.ReadFilterOperationStepHandler.INSTANCE, writeHandler);
            } else {
                resourceRegistration.registerReadWriteAttribute(def, null, writeHandler);
            }
        }
        if (readOnlyAttributes != null) {
            for (AttributeDefinition def : readOnlyAttributes) {
                resourceRegistration.registerReadOnlyAttribute(def, null);
            }
        }
        // Be careful with this attribute. It needs to show up in the "add" operation param list so ops from legacy
        // scripts will validate. It does because it's registered as an attribute but is not setResourceOnly(true)
        // so DefaultResourceAddDescriptionProvider adds it to the param list
        resourceRegistration.registerReadOnlyAttribute(NAME, ReadResourceNameOperationStepHandler.INSTANCE);
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);

        if (registerLegacyOps) {
            final ResourceDescriptionResolver resourceDescriptionResolver = getResourceDescriptionResolver();
            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ENABLE, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .build(), HandlerOperations.ENABLE_HANDLER);

            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(DISABLE, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .build(), HandlerOperations.DISABLE_HANDLER);

            registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(CHANGE_LEVEL_OPERATION_NAME, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(CommonAttributes.LEVEL)
                    .build(), HandlerOperations.CHANGE_LEVEL);

            final SimpleOperationDefinition updateProperties = new SimpleOperationDefinitionBuilder(UPDATE_OPERATION_NAME, resourceDescriptionResolver)
                    .setDeprecated(ModelVersion.create(1, 2, 0))
                    .setParameters(writableAttributes)
                    .build();

            registration.registerOperationHandler(updateProperties, new HandlerOperations.HandlerUpdateOperationStepHandler(propertySorter, writableAttributes));
        }
    }

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion,
                                     final ResourceTransformationDescriptionBuilder rootResourceBuilder,
                                     final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        if (modelVersion.hasTransformers()) {
            final PathElement pathElement = getPathElement();
            final ResourceTransformationDescriptionBuilder resourceBuilder = rootResourceBuilder.addChildResource(pathElement);
            final ResourceTransformationDescriptionBuilder loggingProfileResourceBuilder = loggingProfileBuilder.addChildResource(pathElement);
            registerResourceTransformers(modelVersion, resourceBuilder, loggingProfileResourceBuilder);
        }
    }

    /**
     * Register the transformers for the resource.
     *
     * @param modelVersion          the model version we're registering
     * @param resourceBuilder       the builder for the resource
     * @param loggingProfileBuilder the builder for the logging profile
     */
    protected void registerResourceTransformers(final KnownModelVersion modelVersion,
                                                final ResourceTransformationDescriptionBuilder resourceBuilder,
                                                final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        // do nothing by default
    }

    /**
     * Creates the default {@linkplain org.jboss.as.controller.SimpleResourceDefinition.Parameters parameters} for
     * creating the source.
     *
     * @param path                   the resource path
     * @param type                   the known type of the resource or {@code null} if the type is unknown
     * @param propertySorter         the property sorter
     * @param addAttributes          the attributes for the add operation step handler
     * @param constructionProperties the construction properties required for the handler
     *
     * @return the default parameters
     */
    private static Parameters createParameters(final PathElement path, final Class<? extends Handler> type,
                                               final PropertySorter propertySorter, final AttributeDefinition[] addAttributes,
                                               final ConfigurationProperty<?>... constructionProperties) {
        return new Parameters(path, LoggingExtension.getResourceDescriptionResolver(path.getKey()))
                .setAddHandler(new HandlerOperations.HandlerAddOperationStepHandler(propertySorter, type, addAttributes, constructionProperties))
                .setRemoveHandler(HandlerOperations.REMOVE_HANDLER)
                .setCapabilities(Capabilities.HANDLER_CAPABILITY);
    }
}
