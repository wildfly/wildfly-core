/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.resource.AbstractSocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.SocketBindingResourceDefinition;
import org.jboss.as.server.services.net.LocalDestinationOutboundSocketBindingResourceDefinition;
import org.jboss.as.server.services.net.RemoteDestinationOutboundSocketBindingResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Kabir Khan
 */
public class SocketBindingGroupResourceDefinition extends AbstractSocketBindingGroupResourceDefinition {

    public static final String SOCKET_BINDING_GROUP_CAPABILITY_NAME = "org.wildfly.domain.socket-binding-group";
    public static final RuntimeCapability SOCKET_BINDING_GROUP_CAPABILITY = RuntimeCapability.Builder.of(SOCKET_BINDING_GROUP_CAPABILITY_NAME, true)
            .build();

    public static final SimpleAttributeDefinition DEFAULT_INTERFACE = createDefaultInterface(SOCKET_BINDING_GROUP_CAPABILITY);

    public static final ListAttributeDefinition INCLUDES = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.INCLUDES, ModelType.STRING)
            .setRequired(false)
            .setMinSize(0)
            .setMaxSize(Integer.MAX_VALUE)
            .setElementValidator(new StringLengthValidator(1, true))
            .setAttributeMarshaller(new AttributeMarshaller() {
                @Override
                public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel)) {
                        if (!isMarshallable(attribute, resourceModel, marshallDefault)) {
                            return;
                        }
                        boolean first = true;
                        StringBuilder sb = new StringBuilder();
                        for (ModelNode include : resourceModel.get(ModelDescriptionConstants.INCLUDES).asList()) {
                            if (first) {
                                first = false;
                            } else {
                                sb.append(" ");
                            }
                            sb.append(include.asString());
                        }
                        writer.writeAttribute(attribute.getXmlName(), sb.toString());
                    }
                }
            })
            .setCapabilityReference(SOCKET_BINDING_GROUP_CAPABILITY_NAME, SOCKET_BINDING_GROUP_CAPABILITY_NAME)
            .build();

    public static SocketBindingGroupResourceDefinition INSTANCE = new SocketBindingGroupResourceDefinition();

    private SocketBindingGroupResourceDefinition() {
        super(SocketBindingGroupAddHandler.INSTANCE, ModelOnlyRemoveStepHandler.INSTANCE,
                DEFAULT_INTERFACE, SOCKET_BINDING_GROUP_CAPABILITY);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(INCLUDES, null, createIncludesValidationHandler());
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(SocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(RemoteDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(LocalDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
    }

    public static OperationStepHandler createIncludesValidationHandler() {
        return new DomainIncludesValidationWriteAttributeHandler();
    }

}
