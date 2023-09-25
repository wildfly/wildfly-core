/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class GenericOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement ADDRESS = PathElement.pathElement(CommonAttributes.OUTBOUND_CONNECTION);

    public static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder(CommonAttributes.URI, ModelType.STRING, false)
            .setAllowExpression(true).setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();


    GenericOutboundConnectionResourceDefinition() {
        super(new Parameters(ADDRESS, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.OUTBOUND_CONNECTION))
                .setAddHandler(GenericOutboundConnectionAdd.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), GenericOutboundConnectionAdd.INSTANCE))
                .setDeprecatedSince(ModelVersion.create(4))
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(URI, null, GenericOutboundConnectionWriteHandler.INSTANCE);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
    }

    @Override
    protected OperationStepHandler getWriteAttributeHandler(AttributeDefinition attribute) {
        // we ignore the passed attribute, since all attribute writes lead to the
        // same action - i.e. restart the service
        return GenericOutboundConnectionWriteHandler.INSTANCE;
    }
}
