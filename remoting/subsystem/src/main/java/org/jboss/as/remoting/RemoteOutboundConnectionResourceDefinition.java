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

import static org.jboss.as.remoting.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class RemoteOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement ADDRESS = PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION);

    public static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OUTBOUND_CONNECTION_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, OUTBOUND_CONNECTION_CAPABILITY_NAME)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_CLIENT_REF)
            .build();

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = {
        OUTBOUND_SOCKET_BINDING_REF, AUTHENTICATION_CONTEXT
    };

    static final RemoteOutboundConnectionResourceDefinition INSTANCE = new RemoteOutboundConnectionResourceDefinition();

    private RemoteOutboundConnectionResourceDefinition() {
        super(new Parameters(ADDRESS, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.REMOTE_OUTBOUND_CONNECTION))
                .setAddHandler(RemoteOutboundConnectionAdd.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), RemoteOutboundConnectionAdd.INSTANCE))
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.REMOTE_OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(OUTBOUND_SOCKET_BINDING_REF, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(AUTHENTICATION_CONTEXT, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
    }

    @Override
    protected OperationStepHandler getWriteAttributeHandler(AttributeDefinition attribute) {
        // we ignore the passed attribute, since all attribute writes lead to the
        // same action - i.e. restart the service
        return RemoteOutboundConnectionWriteHandler.INSTANCE;
    }
}
