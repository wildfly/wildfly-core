/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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

    static final RuntimeCapability<Void> SOCKET_BINDING_MANAGER_CAPABILITY = RuntimeCapability.Builder.of(SocketBindingManager.SERVICE_DESCRIPTOR).build();

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
        resourceRegistration.registerReadWriteAttribute(PORT_OFFSET, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
    }
}
