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

import static org.jboss.as.logging.CommonAttributes.ADD_HANDLER_OPERATION_NAME;
import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.LEVEL;
import static org.jboss.as.logging.CommonAttributes.REMOVE_HANDLER_OPERATION_NAME;

import java.util.Locale;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.resolvers.OverflowActionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.AsyncHandler;
import org.jboss.logmanager.handlers.AsyncHandler.OverflowAction;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class AsyncHandlerResourceDefinition extends AbstractHandlerDefinition {

    public static final String NAME = "async-handler";
    private static final String ADD_SUBHANDLER_OPERATION_NAME = "assign-subhandler";
    private static final String REMOVE_SUBHANDLER_OPERATION_NAME = "unassign-subhandler";
    private static final PathElement ASYNC_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition QUEUE_LENGTH = PropertyAttributeDefinition.Builder.of("queue-length", ModelType.INT)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setPropertyName("queueLength")
            .setValidator(new IntRangeValidator(1, false))
            .build();

    public static final PropertyAttributeDefinition OVERFLOW_ACTION = PropertyAttributeDefinition.Builder.of("overflow-action", ModelType.STRING)
            .setAllowExpression(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        String content = resourceModel.get(attribute.getName()).asString().toLowerCase(Locale.ENGLISH);
                        writer.writeAttribute("value", content);
                        writer.writeEndElement();
                    }
                }
            })
            .setRequired(false)
            .setDefaultValue(new ModelNode(OverflowAction.BLOCK.name()))
            .setPropertyName("overflowAction")
            .setResolver(OverflowActionResolver.INSTANCE)
            .setValidator(EnumValidator.create(OverflowAction.class, false, false))
            .build();

    static final SimpleAttributeDefinition HANDLER = SimpleAttributeDefinitionBuilder.create("handler", ModelType.STRING)
            .setAllowExpression(false)
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .setCapabilityReference(Capabilities.HANDLER_REFERENCE_RECORDER)
            .build();

    public static final LogHandlerListAttributeDefinition SUBHANDLERS = LogHandlerListAttributeDefinition.Builder.of("subhandlers", HANDLER)
            .setAllowDuplicates(false)
            .setAllowExpression(false)
            .setRequired(false)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = {ENABLED, LEVEL, FILTER_SPEC, QUEUE_LENGTH, OVERFLOW_ACTION, SUBHANDLERS};


    public AsyncHandlerResourceDefinition(final boolean includeLegacyAttributes) {
        super(ASYNC_HANDLER_PATH, AsyncHandler.class, (includeLegacyAttributes ? Logging.join(ATTRIBUTES, LEGACY_ATTRIBUTES) : ATTRIBUTES), QUEUE_LENGTH);
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration registration) {
        super.registerOperations(registration);
        final ResourceDescriptionResolver resourceDescriptionResolver = getResourceDescriptionResolver();
        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.ADD_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_SUBHANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setDeprecated(ModelVersion.create(1, 2, 0))
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.REMOVE_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(ADD_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.ADD_SUBHANDLER);

        registration.registerOperationHandler(new SimpleOperationDefinitionBuilder(REMOVE_HANDLER_OPERATION_NAME, resourceDescriptionResolver)
                .setParameters(CommonAttributes.HANDLER_NAME)
                .build(), HandlerOperations.REMOVE_SUBHANDLER);
    }

    @Override
    protected void registerResourceTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder resourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        // do nothing by default
    }
}
