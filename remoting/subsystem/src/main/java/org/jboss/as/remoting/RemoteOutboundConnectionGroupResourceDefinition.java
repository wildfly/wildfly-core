/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.remoting;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
class RemoteOutboundConnectionGroupResourceDefinition extends SimpleResourceDefinition {

    static final PathElement ADDRESS = PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION_GROUP);

    static final StringListAttributeDefinition OUTBOUND_SOCKET_BINDINGS_REFS = new StringListAttributeDefinition.Builder(
            CommonAttributes.OUTBOUND_SOCKET_BINDING_REFS)
            .setAllowNull(false)
            .setAttributeParser(AttributeParser.COMMA_DELIMITED_STRING_LIST)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel,
                        boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {

                    StringBuilder builder = new StringBuilder();
                    if (resourceModel.hasDefined(attribute.getName())) {
                        for (ModelNode p : resourceModel.get(attribute.getName()).asList()) {
                            builder.append(p.asString()).append(", ");
                        }
                    }
                    if (builder.length() > 0) {
                        writer.writeAttribute(attribute.getXmlName(), builder.substring(0, builder.length() - 2));
                    }
                }
            })
            .setRestartAllServices()
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .build();

    public static final SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.USERNAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .build();

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(CommonAttributes.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(
            CommonAttributes.PROTOCOL, ModelType.STRING, true).setValidator(
                    new EnumValidator<Protocol>(Protocol.class, true, false))
            .setDefaultValue(new ModelNode(Protocol.HTTP_REMOTING.toString()))
            .setAllowExpression(true)
            .build();

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = {
            OUTBOUND_SOCKET_BINDINGS_REFS, USERNAME, SECURITY_REALM, PROTOCOL
    };

    static final RemoteOutboundConnectionGroupResourceDefinition INSTANCE = new RemoteOutboundConnectionGroupResourceDefinition();

    private RemoteOutboundConnectionGroupResourceDefinition() {
        super(ADDRESS, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.REMOTE_OUTBOUND_CONNECTION_GROUP),
                RemoteOutboundConnectionGroupAdd.INSTANCE, RemoteOutboundConnectionGroupRemove.INSTANCE);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.REMOTE_OUTBOUND_CONNECTION_GROUP));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(OUTBOUND_SOCKET_BINDINGS_REFS, null, RemoteOutboundConnectionGroupWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(USERNAME, null, RemoteOutboundConnectionGroupWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SECURITY_REALM, null, RemoteOutboundConnectionGroupWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(PROTOCOL, null, RemoteOutboundConnectionGroupWriteHandler.INSTANCE);
    }
}
