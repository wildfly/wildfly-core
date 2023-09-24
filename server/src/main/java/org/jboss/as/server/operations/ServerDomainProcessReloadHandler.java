/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.server.DomainServerCommunicationServices;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Custom reload handler updating the operation-id before reconnecting to the HC.
 *
 * @author Emanuel Muckenhuber
 */
public class ServerDomainProcessReloadHandler extends ServerProcessReloadHandler {

    private final DomainServerCommunicationServices.OperationIDUpdater operationIDUpdater;

    public ServerDomainProcessReloadHandler(ServiceName rootService, RunningModeControl runningModeControl, ControlledProcessState processState,
                                            final DomainServerCommunicationServices.OperationIDUpdater operationIDUpdater,
                                            final ServerEnvironment serverEnvironment) {
        super(rootService, runningModeControl, processState, serverEnvironment);
        this.operationIDUpdater = operationIDUpdater;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        // Update the operation permit
        final int permit = operation.require("operation-id").asInt();
        operationIDUpdater.updateOperationID(permit);

        super.execute(context, operation);
    }
}
