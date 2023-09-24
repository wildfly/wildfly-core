/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.resources.ServerConfigResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * {@code OperationHandler} determining the status of a server.
 *
 * @author Emanuel Muckenhuber
 */
public class ServerStatusHandler implements OperationStepHandler {

    public static final String ATTRIBUTE_NAME = "status";

    private final ServerInventory serverInventory;

    /**
     * Create the ServerAddHandler
     */
    public ServerStatusHandler(final ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final PathElement element = address.getLastElement();
        final String serverName = element.getValue();

        final ModelNode subModel = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
        final boolean isStart = ServerConfigResourceDefinition.AUTO_START.resolveModelAttribute(context, subModel).asBoolean();

        ServerStatus status = serverInventory.determineServerStatus(serverName);

        if (status == ServerStatus.STOPPED) {
            status = isStart ? status : ServerStatus.DISABLED;
        }

        if(status != null) {
            context.getResult().set(status.toString());
        } else {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.failedToGetServerStatus());
        }
    }

}
