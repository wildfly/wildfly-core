/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
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
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.resource.AbstractSocketBindingGroupResourceDefinition;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Kabir Khan
 */
public class SocketBindingGroupResourceDefinition extends AbstractSocketBindingGroupResourceDefinition {

    static final RuntimeCapability<Void> SOCKET_BINDING_MANAGER_CAPABILITY =
            RuntimeCapability.Builder.of("org.wildfly.management.socket-binding-manager", SocketBindingManager.class).build();


    public static final SimpleAttributeDefinition DEFAULT_INTERFACE = createDefaultInterface(SOCKET_BINDING_MANAGER_CAPABILITY);

    public static final SimpleAttributeDefinition PORT_OFFSET = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT_OFFSET, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(-65535, 65535, true, true))
            .setDefaultValue(ModelNode.ZERO).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static SocketBindingGroupResourceDefinition INSTANCE = new SocketBindingGroupResourceDefinition();

    private SocketBindingGroupResourceDefinition() {
        super(BindingGroupAddHandler.INSTANCE,
                new ReloadRequiredRemoveStepHandler() {
                    @Override
                    protected boolean requiresRuntime(OperationContext context) {
                        return true;
                    }
                },
                DEFAULT_INTERFACE, SOCKET_BINDING_MANAGER_CAPABILITY);
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(SocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(RemoteDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
        resourceRegistration.registerSubModel(LocalDestinationOutboundSocketBindingResourceDefinition.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(PORT_OFFSET, null, new ReloadRequiredWriteAttributeHandler(PORT_OFFSET));
    }
}
