/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import static org.jboss.as.host.controller.resources.ServerConfigResourceDefinition.GROUP;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.MODULE_OPTIONS;
import static org.jboss.as.host.controller.resources.ServerConfigResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE;
import static org.jboss.as.host.controller.resources.ServerConfigResourceDefinition.SOCKET_BINDING_GROUP;
import static org.jboss.as.host.controller.resources.ServerConfigResourceDefinition.SOCKET_BINDING_PORT_OFFSET;

import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.operations.coordination.ServerOperationResolver;
import org.jboss.dmr.ModelNode;

/**
 * Writes the group and socket-binding-group attributes of a server group and validates the new value. ServerOperationResolver is responsible for
 * putting the affected server in the restart-required state.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerRestartRequiredServerConfigWriteAttributeHandler extends ModelOnlyWriteAttributeHandler {

    public static OperationStepHandler INSTANCE = new ServerRestartRequiredServerConfigWriteAttributeHandler();

    protected ServerRestartRequiredServerConfigWriteAttributeHandler() {
        super(GROUP, MODULE_OPTIONS, SOCKET_BINDING_GROUP, SOCKET_BINDING_PORT_OFFSET, SOCKET_BINDING_DEFAULT_INTERFACE);
    }


    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
            ModelNode currentValue, Resource resource) throws OperationFailedException {
        if (newValue.equals(currentValue)) {
            //Set an attachment to avoid propagation to the servers, we don't want them to go into restart-required if nothing changed
            ServerOperationResolver.addToDontPropagateToServersAttachment(context, operation);
        }
    }

}
